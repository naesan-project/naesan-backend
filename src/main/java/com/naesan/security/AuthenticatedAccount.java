package com.naesan.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.naesan.account.domain.Account;

public record AuthenticatedAccount(
        UUID id,
        String email,
        Instant authenticatedAt
) implements Serializable {

    public static AuthenticatedAccount from(Account account, Instant authenticatedAt) {
        return new AuthenticatedAccount(
                account.id(),
                account.email().value(),
                authenticatedAt
        );
    }
}
