package com.naesan.transfer.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.transfer.domain.TransferRequest;

public interface TransferRequestRepository {

    void save(TransferRequest request);

    void update(TransferRequest request);

    Optional<TransferRequest> findById(UUID requestId);

    Optional<TransferRequest> findByIdForUpdate(UUID requestId);

    Optional<TransferRequest> findPendingByPassportIdForUpdate(UUID passportId);

}
