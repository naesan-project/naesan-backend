package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.PassportStatus;

@Repository
public class PassportJdbcRepository implements PassportRepository {
    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                snapshot_id,
                current_holder_account_id,
                status,
                version,
                created_at
            FROM passports
            """;
    private static final String INSERT_PASSPORT = """
            INSERT INTO passports (
                id,
                snapshot_id,
                current_holder_account_id,
                status,
                version,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID = SELECT_COLUMNS + " WHERE id = ?";
    private static final String FIND_BY_SNAPSHOT_ID =
            SELECT_COLUMNS + " WHERE snapshot_id = ?";
    private static final String FIND_ALL_BY_CURRENT_HOLDER = SELECT_COLUMNS + """
             WHERE current_holder_account_id = ?
             ORDER BY created_at DESC, id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public PassportJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Passport passport) {
        jdbcTemplate.update(
                INSERT_PASSPORT,
                passport.id(),
                passport.snapshotId(),
                passport.currentHolderAccountId(),
                passport.status().name(),
                passport.version(),
                passport.createdAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public Optional<Passport> findById(UUID passportId) {
        return findOne(FIND_BY_ID, passportId);
    }

    private Optional<Passport> findOne(String sql, UUID id) {
        return jdbcTemplate.query(sql, this::mapPassport, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Passport> findBySnapshotId(UUID snapshotId) {
        return findOne(FIND_BY_SNAPSHOT_ID, snapshotId);
    }

    @Override
    public List<Passport> findAllByCurrentHolderAccountId(UUID accountId) {
        return jdbcTemplate.query(
                FIND_ALL_BY_CURRENT_HOLDER,
                this::mapPassport,
                accountId
        );
    }

    private Passport mapPassport(ResultSet resultSet, int rowNumber) throws SQLException {
        return Passport.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("snapshot_id", UUID.class),
                resultSet.getObject("current_holder_account_id", UUID.class),
                PassportStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
