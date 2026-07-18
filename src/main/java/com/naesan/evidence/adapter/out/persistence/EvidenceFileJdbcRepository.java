package com.naesan.evidence.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.evidence.application.EvidenceException;
import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.StorageKey;

@Repository
public class EvidenceFileJdbcRepository implements EvidenceFileRepository {
    private static final String INSERT_FILE = """
            INSERT INTO evidence_files (
                id,
                evidence_id,
                object_key,
                sha256,
                media_type,
                size_bytes,
                state,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_EVIDENCE_ID = """
            SELECT
                id,
                evidence_id,
                object_key,
                sha256,
                media_type,
                size_bytes,
                state,
                created_at,
                updated_at
            FROM evidence_files
            WHERE evidence_id = ?
            """;
    private static final String UPDATE_FILE = """
            UPDATE evidence_files
            SET object_key = ?, state = ?, updated_at = ?
            WHERE id = ? AND state = 'TEMPORARY'
            """;

    private final JdbcTemplate jdbcTemplate;

    public EvidenceFileJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(EvidenceFile evidenceFile) {
        jdbcTemplate.update(
                INSERT_FILE,
                evidenceFile.id(),
                evidenceFile.evidenceId(),
                evidenceFile.objectKey().value(),
                evidenceFile.sha256(),
                evidenceFile.fileType().mediaType(),
                evidenceFile.size(),
                evidenceFile.state().name(),
                evidenceFile.createdAt().atOffset(ZoneOffset.UTC),
                evidenceFile.updatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public void update(EvidenceFile evidenceFile) {
        int updatedRowCount = jdbcTemplate.update(
                UPDATE_FILE,
                evidenceFile.objectKey().value(),
                evidenceFile.state().name(),
                evidenceFile.updatedAt().atOffset(ZoneOffset.UTC),
                evidenceFile.id()
        );
        if (updatedRowCount == 0) {
            throw EvidenceException.concurrentModification();
        }
    }

    @Override
    public Optional<EvidenceFile> findByEvidenceId(UUID evidenceId) {
        return jdbcTemplate.query(
                        FIND_BY_EVIDENCE_ID,
                        this::mapEvidenceFile,
                        evidenceId
                )
                .stream()
                .findFirst();
    }

    private EvidenceFile mapEvidenceFile(ResultSet resultSet, int rowNumber) throws SQLException {
        return EvidenceFile.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("evidence_id", UUID.class),
                new StorageKey(resultSet.getString("object_key")),
                resultSet.getString("sha256"),
                fileType(resultSet.getString("media_type")),
                resultSet.getLong("size_bytes"),
                EvidenceFileState.valueOf(resultSet.getString("state")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private EvidenceFileType fileType(String mediaType) {
        return EvidenceFileType.findByMediaType(mediaType)
                .orElseThrow(() -> new IllegalStateException(
                        "저장된 Evidence 파일 형식을 복원할 수 없습니다."
                ));
    }
}
