package com.naesan.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.account.application.port.out.AccountEvidenceDeletion;
import com.naesan.account.application.port.out.AccountEvidenceDeletionResult;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;
import com.naesan.security.RefreshTokenRepository;

public class DeleteAccountService {
    private final AccountRepository accountRepository;
    private final AccountEvidenceDeletion evidenceDeletion;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DeleteAccountService(
            AccountRepository accountRepository,
            AccountEvidenceDeletion evidenceDeletion,
            RefreshTokenRepository refreshTokenRepository,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.evidenceDeletion = Objects.requireNonNull(evidenceDeletion);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    public AccountEvidenceDeletionResult delete(UUID accountId) {
        transactionTemplate.executeWithoutResult(status ->
                blockAccountAccess(accountId)
        );
        return evidenceDeletion.deleteAll(accountId);
    }

    private void blockAccountAccess(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountException::invalidCredentials);
        Account deletionPending = account.requestDeletion();
        if (deletionPending.status() != account.status()) {
            accountRepository.update(deletionPending);
        }
        refreshTokenRepository.revokeAll(accountId, Instant.now(clock));
    }
}
