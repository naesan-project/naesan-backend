package com.naesan.account.application.port.out;

import java.util.UUID;

public interface AccountEvidenceDeletion {

    AccountEvidenceDeletionResult deleteAll(UUID accountId);
}
