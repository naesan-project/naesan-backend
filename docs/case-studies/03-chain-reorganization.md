# confirmation 이후 chain reorganization

## 문제

필요한 confirmation을 충족한 transaction도 canonical chain에서 사라질 수 있습니다. 저장 당시 receipt와 block hash가 정확하더라도 이후 reorganization이 발생하면 DB의 `CONFIRMED` evidence는 오래된 관측이 됩니다.

## 실험

Anvil snapshot/revert로 다음 상황을 결정적으로 재현했습니다.

1. 컨트랙트 배포 후 snapshot 생성
2. commitment 제출과 confirmation 확인
3. receipt, block hash와 contract read-back 확인
4. snapshot revert로 transaction 포함 block 제거
5. 같은 commitment lookup이 empty인지 확인
6. commitment 재제출
7. canonical `CommitmentAnchored` event 개수 확인

## 결과

| 단계 | 관측 결과 |
| --- | --- |
| revert 전 | confirmed receipt와 contract state 존재 |
| revert 후 | commitment lookup empty |
| 재제출 후 | confirmed receipt 존재 |
| canonical event | 1건 |

Adapter는 chain에서 사라진 commitment를 부재로 판정하고 같은 commitment를 다시 제출할 수 있었습니다. 하지만 현재 Worker는 이미 DB에 `CONFIRMED`로 저장된 proof를 주기적으로 다시 검사하지 않습니다.

## 결정

현재 Sepolia 포트폴리오 데모는 빠른 시연을 위해 2 confirmations를 사용하고 다음 evidence를 저장합니다.

- transaction hash와 block hash
- confirmation count
- contract read-back commitment와 anchor 시각
- 마지막 chain 확인 시각

Public 요청마다 RPC를 호출하는 방식은 API 가용성과 rate limit을 결합하므로 사용하지 않습니다. 실제 가치가 생기는 경우 background revalidation 또는 finalized block 기준을 별도 상태 모델과 함께 도입합니다.

## 트레이드오프

- confirmation을 높이면 reorg 확률은 낮아지지만 사용자 확정 지연이 증가합니다.
- background 재검증은 RPC 비용, backoff와 감사 이력 설계가 필요합니다.
- 현재 public verification은 저장된 `checkedAt` 이후 chain 변경을 자동 반영하지 않습니다.
- Sepolia 데모 정책을 실제 자산 서비스의 finality 정책으로 그대로 사용할 수 없습니다.

## 재현 근거

- chain snapshot/revert: `src/test/java/com/naesan/passport/support/AnvilProofChain.java`
- reorg 실험: `EvmProofAnchorAdapterIntegrationTest.reanchorsCommitmentAfterConfirmedBlockReorganization`
- EVM evidence schema: `src/main/resources/db/migration/V11__persist_evm_anchor_evidence.sql`
- commit: [`b6967af`](https://github.com/naesan-project/naesan-backend/commit/b6967af0e38258212722fe654631dc6a82e15269)
