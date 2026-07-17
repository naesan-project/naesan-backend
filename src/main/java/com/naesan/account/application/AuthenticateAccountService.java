package com.naesan.account.application;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;

public class AuthenticateAccountService {
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordHash missingAccountPasswordHash;

    public AuthenticateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordHash missingAccountPasswordHash
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.missingAccountPasswordHash = Objects.requireNonNull(missingAccountPasswordHash);
    }

    @Transactional(readOnly = true)
    public Account authenticate(String emailValue, String rawPassword) {
        Email email = new Email(emailValue);
        Optional<Account> foundAccount = accountRepository.findByEmail(email);
        PasswordHash passwordHash = foundAccount
                .map(Account::passwordHash)
                .orElse(missingAccountPasswordHash);
        boolean passwordMatches = passwordHasher.matches(rawPassword, passwordHash);

        Account account = foundAccount.orElseThrow(AccountException::invalidCredentials);
        if (!passwordMatches || !account.canAuthenticate()) {
            throw AccountException.invalidCredentials();
        }

        return account;
    }
}
