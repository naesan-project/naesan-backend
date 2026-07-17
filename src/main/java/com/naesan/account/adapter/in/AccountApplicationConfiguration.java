package com.naesan.account.adapter.in;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;

@Configuration(proxyBeanMethods = false)
public class AccountApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthenticateAccountService authenticateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher
    ) {
        return new AuthenticateAccountService(accountRepository, passwordHasher);
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
