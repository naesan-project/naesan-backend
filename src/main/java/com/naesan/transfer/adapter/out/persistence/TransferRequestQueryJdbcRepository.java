package com.naesan.transfer.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.transfer.application.TransferRequestDetails;
import com.naesan.transfer.application.port.out.TransferRequestQueryRepository;
import com.naesan.transfer.domain.TransferRequest;
import com.naesan.transfer.domain.TransferRequestStatus;

@Repository
public class TransferRequestQueryJdbcRepository implements TransferRequestQueryRepository {
    private static final String SELECT_DETAILS = """
            SELECT
                transfer_request.id,
                transfer_request.passport_id,
                transfer_request.requester_account_id,
                transfer_request.recipient_account_id,
                transfer_request.status,
                transfer_request.version,
                transfer_request.expires_at,
                transfer_request.created_at,
                transfer_request.updated_at,
                requester.email AS requester_email,
                recipient.email AS recipient_email
            FROM transfer_requests transfer_request
            JOIN accounts requester
                ON requester.id = transfer_request.requester_account_id
            JOIN accounts recipient
                ON recipient.id = transfer_request.recipient_account_id
            """;
    private static final String FIND_ALL_OUTGOING = SELECT_DETAILS + """
             WHERE transfer_request.requester_account_id = ?
             ORDER BY transfer_request.created_at DESC, transfer_request.id DESC
            """;
    private static final String FIND_ALL_INCOMING = SELECT_DETAILS + """
             WHERE transfer_request.recipient_account_id = ?
             ORDER BY transfer_request.created_at DESC, transfer_request.id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public TransferRequestQueryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TransferRequestDetails> findAllOutgoing(UUID accountId) {
        return jdbcTemplate.query(FIND_ALL_OUTGOING, this::mapDetails, accountId);
    }

    @Override
    public List<TransferRequestDetails> findAllIncoming(UUID accountId) {
        return jdbcTemplate.query(FIND_ALL_INCOMING, this::mapDetails, accountId);
    }

    private TransferRequestDetails mapDetails(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        TransferRequest request = TransferRequest.restore(
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
        return new TransferRequestDetails(
                request,
                resultSet.getString("requester_email"),
                resultSet.getString("recipient_email")
        );
    }
}
