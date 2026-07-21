package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.domain.OutboxEventStatus;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxOperationExposureIntegrationTest {
    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "id",
            "outbox_event_id",
            "proof_anchor_id",
            "operator_id",
            "reason",
            "previous_status",
            "new_status",
            "previous_attempt_count",
            "reprocess_number",
            "requested_at"
    );

    private final RequestMappingHandlerMapping handlerMapping;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxOperationExposureIntegrationTest(
            @Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping,
            MeterRegistry meterRegistry,
            JdbcTemplate jdbcTemplate
    ) {
        this.handlerMapping = handlerMapping;
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("재처리와 감사 기능은 사용자 HTTP API로 노출되지 않는다")
    void keepsOperationsOutsidePublicApi() {
        Set<String> apiPaths = handlerMapping.getHandlerMethods()
                .keySet()
                .stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .filter(path -> path.startsWith("/api/"))
                .collect(Collectors.toSet());

        assertThat(apiPaths)
                .noneMatch(path -> path.contains("reprocess"))
                .noneMatch(path -> path.contains("dead-letter"))
                .noneMatch(path -> path.contains("manual-review"))
                .noneMatch(path -> path.contains("outbox"));
    }

    @Test
    @DisplayName("감사 테이블에는 payload와 고객 식별자 column이 없다")
    void excludesPayloadAndCustomerDataFromAuditSchema() {
        Set<String> columns = Set.copyOf(jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_reprocess_audit'
                """,
                String.class
        ));

        assertThat(columns).isEqualTo(AUDIT_COLUMNS);
    }

    @Test
    @DisplayName("worker metric은 고정 상태 외 동적 식별자를 tag로 사용하지 않는다")
    void limitsWorkerMetricTagsToStatus() {
        Set<String> allowedStatusTags = Arrays
                .stream(OutboxEventStatus.values())
                .map(status -> status.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Meter> outboxMeters = meterRegistry.getMeters()
                .stream()
                .filter(meter -> meter.getId().getName().startsWith(
                        "naesan.proof.outbox"
                ))
                .toList();

        assertThat(outboxMeters).isNotEmpty();
        assertThat(outboxMeters)
                .allSatisfy(meter -> meter.getId().getTags().forEach(tag -> {
                    assertThat(tag.getKey()).isEqualTo("status");
                    assertThat(tag.getValue()).isIn(allowedStatusTags);
                }));
    }
}
