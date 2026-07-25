package com.naesan.passport.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.domain.OwnershipHistory;

public class ListOwnershipHistoryService {
    private final PassportRepository passportRepository;
    private final OwnershipHistoryRepository ownershipHistoryRepository;

    public ListOwnershipHistoryService(
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository
    ) {
        this.passportRepository = Objects.requireNonNull(passportRepository);
        this.ownershipHistoryRepository = Objects.requireNonNull(ownershipHistoryRepository);
    }

    @Transactional(readOnly = true)
    public List<OwnershipHistory> list(UUID holderAccountId, UUID passportId) {
        passportRepository.findById(passportId)
                .filter(passport -> passport.currentHolderAccountId()
                        .equals(holderAccountId))
                .orElseThrow(PassportException::notFound);
        return ownershipHistoryRepository.findAllByPassportId(passportId);
    }
}
