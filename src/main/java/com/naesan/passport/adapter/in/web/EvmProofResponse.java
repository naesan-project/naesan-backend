package com.naesan.passport.adapter.in.web;

import java.time.Instant;
import java.util.HexFormat;

import com.naesan.passport.domain.EvmAnchorEvidence;
import com.naesan.passport.domain.ProofAnchor;

public record EvmProofResponse(
        String chainId,
        String contractAddress,
        String transactionHash,
        String blockNumber,
        String blockHash,
        int confirmations,
        String readBackCommitment,
        Instant anchoredAt,
        Instant checkedAt
) {

    static EvmProofResponse from(ProofAnchor proofAnchor) {
        EvmAnchorEvidence evidence = proofAnchor.evmEvidence();
        if (evidence == null) {
            return null;
        }
        return new EvmProofResponse(
                evidence.chainId().toString(),
                evidence.contractAddress(),
                evidence.transactionHash(),
                evidence.blockNumber().toString(),
                evidence.blockHash(),
                evidence.confirmations(),
                HexFormat.of().formatHex(evidence.readBackCommitment()),
                evidence.anchoredAt(),
                evidence.checkedAt()
        );
    }
}
