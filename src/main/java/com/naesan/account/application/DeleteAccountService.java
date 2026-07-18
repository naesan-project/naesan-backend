package com.naesan.account.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.account.application.port.out.AccountEvidenceDeletion;
import com.naesan.account.application.port.out.AccountEvidenceDeletionResult;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;

public class DeleteAccountService {
    private final AccountRepository accountRepository;
    private final AccountEvidenceDeletion evidenceDeletion;
    private final TransactionTemplate transactionTemplate;

    public DeleteAccountService(
            AccountRepository accountRepository,
            AccountEvidenceDeletion evidenceDeletion,
            TransactionTemplate transactionTemplate
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.evidenceDeletion = Objects.requireNonNull(evidenceDeletion);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
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
    }
}
