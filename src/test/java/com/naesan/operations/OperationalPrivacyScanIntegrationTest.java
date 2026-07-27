package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.domain.StorageKey;
import com.naesan.share.application.IssuedPublicShare;
import com.naesan.share.application.ManagePublicShareService;
import com.naesan.share.domain.PublicShareCapability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OperationalPrivacyScanIntegrationTest {
    private static final String EMAIL = "privacy-scan@example.com";
    private static final String RAW_PASSWORD = "privacy-canary-password";
    private static final String FILE_CONTENT = "private-file-canary-content";
    private static final String SNAPSHOT_DIGEST = "d".repeat(64);
    private static final String PROOF_PAYLOAD = "proof-payload-canary";
    private static final byte[] CANONICAL_PAYLOAD =
            ("{\"private\":\"" + PROOF_PAYLOAD + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-27T00:00:00Z");
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final FileStorage fileStorage;
    private final ManagePublicShareService managePublicShareService;

    @Autowired
    OperationalPrivacyScanIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            FileStorage fileStorage,
            ManagePublicShareService managePublicShareService
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorage = fileStorage;
        this.managePublicShareService = managePublicShareService;
    }

    @Test
    @DisplayName("운영 log와 DB와 object key에 raw secret과 고객 정보가 누출되지 않는다")
    void findsNoPrivacyLeakageAcrossOperationalSurfaces() throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        StorageKey storedFile = null;

        try {
            registerAccount();
            UUID accountId = accountId();
            UUID passportId = preparePassport(accountId);
            IssuedPublicShare share = managePublicShareService.issue(
                    accountId,
                    passportId,
                    PublicShareCapability.SUMMARY
            );
            storedFile = fileStorage.storeTemporary(new ByteArrayInputStream(
                    FILE_CONTENT.getBytes(StandardCharsets.UTF_8)
            ));

            assertLogsDoNotContainPrivateValues(appender.list, share.rawToken());
            assertDatabaseDoesNotContainRawValues(share.rawToken());
            assertObjectKeysDoNotContainPrivateValues(
                    accountId,
                    share.rawToken()
            );
        } finally {
            if (storedFile != null) {
                fileStorage.delete(storedFile);
            }
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private void registerAccount() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, RAW_PASSWORD)))
                .andExpect(status().isCreated());
    }

    private UUID accountId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE email = ?",
                UUID.class,
                EMAIL
        );
    }

    private UUID preparePassport(UUID accountId) {
        UUID evidenceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID passportId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name,
                    purchased_at, amount, currency, state,
                    created_at, updated_at, confirmed_at
                )
                VALUES (?, ?, 'Privacy Merchant', 'Privacy Product',
                        ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?)
                """,
                evidenceId,
                accountId,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO evidence_snapshots (
                    id, evidence_id, schema_version, canonical_payload,
                    snapshot_digest, created_at
                )
                VALUES (?, ?, 1, ?, ?, ?)
                """,
                snapshotId,
                evidenceId,
                CANONICAL_PAYLOAD,
                SNAPSHOT_DIGEST,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO passports (
                    id, snapshot_id, current_holder_account_id,
                    status, version, created_at
                )
                VALUES (?, ?, ?, 'ACTIVE', 0, ?)
                """,
                passportId,
                snapshotId,
                accountId,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        return passportId;
    }

    private void assertLogsDoNotContainPrivateValues(
            List<ILoggingEvent> events,
            String rawToken
    ) {
        String logs = events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(logs)
                .doesNotContain(EMAIL)
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain(rawToken)
                .doesNotContain(FILE_CONTENT)
                .doesNotContain(SNAPSHOT_DIGEST)
                .doesNotContain(PROOF_PAYLOAD)
                .doesNotContain(HexFormat.of().formatHex(CANONICAL_PAYLOAD));
    }

    private void assertDatabaseDoesNotContainRawValues(String rawToken) {
        DatabasePrivacyScanner scanner =
                new DatabasePrivacyScanner(jdbcTemplate);

        assertThat(scanner.findRawValue(RAW_PASSWORD)).isEmpty();
        assertThat(scanner.findRawValue(rawToken)).isEmpty();
        assertThat(scanner.findRawValue(FILE_CONTENT)).isEmpty();
    }

    private void assertObjectKeysDoNotContainPrivateValues(
            UUID accountId,
            String rawToken
    ) {
        List<String> objectKeys = fileStorage.listTemporaryObjects()
                .stream()
                .map(metadata -> metadata.key().value())
                .toList();
        assertThat(objectKeys)
                .isNotEmpty()
                .allSatisfy(key -> assertThat(key)
                        .doesNotContain(EMAIL)
                        .doesNotContain(accountId.toString())
                        .doesNotContain(rawToken)
                        .doesNotContain(FILE_CONTENT)
                        .doesNotContain(SNAPSHOT_DIGEST));
    }
}
