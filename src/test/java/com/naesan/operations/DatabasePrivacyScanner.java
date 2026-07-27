package com.naesan.operations;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

final class DatabasePrivacyScanner {
    private static final String PRIVACY_COLUMNS = """
            SELECT table_name, column_name, udt_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND udt_name IN ('varchar', 'text', 'json', 'jsonb', 'bytea')
            ORDER BY table_name, ordinal_position
            """;
    private final JdbcTemplate jdbcTemplate;

    DatabasePrivacyScanner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<String> findRawValue(String rawValue) {
        return privacyColumns()
                .stream()
                .filter(column -> contains(column, rawValue))
                .map(DatabaseColumn::location)
                .toList();
    }

    private List<DatabaseColumn> privacyColumns() {
        return jdbcTemplate.query(
                PRIVACY_COLUMNS,
                (resultSet, rowNumber) -> new DatabaseColumn(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("udt_name").equals("bytea")
                )
        );
    }

    private boolean contains(DatabaseColumn column, String rawValue) {
        String columnReference = quoted(column.name());
        String valueExpression = column.binary()
                ? "encode(" + columnReference + ", 'hex')"
                : "CAST(" + columnReference + " AS text)";
        String expectedValue = column.binary()
                ? HexFormat.of().formatHex(
                        rawValue.getBytes(StandardCharsets.UTF_8)
                )
                : rawValue;
        Boolean found = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM %s
                    WHERE POSITION(? IN %s) > 0
                )
                """.formatted(quoted(column.table()), valueExpression),
                Boolean.class,
                expectedValue
        );
        return Boolean.TRUE.equals(found);
    }

    private String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record DatabaseColumn(
            String table,
            String name,
            boolean binary
    ) {

        String location() {
            return table + "." + name;
        }
    }
}
