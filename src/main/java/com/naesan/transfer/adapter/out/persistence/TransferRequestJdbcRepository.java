package com.naesan.transfer.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.transfer.application.port.out.TransferRequestRepository;
import com.naesan.transfer.domain.TransferRequest;
import com.naesan.transfer.domain.TransferRequestStatus;

@Repository
public class TransferRequestJdbcRepository implements TransferRequestRepository {
    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                passport_id,
                requester_account_id,
                recipient_account_id,
                status,
                version,
                expires_at,
                created_at,
                updated_at
            FROM transfer_requests
            """;
    private static final String INSERT_REQUEST = """
            INSERT INTO transfer_requests (
                id,
                passport_id,
                requester_account_id,
                recipient_account_id,
                status,
                version,
                expires_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_REQUEST = """
            UPDATE transfer_requests
            SET status = ?, version = ?, updated_at = ?
            WHERE id = ?
            """;
    private static final String FIND_BY_ID_FOR_UPDATE =
            SELECT_COLUMNS + " WHERE id = ? FOR UPDATE";
    private static final String FIND_PENDING_BY_PASSPORT_ID = SELECT_COLUMNS + """
             WHERE passport_id = ? AND status = 'PENDING'
            """;
    private final JdbcTemplate jdbcTemplate;

    public TransferRequestJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(TransferRequest request) {
        jdbcTemplate.update(
                INSERT_REQUEST,
                request.id(),
                request.passportId(),
                request.requesterAccountId(),
                request.recipientAccountId(),
                request.status().name(),
                request.version(),
                request.expiresAt().atOffset(ZoneOffset.UTC),
                request.createdAt().atOffset(ZoneOffset.UTC),
                request.updatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public void update(TransferRequest request) {
        jdbcTemplate.update(
                UPDATE_REQUEST,
                request.status().name(),
                request.version(),
                request.updatedAt().atOffset(ZoneOffset.UTC),
                request.id()
        );
    }

    @Override
    public Optional<TransferRequest> findByIdForUpdate(UUID requestId) {
        return findOne(FIND_BY_ID_FOR_UPDATE, requestId);
    }

    @Override
    public Optional<TransferRequest> findPendingByPassportId(UUID passportId) {
        return findOne(FIND_PENDING_BY_PASSPORT_ID, passportId);
    }

    private Optional<TransferRequest> findOne(String sql, UUID id) {
        return jdbcTemplate.query(sql, this::mapRequest, id)
                .stream()
                .findFirst();
    }

    private TransferRequest mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return TransferRequest.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("passport_id", UUID.class),
                resultSet.getObject("requester_account_id", UUID.class),
                resultSet.getObject("recipient_account_id", UUID.class),
                TransferRequestStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
