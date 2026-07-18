package com.naesan.account.adapter.in.web;

import java.util.UUID;

import com.naesan.security.AuthenticatedAccount;

public record AccountSessionResponse(
        UUID accountId,
        String email
) {

    public static AccountSessionResponse from(AuthenticatedAccount account) {
        return new AccountSessionResponse(
                account.id(),
                account.email()
        );
    }
}
