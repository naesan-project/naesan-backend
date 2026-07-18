package com.naesan.security;

import java.io.Serializable;
import java.util.UUID;

import com.naesan.account.domain.Account;

public record AuthenticatedAccount(
        UUID id,
        String email
) implements Serializable {

    public static AuthenticatedAccount from(Account account) {
        return new AuthenticatedAccount(
                account.id(),
                account.email().value()
        );
    }
}
