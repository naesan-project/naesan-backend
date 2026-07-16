package com.naesan.account.application.port.out;

import com.naesan.account.domain.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);
}
