package com.naesan.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("75637870-c7a3-4c49-9d8f-0609c69d2b40");
    private static final Instant CREATED_AT = Instant.parse("2026-07-15T00:00:00Z");
    private static final Email EMAIL = new Email("User@Example.com");
    private static final PasswordHash PASSWORD_HASH =
            new PasswordHash("$2b$12$" + "a".repeat(53));

    @Test
    @DisplayName("신규 계정을 ACTIVE 상태로 생성한다")
    void createsActiveAccount() {
        Account account = Account.create(ACCOUNT_ID, EMAIL, PASSWORD_HASH, CREATED_AT);

        assertThat(account.id()).isEqualTo(ACCOUNT_ID);
        assertThat(account.email()).isEqualTo(new Email("user@example.com"));
        assertThat(account.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("저장된 계정 상태를 복원하고 ACTIVE 계정만 인증을 허용한다")
    void restoresAccountStatus() {
        Account activeAccount = Account.restore(
                ACCOUNT_ID,
                EMAIL,
                PASSWORD_HASH,
                AccountStatus.ACTIVE,
                CREATED_AT
        );
        Account inactiveAccount = Account.restore(
                UUID.randomUUID(),
                EMAIL,
                PASSWORD_HASH,
                AccountStatus.DELETION_PENDING,
                CREATED_AT
        );

        assertThat(activeAccount.canAuthenticate()).isTrue();
        assertThat(inactiveAccount.canAuthenticate()).isFalse();
    }

    @Test
    @DisplayName("필수 값이 null이면 계정을 생성하지 않는다")
    void rejectsNullRequiredValue() {
        assertThatThrownBy(() -> Account.create(null, EMAIL, PASSWORD_HASH, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.create(ACCOUNT_ID, null, PASSWORD_HASH, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.create(ACCOUNT_ID, EMAIL, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.create(ACCOUNT_ID, EMAIL, PASSWORD_HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.restore(
                ACCOUNT_ID,
                EMAIL,
                PASSWORD_HASH,
                null,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UUID가 같으면 동일한 계정이다")
    void comparesAccountById() {
        Account account = Account.create(ACCOUNT_ID, EMAIL, PASSWORD_HASH, CREATED_AT);
        Account sameAccount = Account.create(
                ACCOUNT_ID,
                new Email("another@example.com"),
                new PasswordHash("$2a$12$" + "b".repeat(53)),
                CREATED_AT.plusSeconds(1)
        );

        assertThat(account).isEqualTo(sameAccount);
        assertThat(account.hashCode()).isEqualTo(sameAccount.hashCode());
    }

    @Test
    @DisplayName("UUID가 다르면 서로 다른 계정이다")
    void distinguishesAccountById() {
        Account account = Account.create(ACCOUNT_ID, EMAIL, PASSWORD_HASH, CREATED_AT);
        Account anotherAccount = Account.create(UUID.randomUUID(), EMAIL, PASSWORD_HASH, CREATED_AT);

        assertThat(account).isNotEqualTo(anotherAccount);
    }
}
