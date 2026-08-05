package com.naesan.account.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
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
import com.naesan.security.RefreshTokenRepository;
import com.naesan.account.adapter.in.web.RefreshTokenCookieManager;

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
            RefreshTokenRepository refreshTokenRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        return new DeleteAccountService(
                accountRepository,
                evidenceDeletion,
                refreshTokenRepository,
                new TransactionTemplate(transactionManager),
                clock
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

    @Bean
    RefreshTokenCookieManager refreshTokenCookieManager(
            @Value("${naesan.security.token.refresh-cookie-secure}") boolean secure,
            @Value("${naesan.security.token.refresh-time-to-live}") Duration timeToLive
    ) {
        return new RefreshTokenCookieManager(secure, timeToLive);
    }
}
