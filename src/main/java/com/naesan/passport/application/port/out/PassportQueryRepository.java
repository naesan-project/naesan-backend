package com.naesan.passport.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.naesan.passport.application.PassportDetails;

public interface PassportQueryRepository {

    List<PassportDetails> findAllByHolderAccountId(UUID accountId);

    Optional<PassportDetails> findByIdAndHolderAccountId(
            UUID passportId,
            UUID accountId
    );
}
