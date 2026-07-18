package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.domain.OwnershipChangeReason;
import com.naesan.passport.domain.OwnershipHistory;

@Repository
public class OwnershipHistoryJdbcRepository implements OwnershipHistoryRepository {
    private static final String INSERT_OWNERSHIP_HISTORY = """
            INSERT INTO ownership_history (
                id,
                passport_id,
                previous_holder_account_id,
                new_holder_account_id,
                reason,
                changed_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_ALL_BY_PASSPORT_ID = """
            SELECT
                id,
                passport_id,
                previous_holder_account_id,
                new_holder_account_id,
                reason,
                changed_at
            FROM ownership_history
            WHERE passport_id = ?
            ORDER BY changed_at, id
            """;

    private final JdbcTemplate jdbcTemplate;

    public OwnershipHistoryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(OwnershipHistory ownershipHistory) {
        jdbcTemplate.update(
                INSERT_OWNERSHIP_HISTORY,
                ownershipHistory.id(),
                ownershipHistory.passportId(),
                ownershipHistory.previousHolderAccountId(),
                ownershipHistory.newHolderAccountId(),
                ownershipHistory.reason().name(),
                ownershipHistory.changedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public List<OwnershipHistory> findAllByPassportId(UUID passportId) {
        return jdbcTemplate.query(
                FIND_ALL_BY_PASSPORT_ID,
                this::mapOwnershipHistory,
                passportId
        );
    }

    private OwnershipHistory mapOwnershipHistory(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return OwnershipHistory.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("passport_id", UUID.class),
                resultSet.getObject("previous_holder_account_id", UUID.class),
                resultSet.getObject("new_holder_account_id", UUID.class),
                OwnershipChangeReason.valueOf(resultSet.getString("reason")),
                resultSet.getObject("changed_at", OffsetDateTime.class).toInstant()
        );
    }
}
