# RPC 장애와 애플리케이션 가용성 분리

## 문제

Web3j는 RPC의 429·503을 checked `IOException`이 아닌 runtime `ClientConnectionException`으로 반환할 수 있습니다. 일부 호출만 이 예외를 처리하면 Worker의 retry 계약을 우회합니다.

추가로 기존 구성은 EVM adapter bean 생성 중 `verifyConfiguration()`을 호출했습니다. 따라서 provider의 순간 장애가 Web3 처리 실패를 넘어 계정·증빙·패스 조회까지 포함한 전체 Spring context 시작 실패로 확대됐습니다.

## 실험

### RPC 호출 경계

JSON-RPC proxy로 다음 요청에 429, 503과 read timeout을 주입했습니다.

- chain ID, contract code와 writer 검증
- nonce, gas price와 gas estimate
- raw transaction 전송
- receipt, block, read-back과 log lookup

Worker 통합 테스트에서는 첫 실행 후 Outbox가 `RETRY_WAIT`, proof가 `PREPARED`, 오류가 `RETRYABLE / RPC_UNAVAILABLE`인지 확인했습니다. 다음 시도에서는 RPC를 정상화해 같은 event가 `SUCCEEDED`, proof가 `CONFIRMED`되는지 검증했습니다.

### startup과 health

`ApplicationContextRunner`에 connection-refused RPC URL을 설정했습니다. 개선 전 코드를 복원한 red test에서는 `evmProofAnchorPort` bean factory가 실패했습니다.

개선 후에는 503을 주입한 상태에서 다음 계약을 확인했습니다.

- 애플리케이션 context 생성 성공
- `/health`: HTTP 200, `UP`
- `/ready`: HTTP 503, `DOWN`
- 내부 error code는 actuator 응답에서 숨김
- RPC 정상화 후 `/ready`: HTTP 200, `UP`

### Render 배포 게이트

실제 Render 배포에서는 비밀값인 RPC URL을 변경하지 않고 Sepolia chain ID를 `11155111`에서 `1`로 바꾸어 영구 설정 오류를 주입했습니다. 새 인스턴스의 Spring 프로세스는 시작됐지만 `/ready`가 성공하지 않아 트래픽 승격 단계에서 대기했습니다.

그동안 외부 URL은 이전 정상 인스턴스를 계속 제공했고 `/health`와 `/ready`가 모두 HTTP 200 `UP`을 유지했습니다. chain ID를 `11155111`로 되돌려 다음 배포를 시작하자 실패 설정 배포는 자동 취소됐습니다. 즉 애플리케이션 내부의 liveness/readiness 분리뿐 아니라 배포 플랫폼의 readiness gate가 잘못된 Web3 설정의 무중단 차단선으로 동작함을 확인했습니다.

| 배포 관측 | 결과 |
| --- | --- |
| 정상 기준선 | `/health` 200 `UP`, `/ready` 200 `UP` |
| 잘못된 chain ID 인스턴스 | Spring 시작, `/ready` 승격 대기 |
| 장애 주입 중 외부 URL | 기존 정상 인스턴스가 두 endpoint 모두 200 유지 |
| 정상 설정 재배포 | deploy 시작부터 Live까지 161초 |
| 재배포 애플리케이션 startup | 130.595초 |
| provider probe | startup 8초 뒤 `AVAILABLE` |
| Live 이후 측정 | `/health` 200 `UP`, `/ready` 200 `UP` |

## 결과

원격 검증을 startup에서 전용 scheduler의 캐시형 health probe로 이동했습니다. `/ready` 요청은 RPC를 직접 호출하지 않고 마지막 probe 결과를 읽습니다.

| 상황 | liveness | readiness | Worker |
| --- | --- | --- | --- |
| RPC 정상 | `UP` | `UP` | 처리 |
| 429·503·timeout | `UP` | `DOWN` | `RETRY_WAIT` |
| chain·contract 설정 오류 | `UP` | `OUT_OF_SERVICE` | 운영자 수정 필요 |
| RPC 회복 | `UP` | `UP` | 다음 due 실행에서 재개 |

Provider probe 결과·지연·현재 가용성·회복 시간은 bounded-cardinality Micrometer metric으로 기록합니다. 가용성이 2분간 0이면 Prometheus warning을 발생시킵니다.

## 결정

- 형식과 필수값 같은 로컬 설정 오류는 startup에서 차단합니다.
- 시간이 지나면 회복할 수 있는 원격 가용성은 health 상태로 관리합니다.
- liveness는 프로세스 생존만, readiness는 DB와 proof provider 준비 상태를 나타냅니다.
- health probe는 다른 scheduled worker와 별도 thread로 격리합니다.
- 기본 30초 interval로 불필요한 무료 RPC 사용량을 제한합니다.

## 트레이드오프

- readiness는 cache interval만큼 실제 RPC 상태보다 늦을 수 있습니다.
- 첫 probe 전에는 안전하게 `OUT_OF_SERVICE`로 표시됩니다.
- health probe도 RPC quota를 사용합니다.
- Render가 `/ready`를 배포 health check로 사용하므로 Web3 장애 시 새 릴리스가 트래픽을 받지 않습니다. 반대로 이미 승격된 인스턴스에서 RPC가 실패하면 readiness가 내려가므로 플랫폼의 재시작·라우팅 정책도 함께 설계해야 합니다.
- 실패 설정 배포가 차단되는 동안 외부 사용자는 장애를 보지 않으므로, 배포 게이트 실험만으로 실제 RPC 장애 중 사용자 요청 성공률을 측정할 수는 없습니다.
- 첫 정상 설정 원복 배포는 새 애플리케이션 로그 없이 health check 대기에 머물러 수동 취소했고, 같은 설정의 재배포는 성공했습니다. Render 로그만으로 최초 지연 원인을 확정할 수 없어 Web3 회복 시간에 포함하지 않았습니다.

## 재현 근거

- 예외 matrix: `EvmProofAnchorAdapterIntegrationTest`
- startup: `EvmProofStartupContextTest`
- readiness와 Worker 회복: `EvmProofOutboxIntegrationTest`
- health 상태: `EvmProofHealthIndicatorTest`
- 배포 측정: `operations/measure-web3-readiness.sh`
- 실제 배포: Render deploy `dep-d9ql24ks728c73a32fb0`(readiness 대기 후 정상 설정 배포로 자동 취소)
- 정상 재배포: Render deploy `dep-d9ql77e7bikc73e4a92g`(Live)
- commits: [`435abd6`](https://github.com/naesan-project/naesan-backend/commit/435abd6), [`44c030a`](https://github.com/naesan-project/naesan-backend/commit/44c030a), [`102aa22`](https://github.com/naesan-project/naesan-backend/commit/102aa22)
