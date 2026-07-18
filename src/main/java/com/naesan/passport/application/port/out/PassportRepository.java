package com.naesan.passport.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.naesan.passport.domain.Passport;

public interface PassportRepository {

    void save(Passport passport);

    Optional<Passport> findById(UUID passportId);

    Optional<Passport> findBySnapshotId(UUID snapshotId);

    List<Passport> findAllByCurrentHolderAccountId(UUID accountId);
}
