CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_email_unique UNIQUE (email),
    CONSTRAINT accounts_email_normalized CHECK (
        email = LOWER(email)
        AND OCTET_LENGTH(email) <= 254
        AND email ~ '^[!-~]+$'
        AND email ~ '^[^@]+@[^@]+$'
    ),
    CONSTRAINT accounts_password_hash_bcrypt CHECK (
        password_hash ~ '^\$2[ayb]\$12\$[./A-Za-z0-9]{53}$'
    ),
    CONSTRAINT accounts_status_valid CHECK (
        status IN ('ACTIVE', 'DELETION_PENDING', 'DELETED')
    )
);
