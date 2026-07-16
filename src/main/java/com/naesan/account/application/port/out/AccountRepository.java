package com.naesan.account.application.port.out;

import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;

public interface AccountRepository {

    boolean existsByEmail(Email email);

    void save(Account account);
}
