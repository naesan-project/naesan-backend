package com.naesan.passport.domain;

import java.math.BigInteger;
import java.time.Instant;

public record EvmAnchorEvidence(
        BigInteger chainId,
        String contractAddress,
        String transactionHash,
        BigInteger blockNumber,
        String blockHash,
        int confirmations,
        byte[] readBackCommitment,
        Instant checkedAt
) {
    private static final int COMMITMENT_BYTES = 32;

    public EvmAnchorEvidence {
        if (chainId == null
                || contractAddress == null
                || transactionHash == null
                || blockNumber == null
                || blockHash == null
                || readBackCommitment == null
                || checkedAt == null) {
            throw new IllegalArgumentException("EVM 기준점 증거의 필수 값은 null일 수 없습니다.");
        }
        if (chainId.signum() <= 0 || blockNumber.signum() < 0 || confirmations <= 0) {
            throw new IllegalArgumentException("EVM 기준점의 수치 값이 유효하지 않습니다.");
        }
        if (!contractAddress.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("EVM contract address 형식이 유효하지 않습니다.");
        }
        if (!transactionHash.matches("0x[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("EVM transaction hash 형식이 유효하지 않습니다.");
        }
        if (!blockHash.matches("0x[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("EVM block hash 형식이 유효하지 않습니다.");
        }
        if (readBackCommitment.length != COMMITMENT_BYTES) {
            throw new IllegalArgumentException("EVM read-back commitment는 32 byte여야 합니다.");
        }
        readBackCommitment = readBackCommitment.clone();
    }

    @Override
    public byte[] readBackCommitment() {
        return readBackCommitment.clone();
    }
}
