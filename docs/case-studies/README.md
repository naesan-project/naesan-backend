# Web3 트러블슈팅 사례

내산의 Web3 경로에서 실제로 발생했거나 결정적으로 장애를 주입해 재현한 문제를 정리합니다. 각 문서는 문제, 실험, 결과, 결정과 트레이드오프를 코드·테스트·커밋에 연결합니다.

| 사례 | 검증 방법 | 핵심 결과 |
| --- | --- | --- |
| [전송 응답 유실 복구](01-ambiguous-transaction-recovery.md) | Anvil에 transaction을 전달한 뒤 JSON-RPC 응답만 절단 | 재제출 없이 기존 receipt를 찾아 Anchor event 1건으로 확정 |
| [RPC 장애와 readiness 분리](02-rpc-resilience-and-readiness.md) | 429·503·timeout, connection refused 주입 | 애플리케이션은 기동하고 readiness와 Outbox만 실패 후 회복 |
| [chain reorganization](03-chain-reorganization.md) | Anvil snapshot/revert로 확정 block 제거 | commitment 부재 감지와 재제출 가능성 확인, 사후 감지 한계 공개 |

속도·비용·확장성과 운영 한계는 [Web3 한계와 측정](WEB3-LIMITATIONS.md)에 별도로 정리했습니다.

## 증거 기준

- 공개 네트워크에서 발생한 사건과 로컬 장애 주입을 구분합니다.
- 테스트 시간과 공개 네트워크 지연을 같은 수치로 표현하지 않습니다.
- Sepolia ETH는 Faucet 자산이므로 실제 현금 비용과 분리합니다.
- API key, RPC URL, private key, 계정 정보와 원시 운영 로그는 저장하지 않습니다.
- `docs/portfolio`, `.omx`, build report와 NDJSON 원자료는 로컬 기록으로 유지합니다.
