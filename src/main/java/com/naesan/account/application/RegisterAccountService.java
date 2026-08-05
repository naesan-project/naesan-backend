package com.naesan.account.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;
import com.naesan.account.domain.PasswordPolicy;

public class RegisterAccountService {
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public RegisterAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Account register(String emailValue, String rawPassword) {
        Email email = new Email(emailValue);
        PasswordPolicy.validate(rawPassword);
        validateEmailAvailable(email);

        PasswordHash passwordHash = passwordHasher.hash(rawPassword);
        Account account = Account.create(
                UUID.randomUUID(),
                email,
                passwordHash,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );
        accountRepository.save(account);

        return account;
    }

    private void validateEmailAvailable(Email email) {
        if (accountRepository.existsByEmail(email)) {
            throw AccountException.emailAlreadyRegistered();
        }
    }
}
