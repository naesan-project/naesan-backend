package com.naesan.passport.adapter.out.proof;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderCapabilities;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.domain.EvmAnchorEvidence;

public final class EvmProofAnchorAdapter implements ProofAnchorPort {
    private static final BigInteger MINIMUM_GAS_LIMIT = BigInteger.valueOf(100_000L);
    private static final ProofProviderCapabilities CAPABILITIES =
            new ProofProviderCapabilities(true, true);
    private static final Event ANCHORED_EVENT = new Event(
            "CommitmentAnchored",
            List.of(
                    new TypeReference<Bytes32>(true) { },
                    new TypeReference<Uint64>() { }
            )
    );

    private final Web3j web3j;
    private final Credentials credentials;
    private final EvmProofProperties properties;
    private final Clock clock;

    public EvmProofAnchorAdapter(
            Web3j web3j,
            Credentials credentials,
            EvmProofProperties properties,
            Clock clock
    ) {
        this.web3j = Objects.requireNonNull(web3j);
        this.credentials = Objects.requireNonNull(credentials);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ProofProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    public void verifyConfiguration() {
        try {
            BigInteger actualChainId = web3j.ethChainId().send().getChainId();
            if (!properties.chainId().equals(actualChainId)) {
                throw permanent("CHAIN_ID_MISMATCH");
            }
            String code = web3j.ethGetCode(
                    properties.contractAddress(),
                    DefaultBlockParameterName.LATEST
            ).send().getCode();
            if (code == null || code.equals("0x") || code.equals("0x0")) {
                throw permanent("CONTRACT_NOT_FOUND");
            }
        } catch (IOException exception) {
            throw retryable("RPC_UNAVAILABLE");
        }
    }

    @Override
    public ProofAnchorReceipt submit(ProofAnchorCommand command) {
        verifyConfiguration();
        Optional<ProofAnchorReceipt> existing = lookup(command.commitment());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        String transactionHash;
        try {
            transactionHash = sendAnchorTransaction(command.commitment());
        } catch (ProofProviderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw ambiguous("SUBMIT_RESULT_UNKNOWN");
        }

        return receipt(transactionHash, decodeCommitment(command.commitment()))
                .orElseGet(() -> new ProofAnchorReceipt(
                transactionHash,
                clock.instant(),
                false
        ));
    }

    @Override
    public Optional<ProofAnchorReceipt> lookup(String commitment) {
        verifyConfiguration();
        byte[] commitmentBytes = decodeCommitment(commitment);
        LookupResult lookupResult = callLookup(commitmentBytes);
        if (!lookupResult.exists()) {
            return Optional.empty();
        }

        String transactionHash = findAnchorTransaction(commitmentBytes)
                .orElseThrow(() -> permanent("ANCHOR_EVENT_NOT_FOUND"));
        Optional<ProofAnchorReceipt> receipt = receipt(transactionHash, commitmentBytes);
        if (receipt.isEmpty()) {
            return Optional.of(new ProofAnchorReceipt(
                    transactionHash,
                    lookupResult.anchoredAt(),
                    false
            ));
        }
        ProofAnchorReceipt resolved = receipt.orElseThrow();
        if (!resolved.anchoredAt().equals(lookupResult.anchoredAt())) {
            throw permanent("READ_BACK_MISMATCH");
        }
        return Optional.of(resolved);
    }

    private String sendAnchorTransaction(String commitment) throws IOException {
        String data = FunctionEncoder.encode(new Function(
                "anchor",
                List.of(new Bytes32(decodeCommitment(commitment))),
                List.of()
        ));
        String sender = credentials.getAddress();
        BigInteger nonce = web3j.ethGetTransactionCount(
                sender,
                DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = estimateGas(sender, data);
        RawTransaction transaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                properties.contractAddress(),
                BigInteger.ZERO,
                data
        );
        byte[] signed = TransactionEncoder.signMessage(
                transaction,
                properties.chainId().longValueExact(),
                credentials
        );
        EthSendTransaction response = web3j.ethSendRawTransaction(
                Numeric.toHexString(signed)
        ).send();
        if (response.hasError()) {
            throw classifySubmissionError(response.getError().getMessage());
        }
        String transactionHash = response.getTransactionHash();
        if (transactionHash == null || transactionHash.isBlank()) {
            throw ambiguous("SUBMIT_RESULT_UNKNOWN");
        }
        return transactionHash;
    }

    private BigInteger estimateGas(String sender, String data) throws IOException {
        EthEstimateGas response = web3j.ethEstimateGas(
                Transaction.createFunctionCallTransaction(
                        sender,
                        null,
                        null,
                        null,
                        properties.contractAddress(),
                        BigInteger.ZERO,
                        data
                )
        ).send();
        if (response.hasError()) {
            throw classifySubmissionError(response.getError().getMessage());
        }
        BigInteger estimate = response.getAmountUsed();
        return estimate == null
                ? MINIMUM_GAS_LIMIT
                : estimate.max(MINIMUM_GAS_LIMIT);
    }

    private Optional<ProofAnchorReceipt> receipt(
            String transactionHash,
            byte[] commitment
    ) {
        for (int attempt = 0; attempt < properties.receiptAttempts(); attempt++) {
            try {
                Optional<TransactionReceipt> receipt = web3j
                        .ethGetTransactionReceipt(transactionHash)
                        .send()
                        .getTransactionReceipt();
                if (receipt.isPresent()) {
                    return Optional.of(toProofReceipt(
                            receipt.orElseThrow(),
                            commitment
                    ));
                }
                pause();
            } catch (IOException exception) {
                throw retryable("RPC_UNAVAILABLE");
            }
        }
        return Optional.empty();
    }

    private ProofAnchorReceipt toProofReceipt(
            TransactionReceipt receipt,
            byte[] commitment
    ) {
        if (!receipt.isStatusOK()) {
            throw permanent("CONTRACT_REVERT");
        }
        BigInteger blockNumber = receipt.getBlockNumber();
        EthBlock.Block block = getBlock(blockNumber);
        int confirmations = currentConfirmations(blockNumber);
        LookupResult readBack = callLookup(commitment);
        if (!readBack.exists()) {
            throw permanent("READ_BACK_MISMATCH");
        }
        Instant anchoredAt = Instant.ofEpochSecond(
                block.getTimestamp().longValueExact()
        );
        if (!anchoredAt.equals(readBack.anchoredAt())) {
            throw permanent("READ_BACK_MISMATCH");
        }
        EvmAnchorEvidence evidence = new EvmAnchorEvidence(
                properties.chainId(),
                properties.contractAddress(),
                receipt.getTransactionHash(),
                blockNumber,
                block.getHash(),
                confirmations,
                commitment,
                clock.instant()
        );
        return new ProofAnchorReceipt(
                receipt.getTransactionHash(),
                anchoredAt,
                confirmations >= properties.requiredConfirmations(),
                evidence
        );
    }

    private EthBlock.Block getBlock(BigInteger blockNumber) {
        try {
            EthBlock.Block block = web3j.ethGetBlockByNumber(
                    new DefaultBlockParameterNumber(blockNumber),
                    false
            ).send().getBlock();
            if (block == null) {
                throw retryable("BLOCK_NOT_FOUND");
            }
            return block;
        } catch (IOException exception) {
            throw retryable("RPC_UNAVAILABLE");
        }
    }

    private int currentConfirmations(BigInteger anchoredBlock) {
        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            BigInteger count = head.subtract(anchoredBlock).add(BigInteger.ONE);
            return count.max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE))
                    .intValueExact();
        } catch (IOException exception) {
            throw retryable("RPC_UNAVAILABLE");
        }
    }

    private LookupResult callLookup(byte[] commitment) {
        Function function = new Function(
                "lookup",
                List.of(new Bytes32(commitment)),
                List.of(new TypeReference<Bool>() { }, new TypeReference<Uint64>() { })
        );
        try {
            var response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            properties.contractAddress(),
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();
            if (response.hasError()) {
                throw permanent("CONTRACT_MISMATCH");
            }
            List<Type> values = FunctionReturnDecoder.decode(
                    response.getValue(),
                    function.getOutputParameters()
            );
            if (values.size() != 2) {
                throw permanent("CONTRACT_MISMATCH");
            }
            boolean exists = (Boolean) values.get(0).getValue();
            BigInteger anchoredAt = (BigInteger) values.get(1).getValue();
            return new LookupResult(
                    exists,
                    Instant.ofEpochSecond(anchoredAt.longValueExact())
            );
        } catch (IOException exception) {
            throw retryable("RPC_UNAVAILABLE");
        } catch (ArithmeticException | ClassCastException exception) {
            throw permanent("CONTRACT_MISMATCH");
        }
    }

    private Optional<String> findAnchorTransaction(byte[] commitment) {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(properties.deploymentBlock()),
                DefaultBlockParameterName.LATEST,
                properties.contractAddress()
        );
        filter.addSingleTopic(EventEncoder.encode(ANCHORED_EVENT));
        filter.addSingleTopic(Numeric.toHexString(commitment));
        try {
            List<EthLog.LogResult<?>> logs = web3j.ethGetLogs(filter).send().getLogs();
            if (logs.isEmpty()) {
                return Optional.empty();
            }
            Object value = logs.get(0).get();
            if (!(value instanceof EthLog.LogObject log)) {
                throw permanent("ANCHOR_EVENT_INVALID");
            }
            return Optional.ofNullable(log.getTransactionHash());
        } catch (IOException exception) {
            throw retryable("RPC_UNAVAILABLE");
        }
    }

    private byte[] decodeCommitment(String commitment) {
        if (commitment == null || !commitment.matches("[0-9a-f]{64}")) {
            throw permanent("INVALID_COMMITMENT");
        }
        return Numeric.hexStringToByteArray("0x" + commitment);
    }

    private ProofProviderException classifySubmissionError(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("revert")
                || normalized.contains("unauthorized")
                || normalized.contains("already anchored")) {
            return permanent("CONTRACT_REVERT");
        }
        if (normalized.contains("insufficient funds")) {
            return permanent("INSUFFICIENT_FUNDS");
        }
        return retryable("TRANSACTION_REJECTED");
    }

    private void pause() {
        Duration delay = properties.receiptPollInterval();
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw retryable("WAIT_INTERRUPTED");
        }
    }

    private ProofProviderException retryable(String code) {
        return new ProofProviderException(ProofFailureType.RETRYABLE, code);
    }

    private ProofProviderException permanent(String code) {
        return new ProofProviderException(ProofFailureType.PERMANENT, code);
    }

    private ProofProviderException ambiguous(String code) {
        return new ProofProviderException(ProofFailureType.AMBIGUOUS, code);
    }

    private record LookupResult(boolean exists, Instant anchoredAt) {
    }
}
