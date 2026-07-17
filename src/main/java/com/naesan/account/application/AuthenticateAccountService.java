package com.naesan.account.application;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;

public class AuthenticateAccountService {
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    @Transactional(readOnly = true)
    public Account authenticate(String emailValue, String rawPassword) {
        Email email = new Email(emailValue);
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(AccountException::invalidCredentials);

        if (!passwordHasher.matches(rawPassword, account.passwordHash())
                || !account.canAuthenticate()) {
            throw AccountException.invalidCredentials();
        }

        return account;
    }
}
