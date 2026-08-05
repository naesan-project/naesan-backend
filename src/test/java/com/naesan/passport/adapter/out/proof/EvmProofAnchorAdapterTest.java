package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

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
