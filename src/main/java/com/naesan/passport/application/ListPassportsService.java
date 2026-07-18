package com.naesan.passport.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.PassportQueryRepository;

public class ListPassportsService {
    private final PassportQueryRepository passportQueryRepository;

    public ListPassportsService(PassportQueryRepository passportQueryRepository) {
        this.passportQueryRepository = Objects.requireNonNull(passportQueryRepository);
    }

    @Transactional(readOnly = true)
    public List<PassportDetails> list(UUID holderAccountId) {
        return passportQueryRepository.findAllByHolderAccountId(holderAccountId);
    }
}
