# EVM 전송 응답 유실과 lookup-first 복구

## 문제

노드가 signed transaction을 처리한 뒤 HTTP 응답만 유실되면 backend는 성공과 실패를 구분할 수 없습니다. 이 상황을 일반 실패로 보고 즉시 재전송하면 같은 side effect를 중복 실행하거나 nonce 경쟁과 불필요한 gas를 만들 수 있습니다.

## 실험

테스트 전용 JSON-RPC proxy가 다음 순서로 장애를 만들었습니다.

1. `eth_sendRawTransaction`을 로컬 Anvil에 전달합니다.
2. Anvil이 transaction을 처리하고 정상 응답을 반환할 때까지 기다립니다.
3. 응답 body 일부만 backend에 전달한 뒤 연결을 닫습니다.
4. 이후 lookup, log와 receipt 조회는 정상 전달합니다.

검증 기준은 proxy 호출 횟수만이 아니라 실제 체인과 DB 상태로 정했습니다.

- 첫 처리 오류가 `AMBIGUOUS / SUBMIT_RESULT_UNKNOWN`인지
- Outbox와 proof가 `RECONCILE_PENDING`인지
- 다음 처리가 새 raw transaction을 제출하지 않는지
- 최종 Anchor event가 정확히 1건인지
- Outbox가 `SUCCEEDED`, proof가 `CONFIRMED`인지

## 결과

| 항목 | 관측 결과 |
| --- | --- |
| 첫 Worker 처리 | `AMBIGUOUS → RECONCILE_PENDING` |
| 다음 Worker 처리 | commitment lookup으로 기존 receipt 발견 |
| reconciliation 중 새 transaction | 0건 |
| canonical Anchor event | 1건 |
| 최종 Outbox | `SUCCEEDED`, attempt count 2 |
| 최종 proof | `CONFIRMED` |

Fake adapter가 아닌 signed transaction, Solidity contract, Web3j HTTP transport, PostgreSQL Outbox를 함께 사용했습니다. 의도적인 응답 절단은 로컬에서 수행했고 Sepolia에서 provider 통신을 방해하지는 않았습니다.

## 결정

Transport 결과를 다음 세 범주로 분리합니다.

- `RETRYABLE`: provider가 요청을 명시적으로 거부해 안전하게 다시 시도 가능
- `AMBIGUOUS`: 요청 처리 여부를 알 수 없어 먼저 체인 조회 필요
- `PERMANENT`: 설정이나 계약 오류로 자동 재시도 불가

`AMBIGUOUS`는 실패로 확정하지 않고 lookup-first reconciliation으로 이동합니다. Lookup이 불가능할 때만 수동 검토로 전환합니다.

## 트레이드오프

- 즉시 성공보다 reconciliation 한 번만큼 확정이 늦어집니다.
- lookup RPC까지 실패하면 자동 복구가 지연됩니다.
- deployment block과 현재 head의 거리가 커지면 log 조회 비용이 증가합니다.
- 서로 다른 commitment의 writer nonce 경쟁은 별도 문제입니다.

## 재현 근거

- fault proxy: `src/test/java/com/naesan/passport/support/FaultInjectingJsonRpcProxy.java`
- adapter 실험: `EvmProofAnchorAdapterIntegrationTest.recoversBroadcastTransactionAfterResponseLoss`
- Outbox 실험: `EvmProofOutboxIntegrationTest.reconcilesBroadcastProofAfterResponseLoss`
- 복구 흐름: `src/main/java/com/naesan/passport/application/ProcessProofOutboxService.java`
- commit: [`811e4b4`](https://github.com/naesan-project/naesan-backend/commit/811e4b48b9c192e0567940e09217bc16e2bd7312)
