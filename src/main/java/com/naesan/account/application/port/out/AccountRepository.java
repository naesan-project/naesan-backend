package com.naesan.account.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;

public interface AccountRepository {

    boolean existsByEmail(Email email);

    Optional<Account> findByEmail(Email email);

    Optional<Account> findById(UUID accountId);

    void save(Account account);

    void update(Account account);
}
