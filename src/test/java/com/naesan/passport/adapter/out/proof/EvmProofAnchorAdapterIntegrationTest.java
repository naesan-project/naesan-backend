package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;

@Tag("evm")
class EvmProofAnchorAdapterIntegrationTest {
    private static final DockerImageName ANVIL_IMAGE = DockerImageName.parse(
            "ghcr.io/foundry-rs/foundry:v1.7.1"
    );
    private static final BigInteger CHAIN_ID = BigInteger.valueOf(31_337L);
    private static final int RPC_PORT = 8_545;
    private static final String COMMITMENT = "a".repeat(64);
    private static final Pattern BYTECODE_PATTERN = Pattern.compile(
            "\\\"bytecode\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );
    private static final String MNEMONIC = createMnemonic();
    private static final Credentials WRITER = createWriter();
    private static final GenericContainer<?> ANVIL = new GenericContainer<>(ANVIL_IMAGE)
            .withExposedPorts(RPC_PORT)
            .withCreateContainerCmdModifier(command -> command.withEntrypoint("anvil"))
            .withCommand(
                    "--host", "0.0.0.0",
                    "--chain-id", CHAIN_ID.toString(),
                    "--mnemonic", MNEMONIC
            )
            .waitingFor(Wait.forLogMessage(".*Listening on.*", 1));

    private static Web3j web3j;
    private static HttpService httpService;
    private static String contractAddress;
    private static BigInteger deploymentBlock;

    @BeforeAll
    static void startChainAndDeployContract() throws Exception {
        ANVIL.start();
        httpService = new HttpService(rpcUrl().toString());
        web3j = Web3j.build(httpService);
        TransactionReceipt deployment = deployContract();
        contractAddress = deployment.getContractAddress();
        deploymentBlock = deployment.getBlockNumber();
    }

    @AfterAll
    static void stopChain() {
        if (web3j != null) {
            web3j.shutdown();
        }
        ANVIL.stop();
    }

    @Test
    @DisplayName("실제 EVM에 commitment를 제출하고 컨트랙트에서 다시 조회한다")
    void submitsAndReadsBackCommitment() {
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, contractAddress, 1);

        var submitted = adapter.submit(command(COMMITMENT));
        var lookedUp = adapter.lookup(COMMITMENT);

        assertThat(submitted.confirmed()).isTrue();
        assertThat(submitted.externalReference()).startsWith("0x").hasSize(66);
        assertThat(lookedUp).contains(submitted);
    }

    @Test
    @DisplayName("같은 commitment 재제출은 새 transaction 없이 기존 기준점을 반환한다")
    void deduplicatesSubmissionByLookup() {
        String commitment = "b".repeat(64);
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, contractAddress, 1);

        var first = adapter.submit(command(commitment));
        var second = adapter.submit(command(commitment));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("필요한 confirmation 전에는 미확정으로 반환하고 블록 생성 후 확정한다")
    void waitsForRequiredConfirmations() throws IOException {
        String commitment = "c".repeat(64);
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, contractAddress, 2);

        var submitted = adapter.submit(command(commitment));
        assertThat(submitted.confirmed()).isFalse();

        new Request<>("evm_mine", List.of(), httpService, Response.class).send();

        assertThat(adapter.lookup(commitment)).get().extracting("confirmed")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("설정한 chain ID가 RPC와 다르면 transaction 전에 영구 실패한다")
    void rejectsWrongChain() {
        EvmProofAnchorAdapter adapter = adapter(
                BigInteger.valueOf(11_155_111L),
                contractAddress,
                1
        );

        assertThatThrownBy(() -> adapter.submit(command("d".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CHAIN_ID_MISMATCH");
                });
    }

    @Test
    @DisplayName("배포 코드가 없는 주소는 transaction 전에 영구 실패한다")
    void rejectsMissingContract() {
        EvmProofAnchorAdapter adapter = adapter(
                CHAIN_ID,
                "0x0000000000000000000000000000000000000001",
                1
        );

        assertThatThrownBy(() -> adapter.submit(command("e".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CONTRACT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("zero commitment의 컨트랙트 revert를 영구 실패로 분류한다")
    void classifiesContractRevertAsPermanent() {
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, contractAddress, 1);

        assertThatThrownBy(() -> adapter.submit(command("0".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CONTRACT_REVERT");
                });
    }

    private static EvmProofAnchorAdapter adapter(
            BigInteger chainId,
            String address,
            int confirmations
    ) {
        EvmProofProperties properties = new EvmProofProperties(
                rpcUrl(),
                chainId,
                address,
                deploymentBlock,
                confirmations,
                3,
                Duration.ZERO
        );
        return new EvmProofAnchorAdapter(web3j, WRITER, properties, Clock.systemUTC());
    }

    private static ProofAnchorCommand command(String commitment) {
        return new ProofAnchorCommand("proof-anchor:" + commitment, commitment);
    }

    private static TransactionReceipt deployContract() throws Exception {
        String artifact = Files.readString(Path.of(
                "contracts/artifacts/contracts/ProofCommitmentAnchor.sol/ProofCommitmentAnchor.json"
        ));
        var matcher = BYTECODE_PATTERN.matcher(artifact);
        if (!matcher.find()) {
            throw new IllegalStateException("컨트랙트 bytecode를 찾을 수 없습니다.");
        }
        String bytecode = matcher.group(1);
        String constructor = TypeEncoder.encode(new Address(WRITER.getAddress()));
        RawTransactionManager transactions = new RawTransactionManager(
                web3j,
                WRITER,
                CHAIN_ID.longValueExact(),
                20,
                100
        );
        var sent = transactions.sendTransaction(
                web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(4_000_000L),
                null,
                bytecode + constructor,
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

    private static Credentials createWriter() {
        byte[] seed = MnemonicUtils.generateSeed(MNEMONIC, "");
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        int hardened = Bip32ECKeyPair.HARDENED_BIT;
        Bip32ECKeyPair firstAccount = Bip32ECKeyPair.deriveKeyPair(
                master,
                new int[] {44 | hardened, 60 | hardened, hardened, 0, 0}
        );
        return Credentials.create(firstAccount);
    }

    private static String createMnemonic() {
        byte[] entropy = new byte[16];
        new SecureRandom().nextBytes(entropy);
        return MnemonicUtils.generateMnemonic(entropy);
    }

    private static URI rpcUrl() {
        return URI.create("http://" + ANVIL.getHost() + ":" + ANVIL.getMappedPort(RPC_PORT));
    }
}
