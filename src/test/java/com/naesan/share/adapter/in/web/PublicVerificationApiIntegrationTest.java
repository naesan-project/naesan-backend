package com.naesan.share.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.HexFormat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.naesan.TestcontainersConfiguration;
import com.naesan.share.application.IssuedPublicShare;
import com.naesan.share.application.ManagePublicShareService;
import com.naesan.share.domain.PublicShareCapability;
import com.naesan.passport.domain.AnchorCommitmentCalculator;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        TestcontainersConfiguration.class,
        PublicVerificationApiIntegrationTest.FixedClockConfiguration.class
})
class PublicVerificationApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("54b94823-4f41-4f32-be33-bbb4b3e4808b");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("1a290460-a935-4082-86ea-6f6e1d812f08");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("eb3324c8-4d18-46d8-bfca-81d0026445b8");
    private static final UUID PASSPORT_ID =
            UUID.fromString("b6f6ff41-b757-46db-89a5-eac1e8e2f1bc");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CURRENT_TIME = Instant.parse("2026-07-21T00:00:00Z");
    private static final byte[] ORIGINAL_FILE =
            "%PDF-1.7\noriginal".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ANCHOR_SALT = filledBytes(0x11);
    private static final byte[] CANONICAL_PAYLOAD = canonicalPayload();
    private static final String SNAPSHOT_DIGEST = sha256Hex(CANONICAL_PAYLOAD);
    private static final byte[] COMMITMENT = new AnchorCommitmentCalculator()
            .calculate(SNAPSHOT_DIGEST, ANCHOR_SALT)
            .commitment();

    private final MockMvc mockMvc;
    private final ManagePublicShareService managePublicShareService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicVerificationApiIntegrationTest(
            MockMvc mockMvc,
            ManagePublicShareService managePublicShareService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.managePublicShareService = managePublicShareService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update("DELETE FROM public_shares");
        jdbcTemplate.update("DELETE FROM outbox_reprocess_audit");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertPassportGraph();
    }

    @Test
    @DisplayName("익명 SUMMARY 조회는 공개 allowlist와 no-store만 반환한다")
    void returnsSummaryAllowlist() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.SUMMARY);

        MvcResult result = mockMvc.perform(get("/api/public/passport-verification")
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                share.rawToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.capability").value("SUMMARY"))
                .andExpect(jsonPath("$.productName").value("생각등대"))
                .andExpect(jsonPath("$.purchasedAt").value("2026-07-01"))
                .andExpect(jsonPath("$.passportStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.trustStage").value("INTERNALLY_SEALED"))
                .andExpect(jsonPath("$.commitment")
                        .value(HexFormat.of().formatHex(COMMITMENT)))
                .andExpect(jsonPath("$.verificationMaterial").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(OWNER_ACCOUNT_ID.toString())
                .doesNotContain("public-owner@example.com")
                .doesNotContain("생각상점")
                .doesNotContain("SERIAL-PRIVATE")
                .doesNotContain("1000.00")
                .doesNotContain(SNAPSHOT_DIGEST)
                .doesNotContain("1".repeat(64));
    }

    @Test
    @DisplayName("익명 FILE_MATCH 조회만 commitment 재계산 material을 반환한다")
    void returnsFileMatchVerificationMaterial() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.FILE_MATCH);

        mockMvc.perform(get("/api/public/passport-verification")
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                share.rawToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("FILE_MATCH"))
                .andExpect(jsonPath("$.verificationMaterial.snapshotDigest")
                        .value(SNAPSHOT_DIGEST))
                .andExpect(jsonPath("$.verificationMaterial.anchorSalt")
                        .value("1".repeat(64)))
                .andExpect(jsonPath("$.verificationMaterial.commitment")
                        .value(HexFormat.of().formatHex(COMMITMENT)))
                .andExpect(jsonPath("$.verificationMaterial.snapshotSchemaVersion")
                        .value(1))
                .andExpect(jsonPath("$.verificationMaterial.commitmentSchemaVersion")
                        .value(1))
                .andExpect(jsonPath("$.verificationMaterial.domain")
                        .value("NAESAN_ANCHOR"))
                .andExpect(jsonPath("$.verificationMaterial.snapshotHashAlgorithm")
                        .value("SHA-256"))
                .andExpect(jsonPath("$.verificationMaterial.commitmentHashAlgorithm")
                        .value("KECCAK-256"))
                .andExpect(jsonPath("$.verificationMaterial.commitmentEncoding")
                        .value("ABI"));
    }

    @Test
    @DisplayName("unknown malformed expired revoked token은 같은 404와 no-store를 반환한다")
    void hidesTokenState() throws Exception {
        IssuedPublicShare revokedShare = issue(PublicShareCapability.SUMMARY);
        managePublicShareService.revoke(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                revokedShare.publicShare().id()
        );
        IssuedPublicShare expiredShare = issue(PublicShareCapability.SUMMARY);
        jdbcTemplate.update(
                """
                UPDATE public_shares
                SET created_at = ?, expires_at = ?
                WHERE id = ?
                """,
                CURRENT_TIME.minus(8, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                CURRENT_TIME.minus(1, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                expiredShare.publicShare().id()
        );

        MvcResult unknown = notFound("z".repeat(43));
        MvcResult malformed = notFound("malformed");
        MvcResult revoked = notFound(revokedShare.rawToken());
        MvcResult expired = notFound(expiredShare.rawToken());

        assertThat(malformed.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString())
                .isEqualTo(revoked.getResponse().getContentAsString())
                .isEqualTo(expired.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("FILE_MATCH token은 candidate file을 저장하지 않고 일치 여부만 반환한다")
    void matchesCandidateFile() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.FILE_MATCH);

        mockMvc.perform(multipart(
                                "/api/public/passport-verification/file-match"
                        )
                        .file(candidateFile(ORIGINAL_FILE))
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                share.rawToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.trustStage").value("INTERNALLY_SEALED"))
                .andExpect(jsonPath("$.commitment")
                        .value(HexFormat.of().formatHex(COMMITMENT)))
                .andExpect(jsonPath("$.candidateDigest").doesNotExist())
                .andExpect(jsonPath("$.snapshotDigest").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_files",
                Integer.class
        )).isZero();
    }

    @Test
    @DisplayName("SUMMARY token의 file match는 unknown token과 같은 404를 반환한다")
    void hidesCapabilityMismatch() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.SUMMARY);

        MvcResult mismatch = fileMatchNotFound(share.rawToken());
        MvcResult unknown = fileMatchNotFound("z".repeat(43));

        assertThat(mismatch.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("익명 조회는 source address별 분당 요청 수를 제한한다")
    void limitsAnonymousVerificationRequests() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.SUMMARY);

        for (int count = 0; count < 60; count++) {
            mockMvc.perform(get("/api/public/passport-verification")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.10");
                                return request;
                            })
                            .header(
                                    PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                    share.rawToken()
                            ))
                    .andExpect(status().isOk());
        }

        MvcResult limited = mockMvc.perform(get("/api/public/passport-verification")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                share.rawToken()
                        ))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("PUBLIC_RATE_LIMIT_EXCEEDED"))
                .andReturn();

        assertThat(limited.getResponse().getContentAsString())
                .doesNotContain(share.rawToken());
    }

    @Test
    @DisplayName("익명 file match는 조회보다 낮은 별도 요청 수를 적용한다")
    void limitsAnonymousFileMatchRequests() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.FILE_MATCH);

        for (int count = 0; count < 5; count++) {
            mockMvc.perform(multipart(
                                    "/api/public/passport-verification/file-match"
                            )
                            .file(candidateFile(ORIGINAL_FILE))
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.20");
                                return request;
                            })
                            .header(
                                    PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                    share.rawToken()
                            ))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(multipart("/api/public/passport-verification/file-match")
                        .file(candidateFile(ORIGINAL_FILE))
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.20");
                            return request;
                        })
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                share.rawToken()
                        ))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.code").value("PUBLIC_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("공개 검증 URI 응답과 application log에 token과 privacy 정보가 남지 않는다")
    void preventsPublicVerificationLeakage() throws Exception {
        IssuedPublicShare share = issue(PublicShareCapability.FILE_MATCH);
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);

        MvcResult result;
        try {
            result = mockMvc.perform(get("/api/public/passport-verification")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.30");
                                return request;
                            })
                            .header(
                                    PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                    share.rawToken()
                            ))
                    .andExpect(status().isOk())
                    .andReturn();
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        String loggedMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
        assertThat(result.getRequest().getRequestURI())
                .doesNotContain(share.rawToken());
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(share.rawToken())
                .doesNotContain(OWNER_ACCOUNT_ID.toString())
                .doesNotContain("public-owner@example.com")
                .doesNotContain("SERIAL-PRIVATE");
        assertThat(loggedMessages)
                .doesNotContain(share.rawToken())
                .doesNotContain(SNAPSHOT_DIGEST)
                .doesNotContain(HexFormat.of().formatHex(ANCHOR_SALT))
                .doesNotContain(OWNER_ACCOUNT_ID.toString())
                .doesNotContain("public-owner@example.com");
    }

    private IssuedPublicShare issue(PublicShareCapability capability) {
        return managePublicShareService.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                capability
        );
    }

    private MvcResult notFound(String rawToken) throws Exception {
        return mockMvc.perform(get("/api/public/passport-verification")
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                rawToken
                        ))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.code").value("PUBLIC_SHARE_NOT_FOUND"))
                .andReturn();
    }

    private MvcResult fileMatchNotFound(String rawToken) throws Exception {
        return mockMvc.perform(multipart(
                                "/api/public/passport-verification/file-match"
                        )
                        .file(candidateFile(ORIGINAL_FILE))
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                rawToken
                        ))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("PUBLIC_SHARE_NOT_FOUND"))
                .andReturn();
    }

    private MockMultipartFile candidateFile(byte[] content) {
        return new MockMultipartFile(
                "file",
                "candidate.pdf",
                "application/pdf",
                content
        );
    }

    private void insertPassportGraph() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'public-owner@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, serial_number,
                    purchased_at, amount, currency, state,
                    created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', '생각등대', 'SERIAL-PRIVATE',
                    ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?
                )
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CURRENT_TIME.atOffset(ZoneOffset.UTC),
                CURRENT_TIME.atOffset(ZoneOffset.UTC),
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO evidence_snapshots (
                    id, evidence_id, schema_version, canonical_payload,
                    snapshot_digest, created_at
                )
                VALUES (?, ?, 1, ?, ?, ?)
                """,
                SNAPSHOT_ID,
                EVIDENCE_ID,
                CANONICAL_PAYLOAD,
                SNAPSHOT_DIGEST,
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO passports (
                    id, snapshot_id, current_holder_account_id,
                    status, version, created_at
                )
                VALUES (?, ?, ?, 'ACTIVE', 0, ?)
                """,
                PASSPORT_ID,
                SNAPSHOT_ID,
                OWNER_ACCOUNT_ID,
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO proof_anchors (
                    id, passport_id, schema_version, anchor_salt,
                    commitment, state, created_at, updated_at
                )
                VALUES (?, ?, 1, ?, ?, 'PREPARED', ?, ?)
                """,
                UUID.randomUUID(),
                PASSPORT_ID,
                ANCHOR_SALT,
                COMMITMENT,
                CURRENT_TIME.atOffset(ZoneOffset.UTC),
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
    }

    private static byte[] filledBytes(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static byte[] canonicalPayload() {
        String fileDigest = sha256Hex(ORIGINAL_FILE);
        return ("""
                {"schemaVersion":1,"fileSha256":"%s","productName":"생각등대"}
                """.formatted(fileDigest).strip())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
        }
    }
}
