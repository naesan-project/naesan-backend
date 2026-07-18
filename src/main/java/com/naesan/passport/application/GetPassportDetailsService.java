package com.naesan.passport.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.PassportQueryRepository;

public class GetPassportDetailsService {
    private final PassportQueryRepository passportQueryRepository;

    public GetPassportDetailsService(PassportQueryRepository passportQueryRepository) {
        this.passportQueryRepository = Objects.requireNonNull(passportQueryRepository);
    }

    @Transactional(readOnly = true)
    public PassportDetails get(UUID holderAccountId, UUID passportId) {
        return passportQueryRepository.findByIdAndHolderAccountId(
                        passportId,
                        holderAccountId
                )
                .orElseThrow(PassportException::notFound);
    }
}
