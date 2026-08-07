# Web3 한계와 측정

## 확인한 한계

| 항목 | 근거 | 현재 상태 |
| --- | --- | --- |
| 무료 RPC log 범위 | Alchemy Free `eth_getLogs` 범위 제한 실제 발생 | block range 분할 조회로 개선 |
| confirmation 지연 | Render→Sepolia 발급부터 2 confirmations까지 57.716초 | 1회 측정, 통계 표본 부족 |
| anchor gas | 서로 다른 Sepolia anchor 2건 모두 45,663 gas | 재현됨 |
| 전송 결과 모호성 | upstream 처리 후 응답 절단 | lookup-first 자동 복구 |
| 단일 writer nonce | 독립 client 경쟁 100회 | 충돌 분류와 재시도 확인 |
| RPC 가용성 | 429·503·timeout 장애 주입 | startup 분리와 retry 확인 |
| reorganization | snapshot/revert | 재제출 가능, 사후 감지 미구현 |
| key rotation | immutable writer contract | 새 contract와 설정 이전 필요 |

## 속도

실제 Render→Sepolia 1회 측정에서 패스 발급 API는 1.178초 만에 접수를 반환했지만 2 confirmations까지 57.716초가 걸렸습니다. Transactional Outbox가 HTTP 응답과 chain 확정을 분리하므로 사용자는 블록 확정 동안 연결을 유지하지 않습니다.

무료 hosting cold start는 같은 측정 구간에서 frontend 22.647초, backend 59.267초였습니다. 애플리케이션 cold start와 Web3 confirmation은 서로 다른 병목이며 합쳐서 “블록체인이 느리다”고 표현하지 않습니다. 표본이 한 번이므로 p50·p95는 제시하지 않습니다.

별도 readiness gate 실험의 정상 재배포에서는 deploy 시작부터 Live까지 161초, Spring startup만 130.595초가 걸렸습니다. 첫 원복 시도는 health check 대기 중 수동 취소 후 동일 설정으로 재시도했습니다. 무료 hosting 배포 편차와 provider 회복 시간을 분리해야 하며, 두 표본만으로 지연 분포를 일반화하지 않습니다.

## 가격

- 실제 anchor gas used: `45,663`
- 측정된 Sepolia transaction fee 예시: `0.000048196013278374 Sepolia ETH`
- 실제 현금 지출: `0원`

Sepolia ETH는 Faucet 자산이라 금전적 가치가 없습니다. Mainnet 비용은 `gasUsed × 당시 gas price × ETH 환율` 시나리오로만 추정하며 실제 청구액으로 표현하지 않습니다.

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
- Public testnet: Sepolia contract와 실제 anchor
- Hosting: Render Free와 Supabase Free
- Mainnet: 배포하지 않음
