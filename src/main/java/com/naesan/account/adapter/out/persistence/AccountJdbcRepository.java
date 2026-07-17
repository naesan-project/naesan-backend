package com.naesan.account.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.account.application.AccountException;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.AccountStatus;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;

@Repository
public class AccountJdbcRepository implements AccountRepository {
    private static final String EXISTS_BY_EMAIL = """
            SELECT EXISTS (
                SELECT 1
                FROM accounts
                WHERE email = ?
            )
            """;
    private static final String FIND_BY_EMAIL = """
            SELECT id, email, password_hash, status, created_at
            FROM accounts
            WHERE email = ?
            """;
    private static final String INSERT_ACCOUNT = """
            INSERT INTO accounts (id, email, password_hash, status, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT accounts_email_unique DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public AccountJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByEmail(Email email) {
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_BY_EMAIL, Boolean.class, email.value());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
        return jdbcTemplate.query(FIND_BY_EMAIL, this::mapAccount, email.value())
                .stream()
                .findFirst();
    }

    private Account mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return Account.restore(
                resultSet.getObject("id", UUID.class),
                new Email(resultSet.getString("email")),
                new PasswordHash(resultSet.getString("password_hash")),
                AccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    @Override
    public void save(Account account) {
        int insertedRowCount = jdbcTemplate.update(
                INSERT_ACCOUNT,
                account.id(),
                account.email().value(),
                account.passwordHash().value(),
                account.status().name(),
                account.createdAt().atOffset(ZoneOffset.UTC)
        );

        if (insertedRowCount == 0) {
            throw AccountException.emailAlreadyRegistered();
        }
    }
}
