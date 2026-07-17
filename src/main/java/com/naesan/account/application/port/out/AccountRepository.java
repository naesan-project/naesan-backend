package com.naesan.account.application.port.out;

import java.util.Optional;

import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;

public interface AccountRepository {

    boolean existsByEmail(Email email);

    Optional<Account> findByEmail(Email email);

    void save(Account account);
}
