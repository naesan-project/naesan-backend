package com.naesan.account.adapter.in;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.application.DeleteAccountService;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.application.port.out.AccountEvidenceDeletion;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.PasswordHash;

@Configuration(proxyBeanMethods = false)
public class AccountApplicationConfiguration {
    private static final String MISSING_ACCOUNT_PASSWORD = "missing-account-password";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthenticateAccountService authenticateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher
    ) {
        PasswordHash missingAccountPasswordHash = passwordHasher.hash(MISSING_ACCOUNT_PASSWORD);
        return new AuthenticateAccountService(
                accountRepository,
                passwordHasher,
                missingAccountPasswordHash
        );
    }

    @Bean
    DeleteAccountService deleteAccountService(
            AccountRepository accountRepository,
            AccountEvidenceDeletion evidenceDeletion,
            PlatformTransactionManager transactionManager
    ) {
        return new DeleteAccountService(
                accountRepository,
                evidenceDeletion,
                new TransactionTemplate(transactionManager)
        );
    }

    @Bean
    RegisterAccountService registerAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        return new RegisterAccountService(accountRepository, passwordHasher, clock);
    }
}
