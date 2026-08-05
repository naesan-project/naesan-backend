package com.naesan.share.application;

import java.time.Instant;
import java.util.HexFormat;

import com.naesan.passport.domain.EvmAnchorEvidence;

public record PublicEvmAnchorVerification(
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

    static PublicEvmAnchorVerification from(EvmAnchorEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        return new PublicEvmAnchorVerification(
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
