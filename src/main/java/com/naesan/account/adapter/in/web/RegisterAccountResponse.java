package com.naesan.account.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.account.domain.Account;

public record RegisterAccountResponse(
        UUID id,
        String email,
        Instant createdAt
) {

    public static RegisterAccountResponse from(Account account) {
        return new RegisterAccountResponse(
                account.id(),
                account.email().value(),
                account.createdAt()
        );
    }
}
