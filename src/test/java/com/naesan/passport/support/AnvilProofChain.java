package com.naesan.passport.support;

import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;

public final class AnvilProofChain implements AutoCloseable {
    private static final DockerImageName ANVIL_IMAGE = DockerImageName.parse(
            "ghcr.io/foundry-rs/foundry:v1.7.1"
    );
    private static final int RPC_PORT = 8_545;
    private static final Pattern BYTECODE_PATTERN = Pattern.compile(
            "\\\"bytecode\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private final BigInteger chainId;
    private final String mnemonic;
    private final Credentials writer;
    private final GenericContainer<?> container;
    private Web3j web3j;
    private HttpService httpService;
    private String contractAddress;
    private BigInteger deploymentBlock;

    public AnvilProofChain(BigInteger chainId) {
        this.chainId = chainId;
        mnemonic = createMnemonic();
        writer = createWriter(mnemonic);
        container = new GenericContainer<>(ANVIL_IMAGE)
                .withExposedPorts(RPC_PORT)
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("anvil"))
                .withCommand(
                        "--host", "0.0.0.0",
                        "--chain-id", chainId.toString(),
                        "--mnemonic", mnemonic
                )
                .waitingFor(Wait.forLogMessage(".*Listening on.*", 1));
    }

    public void start() {
        try {
            container.start();
            httpService = new HttpService(rpcUrl().toString());
            web3j = Web3j.build(httpService);
            TransactionReceipt deployment = deployContract();
            contractAddress = deployment.getContractAddress();
            deploymentBlock = deployment.getBlockNumber();
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("로컬 EVM 테스트 체인을 준비하지 못했습니다.", exception);
        }
    }

    public void mine() {
        try {
            new Request<>("evm_mine", List.of(), httpService, Response.class).send();
        } catch (Exception exception) {
            throw new IllegalStateException("로컬 EVM 블록을 생성하지 못했습니다.", exception);
        }
    }

    public String snapshot() {
        try {
            SnapshotResponse response = new Request<>(
                    "evm_snapshot",
                    List.of(),
                    httpService,
                    SnapshotResponse.class
            ).send();
            if (response.hasError() || response.getResult() == null) {
                throw new IllegalStateException("로컬 EVM snapshot을 만들지 못했습니다.");
            }
            return response.getResult();
        } catch (Exception exception) {
            throw new IllegalStateException("로컬 EVM snapshot을 만들지 못했습니다.", exception);
        }
    }

    public void revert(String snapshotId) {
        try {
            RevertResponse response = new Request<>(
                    "evm_revert",
                    List.of(snapshotId),
                    httpService,
                    RevertResponse.class
            ).send();
            if (response.hasError() || !Boolean.TRUE.equals(response.getResult())) {
                throw new IllegalStateException("로컬 EVM snapshot으로 되돌리지 못했습니다.");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("로컬 EVM snapshot으로 되돌리지 못했습니다.", exception);
        }
    }

    public URI rpcUrl() {
        return URI.create(
                "http://" + container.getHost() + ":" + container.getMappedPort(RPC_PORT)
        );
    }

    public BigInteger chainId() {
        return chainId;
    }

    public Credentials writer() {
        return writer;
    }

    public String writerPrivateKey() {
        String value = writer.getEcKeyPair().getPrivateKey().toString(16);
        return "0".repeat(64 - value.length()) + value;
    }

    public Web3j web3j() {
        return web3j;
    }

    public String contractAddress() {
        return contractAddress;
    }

    public BigInteger deploymentBlock() {
        return deploymentBlock;
    }

    @Override
    public void close() {
        if (web3j != null) {
            web3j.shutdown();
        }
        if (container.isRunning()) {
            container.stop();
        }
    }

    private TransactionReceipt deployContract() throws Exception {
        String artifact = Files.readString(Path.of(
                "contracts/artifacts/contracts/ProofCommitmentAnchor.sol/ProofCommitmentAnchor.json"
        ));
        var matcher = BYTECODE_PATTERN.matcher(artifact);
        if (!matcher.find()) {
            throw new IllegalStateException("컨트랙트 bytecode를 찾을 수 없습니다.");
        }
        String constructor = TypeEncoder.encode(new Address(writer.getAddress()));
        RawTransactionManager transactions = new RawTransactionManager(
                web3j,
                writer,
                chainId.longValueExact(),
                20,
                100
        );
        var sent = transactions.sendTransaction(
                web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(4_000_000L),
                null,
                matcher.group(1) + constructor,
                BigInteger.ZERO,
                false
        );
        if (sent.hasError()) {
            throw new IllegalStateException(sent.getError().getMessage());
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            var receipt = web3j.ethGetTransactionReceipt(sent.getTransactionHash())
                    .send()
                    .getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.orElseThrow();
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("컨트랙트 배포 receipt를 찾을 수 없습니다.");
    }

    private static String createMnemonic() {
        byte[] entropy = new byte[16];
        new SecureRandom().nextBytes(entropy);
        return MnemonicUtils.generateMnemonic(entropy);
    }

    private static Credentials createWriter(String mnemonic) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        int hardened = Bip32ECKeyPair.HARDENED_BIT;
        return Credentials.create(Bip32ECKeyPair.deriveKeyPair(
                master,
                new int[] {44 | hardened, 60 | hardened, hardened, 0, 0}
        ));
    }

    public static final class SnapshotResponse extends Response<String> {
    }

    public static final class RevertResponse extends Response<Boolean> {
    }
}
