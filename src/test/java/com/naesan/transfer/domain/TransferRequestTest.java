package com.naesan.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferRequestTest {
    private static final UUID REQUESTER_ACCOUNT_ID =
            UUID.fromString("f8a69c29-f928-41d4-92cb-101baf9ba850");
    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("7de2d7f1-659c-4487-b57b-2eac9d056688");
    private static final Instant CREATED_AT = Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plus(7, ChronoUnit.DAYS);

    @Test
    @DisplayName("서로 다른 요청자와 수신자로 대기 중인 이전 요청을 만든다")
    void createsPendingRequest() {
        TransferRequest request = createRequest();

        assertThat(request.status()).isEqualTo(TransferRequestStatus.PENDING);
        assertThat(request.version()).isZero();
        assertThat(request.isPendingAt(EXPIRES_AT.minusMillis(1))).isTrue();
        assertThat(request.isPendingAt(EXPIRES_AT)).isFalse();
        assertThat(request.statusAt(EXPIRES_AT))
                .isEqualTo(TransferRequestStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료 시각부터 대기 요청을 EXPIRED로 전이한다")
    void expiresDueRequest() {
        TransferRequest request = createRequest();

        assertThat(request.expireIfDue(EXPIRES_AT.minusMillis(1)))
                .isSameAs(request);
        TransferRequest expiredRequest = request.expireIfDue(EXPIRES_AT);

        assertThat(expiredRequest.status()).isEqualTo(TransferRequestStatus.EXPIRED);
        assertThat(expiredRequest.version()).isOne();
    }

    @Test
    @DisplayName("요청자는 만료 전에 요청을 취소할 수 있다")
    void cancelsByRequester() {
        TransferRequest cancelledRequest = createRequest().cancelBy(
                REQUESTER_ACCOUNT_ID,
                CREATED_AT.plus(1, ChronoUnit.DAYS)
        );

        assertThat(cancelledRequest.status())
                .isEqualTo(TransferRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("수신자는 만료 전에 요청을 거절할 수 있다")
    void rejectsByRecipient() {
        TransferRequest rejectedRequest = createRequest().rejectBy(
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT.plus(1, ChronoUnit.DAYS)
        );

        assertThat(rejectedRequest.status())
                .isEqualTo(TransferRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("수신자는 만료 전에 요청을 수락할 수 있다")
    void acceptsByRecipient() {
        TransferRequest acceptedRequest = createRequest().acceptBy(
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT.plus(1, ChronoUnit.DAYS)
        );

        assertThat(acceptedRequest.status())
                .isEqualTo(TransferRequestStatus.ACCEPTED);
        assertThat(acceptedRequest.version()).isOne();
    }

    @Test
    @DisplayName("권한이 없거나 terminal·만료 요청이면 취소와 거절을 거부한다")
    void rejectsInvalidTransition() {
        TransferRequest request = createRequest();

        assertThatThrownBy(() -> request.cancelBy(
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.rejectBy(
                REQUESTER_ACCOUNT_ID,
                CREATED_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.acceptBy(
                REQUESTER_ACCOUNT_ID,
                CREATED_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.cancelBy(
                REQUESTER_ACCOUNT_ID,
                EXPIRES_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.acceptBy(
                RECIPIENT_ACCOUNT_ID,
                EXPIRES_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.cancelBy(
                REQUESTER_ACCOUNT_ID,
                CREATED_AT
        ).rejectBy(
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT.plusMillis(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("자기 자신에게 이전하거나 유효하지 않은 시간과 version은 거부한다")
    void rejectsInvalidRequest() {
        assertThatThrownBy(() -> TransferRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                REQUESTER_ACCOUNT_ID,
                REQUESTER_ACCOUNT_ID,
                EXPIRES_AT,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransferRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                REQUESTER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private TransferRequest createRequest() {
        return TransferRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                REQUESTER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                EXPIRES_AT,
                CREATED_AT
        );
    }
}
