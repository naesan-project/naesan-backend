package com.naesan.transfer.application.port.out;

import java.util.List;
import java.util.UUID;

import com.naesan.transfer.application.TransferRequestDetails;

public interface TransferRequestQueryRepository {

    List<TransferRequestDetails> findAllOutgoing(UUID accountId);

    List<TransferRequestDetails> findAllIncoming(UUID accountId);
}
