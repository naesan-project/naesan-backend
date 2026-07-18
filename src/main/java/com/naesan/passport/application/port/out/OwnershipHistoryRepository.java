package com.naesan.passport.application.port.out;

import java.util.List;
import java.util.UUID;

import com.naesan.passport.domain.OwnershipHistory;

public interface OwnershipHistoryRepository {

    void append(OwnershipHistory ownershipHistory);

    List<OwnershipHistory> findAllByPassportId(UUID passportId);
}
