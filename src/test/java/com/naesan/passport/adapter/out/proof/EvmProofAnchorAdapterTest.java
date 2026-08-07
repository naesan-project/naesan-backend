package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthLog;

import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;

class EvmProofAnchorAdapterTest {

    @Test
    @DisplayName("nonce 조회 전 RPC 실패는 안전하게 재시도할 수 있다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void classifiesPreBroadcastRpcFailureAsRetryable() throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request request = mock(Request.class);
        when(web3j.ethGetTransactionCount(anyString(), any())).thenReturn(request);
        when(request.send()).thenThrow(new IOException("unavailable"));
        EvmProofAnchorAdapter adapter = adapter(web3j);

        assertThatThrownBy(() -> adapter.sendAnchorTransaction("a".repeat(64)))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.RETRYABLE);
                    assertThat(failure.errorCode()).isEqualTo("RPC_UNAVAILABLE");
                });
    }

    @Test
    @DisplayName("서명 transaction 전송 중 RPC 실패는 결과 불명으로 분류한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void classifiesBroadcastRpcFailureAsAmbiguous() throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request nonceRequest = mock(Request.class);
        Request gasPriceRequest = mock(Request.class);
        Request estimateRequest = mock(Request.class);
        Request submitRequest = mock(Request.class);
        EthGetTransactionCount nonce = mock(EthGetTransactionCount.class);
        EthGasPrice gasPrice = mock(EthGasPrice.class);
        EthEstimateGas estimate = mock(EthEstimateGas.class);
        when(web3j.ethGetTransactionCount(anyString(), any())).thenReturn(nonceRequest);
        when(web3j.ethGasPrice()).thenReturn(gasPriceRequest);
        when(web3j.ethEstimateGas(any())).thenReturn(estimateRequest);
        when(web3j.ethSendRawTransaction(anyString())).thenReturn(submitRequest);
        when(nonceRequest.send()).thenReturn(nonce);
        when(gasPriceRequest.send()).thenReturn(gasPrice);
        when(estimateRequest.send()).thenReturn(estimate);
        when(nonce.getTransactionCount()).thenReturn(BigInteger.ZERO);
        when(gasPrice.getGasPrice()).thenReturn(BigInteger.ONE);
        when(estimate.getAmountUsed()).thenReturn(BigInteger.valueOf(100_000L));
        when(submitRequest.send()).thenThrow(new IOException("result unknown"));
        EvmProofAnchorAdapter adapter = adapter(web3j);

        assertThatThrownBy(() -> adapter.sendAnchorTransaction("a".repeat(64)))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.AMBIGUOUS);
                    assertThat(failure.errorCode()).isEqualTo("SUBMIT_RESULT_UNKNOWN");
                });
    }

    @Test
    @DisplayName("중복 commitment custom error를 기존 결과 복구 대상으로 분류한다")
    void classifiesDuplicateCommitmentError() {
        EvmProofAnchorAdapter adapter = adapter(mock(Web3j.class));
        Response.Error error = new Response.Error(-32_000, "execution reverted");
        error.setData("0x" + "00".repeat(32));
        ProofProviderException generic = adapter.classifySubmissionError(error);

        error.setData("0xc2dc9aad" + "00".repeat(32));
        ProofProviderException duplicate = adapter.classifySubmissionError(error);

        assertThat(generic.errorCode()).isEqualTo("CONTRACT_REVERT");
        assertThat(duplicate.failureType()).isEqualTo(ProofFailureType.PERMANENT);
        assertThat(duplicate.errorCode()).isEqualTo("COMMITMENT_ALREADY_ANCHORED");
    }

    @Test
    @DisplayName("writer nonce 경쟁으로 거부된 transaction은 구분 가능한 재시도 오류다")
    void classifiesWriterNonceConflict() {
        EvmProofAnchorAdapter adapter = adapter(mock(Web3j.class));
        Response.Error error = new Response.Error(-32_003, "nonce too low");

        ProofProviderException failure = adapter.classifySubmissionError(error);

        assertThat(failure.failureType()).isEqualTo(ProofFailureType.RETRYABLE);
        assertThat(failure.errorCode()).isEqualTo("NONCE_CONFLICT");
    }

    @Test
    @DisplayName("anchor 이벤트 조회는 최신 블록부터 10블록 단위로 나눈다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void looksUpAnchorEventInTenBlockChunks() throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request blockNumberRequest = mock(Request.class);
        Request logRequest = mock(Request.class);
        EthBlockNumber blockNumber = mock(EthBlockNumber.class);
        EthLog emptyLogs = mock(EthLog.class);
        EthLog matchingLogs = mock(EthLog.class);
        EthLog.LogObject matchingLog = new EthLog.LogObject(
                false,
                "0x0",
                "0x0",
                "0xtransaction",
                "0xblock",
                "0x4",
                "0x0000000000000000000000000000000000000001",
                "0x",
                null,
                List.of()
        );
        ArgumentCaptor<EthFilter> filterCaptor = ArgumentCaptor.forClass(EthFilter.class);
        when(web3j.ethBlockNumber()).thenReturn(blockNumberRequest);
        when(blockNumberRequest.send()).thenReturn(blockNumber);
        when(blockNumber.getBlockNumber()).thenReturn(BigInteger.valueOf(24L));
        when(web3j.ethGetLogs(filterCaptor.capture())).thenReturn(logRequest);
        when(logRequest.send()).thenReturn(emptyLogs, emptyLogs, matchingLogs);
        when(emptyLogs.getLogs()).thenReturn(List.of());
        when(matchingLogs.getLogs()).thenReturn(List.of(matchingLog));
        EvmProofAnchorAdapter adapter = adapter(web3j);

        assertThat(adapter.findAnchorTransaction(new byte[32]))
                .contains("0xtransaction");
        verify(web3j, times(3)).ethGetLogs(any(EthFilter.class));
        assertThat(filterCaptor.getAllValues())
                .extracting(
                        filter -> ((DefaultBlockParameterNumber) filter.getFromBlock())
                                .getBlockNumber(),
                        filter -> ((DefaultBlockParameterNumber) filter.getToBlock())
                                .getBlockNumber()
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                BigInteger.valueOf(15L),
                                BigInteger.valueOf(24L)
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                BigInteger.valueOf(5L),
                                BigInteger.valueOf(14L)
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                BigInteger.ZERO,
                                BigInteger.valueOf(4L)
                        )
                );
    }

    private static EvmProofAnchorAdapter adapter(Web3j web3j) {
        EvmProofProperties properties = new EvmProofProperties(
                URI.create("http://localhost:8545"),
                BigInteger.valueOf(31_337L),
                "0x0000000000000000000000000000000000000001",
                BigInteger.ZERO,
                1,
                1,
                Duration.ZERO
        );
        return new EvmProofAnchorAdapter(
                web3j,
                Credentials.create("1".repeat(64)),
                properties,
                Clock.systemUTC()
        );
    }
}
