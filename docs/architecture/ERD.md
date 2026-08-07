# 내산 데이터 모델

내산은 구매 증빙을 확정된 snapshot으로 고정한 뒤 패스를 발급합니다. 패스의 현재 보유자와 전체 소유 이력을 분리하고, 공개 공유·이전 요청·EVM 제출을 패스에 연결합니다.

```mermaid
erDiagram
    ACCOUNTS {
        uuid id PK
        string email UK
        string password_hash
        string status
        timestamptz created_at
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid account_id FK
        binary token_hash UK
        timestamptz expires_at
        timestamptz consumed_at
        timestamptz revoked_at
    }

    PURCHASE_EVIDENCE {
        uuid id PK
        uuid owner_account_id FK
        string merchant_name
        string product_name
        date purchased_at
        decimal amount
        string state
        bigint version
    }

    EVIDENCE_FILES {
        uuid id PK
        uuid evidence_id FK, UK
        string object_key UK
        string sha256
        string media_type
        string state
    }

    EVIDENCE_SNAPSHOTS {
        uuid id PK
        uuid evidence_id FK, UK
        int schema_version
        binary canonical_payload
        string snapshot_digest
    }

    PASSPORTS {
        uuid id PK
        uuid snapshot_id FK, UK
        uuid current_holder_account_id FK
        string status
        bigint version
    }

    OWNERSHIP_HISTORY {
        uuid id PK
        uuid passport_id FK
        uuid previous_holder_account_id FK
        uuid new_holder_account_id FK
        string reason
        timestamptz changed_at
    }

    PUBLIC_SHARES {
        uuid id PK
        uuid passport_id FK
        binary token_hash UK
        string capability
        timestamptz expires_at
        timestamptz revoked_at
    }

    TRANSFER_REQUESTS {
        uuid id PK
        uuid passport_id FK
        uuid requester_account_id FK
        uuid recipient_account_id FK
        string status
        bigint version
        timestamptz expires_at
    }

    PROOF_ANCHORS {
        uuid id PK
        uuid passport_id FK, UK
        binary commitment
        string state
        string transaction_hash UK
        decimal block_number
        int confirmation_count
    }

    OUTBOX_EVENTS {
        uuid id PK
        uuid aggregate_id FK
        uuid proof_anchor_id FK
        jsonb payload
        string dispatch_key UK
        string status
        int attempt_count
        bigint fencing_version
    }

    OUTBOX_REPROCESS_AUDIT {
        uuid id PK
        uuid outbox_event_id FK
        uuid proof_anchor_id FK
        string operator_id
        string previous_status
        string new_status
        int reprocess_number
    }

    ACCOUNTS ||--o{ REFRESH_TOKENS : owns
    ACCOUNTS ||--o{ PURCHASE_EVIDENCE : creates
    PURCHASE_EVIDENCE ||--o| EVIDENCE_FILES : attaches
    PURCHASE_EVIDENCE ||--o| EVIDENCE_SNAPSHOTS : confirms
    EVIDENCE_SNAPSHOTS ||--o| PASSPORTS : issues
    ACCOUNTS ||--o{ PASSPORTS : holds
    PASSPORTS ||--o{ OWNERSHIP_HISTORY : records
    ACCOUNTS ||--o{ OWNERSHIP_HISTORY : participates
    PASSPORTS ||--o{ PUBLIC_SHARES : exposes
    PASSPORTS ||--o{ TRANSFER_REQUESTS : transfers
    ACCOUNTS ||--o{ TRANSFER_REQUESTS : participates
    PASSPORTS ||--|| PROOF_ANCHORS : anchors
    PASSPORTS ||--o{ OUTBOX_EVENTS : publishes
    PROOF_ANCHORS ||--o{ OUTBOX_EVENTS : dispatches
    OUTBOX_EVENTS ||--o{ OUTBOX_REPROCESS_AUDIT : audits
    PROOF_ANCHORS ||--o{ OUTBOX_REPROCESS_AUDIT : references
```

## 설계 포인트

- 구매 증빙 원본 파일과 확정 snapshot을 분리해 패스가 변경 불가능한 발급 시점 데이터를 참조합니다.
- `passports.current_holder_account_id`는 현재 조회를 담당하고 `ownership_history`는 발급·이전 이력을 보존합니다.
- 공개 공유에는 capability token의 SHA-256 digest만 저장하며 원문 token은 저장하지 않습니다.
- 패스와 `outbox_events`는 같은 DB transaction에서 생성되고 EVM 제출 결과는 `proof_anchors`에 기록됩니다.
- 부분 unique index와 version·상태 제약으로 동시 공유·이전·Outbox 처리 경쟁을 보호합니다.

정확한 컬럼, check constraint와 index의 기준은 [`src/main/resources/db/migration`](../../src/main/resources/db/migration)입니다.
