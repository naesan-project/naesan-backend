package com.naesan.passport.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReprocessOutboxEventCommandTest {

    @Test
    @DisplayName("운영자와 사유가 있는 내부 재처리 명령만 허용한다")
    void requiresOperatorAndReason() {
        assertThatThrownBy(() -> new ReprocessOutboxEventCommand(
                UUID.randomUUID(),
                " ",
                "provider 장애 복구"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReprocessOutboxEventCommand(
                UUID.randomUUID(),
                "operator-1",
                " "
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
