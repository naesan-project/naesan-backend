# Web3 한계와 측정

## 확인한 한계

| 항목 | 근거 | 현재 상태 |
| --- | --- | --- |
| 무료 RPC log 범위 | Alchemy Free `eth_getLogs` 범위 제한 실제 발생 | block range 분할 조회로 개선 |
| confirmation 지연 | Render→Sepolia 2 confirmations 57.716초, 30.955초 | 2회 측정, 통계 표본 부족 |
| anchor gas | 서로 다른 Sepolia anchor 3건 모두 45,663 gas | 재현됨 |
| 전송 결과 모호성 | upstream 처리 후 응답 절단 | lookup-first 자동 복구 |
| 단일 writer nonce | 독립 client 경쟁 100회 | 충돌 분류와 재시도 확인 |
| RPC 가용성 | 429·503·timeout 장애 주입 | startup 분리와 retry 확인 |
| reorganization | snapshot/revert | 재제출 가능, 사후 감지 미구현 |
| key rotation | immutable writer contract | 새 contract와 설정 이전 필요 |

## 속도

실제 Render→Sepolia 측정에서 패스 발급 API는 1.178초와 1.433초 만에 `PREPARED` 상태를 반환했습니다. 각 건의 2 confirmations 확인에는 57.716초와 30.955초가 걸렸습니다. Transactional Outbox가 HTTP 응답과 chain 확정을 분리하므로 사용자는 블록 확정 동안 연결을 유지하지 않습니다.

무료 hosting cold start는 별도 측정 구간에서 frontend 22.647초, backend 59.267초였습니다. 애플리케이션 cold start와 Web3 confirmation은 서로 다른 병목이며 합쳐서 “블록체인이 느리다”고 표현하지 않습니다. confirmation 표본이 두 번에 불과하므로 p50·p95는 제시하지 않습니다.

별도 readiness gate 실험의 정상 재배포에서는 deploy 시작부터 Live까지 161초, Spring startup만 130.595초가 걸렸습니다. 첫 원복 시도는 health check 대기 중 수동 취소 후 동일 설정으로 재시도했습니다. 무료 hosting 배포 편차와 provider 회복 시간을 분리해야 하며, 두 표본만으로 지연 분포를 일반화하지 않습니다.

## 가격

- 실제 anchor gas used: `45,663`
- 2026-08-07 시연 트랜잭션: [`0x426e…b0a66`](https://sepolia.etherscan.io/tx/0x426e3afffcdc0a95243fed5a2313bcd57582afa9f3d985bb86559b2e592b0a66)
- 시연 시 effective gas price: `1.017462706 gwei`
- 시연 시 Sepolia fee: `0.000046460399544078 Sepolia ETH`
- 실제 현금 지출: `0원`

Sepolia ETH는 Faucet 자산이라 금전적 가치가 없습니다. Mainnet 비용은 `gasUsed × 당시 gas price × ETH 환율` 시나리오로만 추정하며 실제 청구액으로 표현하지 않습니다.

2026-08-07T04:43Z 순간값인 Mainnet `0.113882985 gwei`, ETH `$1,896.93` / `₩2,692,644`를 적용하면 다음과 같습니다.

| 동작 | ETH | USD | KRW |
| --- | ---: | ---: | ---: |
| contract deploy, 181,739 gas | 0.000020696979810915 | $0.0393 | ₩56 |
| anchor 1건, 45,663 gas | 0.000005200238744055 | $0.0099 | ₩14 |
| anchor 월 100건 | 0.0005200238744055 | $0.99 | ₩1,400 |
| anchor 월 1,000건 | 0.005200238744055 | $9.86 | ₩14,002 |

이 순간값은 네트워크 혼잡과 ETH 가격에 따라 크게 변합니다. 운영 예산은 현재값 하나가 아니라 기존 5·15·30·60 gwei 시나리오와 함께 판단합니다.

## 운영 RPC 관찰

2026-08-07 시연 전후 Alchemy 24시간 대시보드는 총 요청을 `800`에서 `1.2K`로 반올림해 표시했고, 5분 평균은 `1.7 CU/s`에서 `17.3 CU/s`로 변했습니다. 1시간 성공률은 `100%`, invalid request와 throughput limited는 모두 `0`이었습니다.

24시간 집계는 반올림되고 health check를 포함하므로 `+400건`을 패스 1건의 정확한 비용으로 귀속하지 않습니다. 최근 5분 request log에서 `eth_call`, `eth_getCode`, `eth_chainId`가 HTTP 200으로 0–3ms에 응답한 사실은 별도로 확인했습니다.

## 확장성

DB fencing은 동일 Outbox event의 중복 처리를 막지만 서로 다른 event가 단일 writer의 같은 pending nonce를 읽는 경쟁까지 직렬화하지 않습니다. 독립 Web3j client 두 개를 동시에 실행한 100회 실험에서는 매회 한 제출이 nonce 충돌로 거부됐고, 다음 nonce를 다시 조회한 재시도는 모두 확정됐습니다.

낮은 쓰기 트래픽에서는 단일 writer가 단순하지만 처리량이 증가하면 nonce coordinator, 제출 queue 또는 계정 분할 전략이 필요합니다.

## 신뢰 범위

컨트랙트는 salted commitment가 특정 anchor 시각에 기록됐다는 사실을 제공합니다. 다음을 증명하지는 않습니다.

- 제품의 정품 여부
- 사용자가 제출한 구매 증빙의 진실성
- 중앙 backend writer로부터의 탈중앙화

원본 증빙과 개인정보는 온체인에 저장하지 않지만, immutable writer key의 보관과 교체는 운영 책임으로 남습니다.

## 검증 환경

- Local: Anvil, production compiler 설정, signed transaction과 PostgreSQL 통합
- Browser integration: production Docker Compose, 실제 React UI의 구매 증빙 파일 업로드·발급·공유 폐기 ([CI evidence](https://github.com/naesan-project/naesan-backend/actions/runs/31149499940))
- Public testnet: Sepolia contract와 실제 anchor
- Hosting: Render Free와 Supabase Free
- Mainnet: 배포하지 않음
