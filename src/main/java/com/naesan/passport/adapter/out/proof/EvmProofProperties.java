package com.naesan.passport.adapter.out.proof;

import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;

import org.web3j.crypto.WalletUtils;

public record EvmProofProperties(
        URI rpcUrl,
        BigInteger chainId,
        String contractAddress,
        BigInteger deploymentBlock,
        int requiredConfirmations,
        int receiptAttempts,
        Duration receiptPollInterval
) {

    public EvmProofProperties {
        if (rpcUrl == null
                || rpcUrl.getHost() == null
                || !("http".equalsIgnoreCase(rpcUrl.getScheme())
                || "https".equalsIgnoreCase(rpcUrl.getScheme()))) {
            throw new IllegalArgumentException("EVM RPC URL은 유효해야 합니다.");
        }
        if (chainId == null || chainId.signum() <= 0) {
            throw new IllegalArgumentException("EVM chain ID는 0보다 커야 합니다.");
        }
        if (contractAddress == null || !WalletUtils.isValidAddress(contractAddress)) {
            throw new IllegalArgumentException("EVM contract address가 유효하지 않습니다.");
        }
        if (deploymentBlock == null || deploymentBlock.signum() < 0) {
            throw new IllegalArgumentException("EVM deployment block은 0 이상이어야 합니다.");
        }
        if (requiredConfirmations <= 0) {
            throw new IllegalArgumentException("EVM confirmation 수는 0보다 커야 합니다.");
        }
        if (receiptAttempts <= 0) {
            throw new IllegalArgumentException("EVM receipt 조회 횟수는 0보다 커야 합니다.");
        }
        if (receiptPollInterval == null || receiptPollInterval.isNegative()) {
            throw new IllegalArgumentException("EVM receipt 조회 간격은 음수일 수 없습니다.");
        }
    }
}
