package org.bxwbb.qcpet.utils.saveUtil;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MySqlSaveUtil implements AutoCloseable {

    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L;

    private final HikariDataSource dataSource;
    private final String petTableName;
    private final String playerPetTableName;
    private final String sequenceTableName;

    public MySqlSaveUtil(String host, int port, String database, String username, String password, String tableName) {
        this(buildJdbcUrl(host, port, database), username, password, tableName);
    }

    public MySqlSaveUtil(String jdbcUrl, String username, String password, String tableName) {
        this(jdbcUrl, username, password, tableName, DEFAULT_MAXIMUM_POOL_SIZE);
    }

    public MySqlSaveUtil(String jdbcUrl, String username, String password, String tableName, int maximumPoolSize) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl cannot be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be greater than 0");
        }
        validateTableName(tableName);
        this.petTableName = tableName;
        this.playerPetTableName = tableName + "_players";
        this.sequenceTableName = tableName + "_sequence";
        this.dataSource = createDataSource(jdbcUrl, username, password, maximumPoolSize);
        ensureTables();
    }

    public long nextPetId() {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO `%s` (`name`, `value`)
                        SELECT 'pet_id', COALESCE(MAX(`id`), 0) FROM `%s`
                        ON DUPLICATE KEY UPDATE `value` = `value`
                        """.formatted(sequenceTableName, petTableName));
                statement.executeUpdate("""
                        UPDATE `%s`
                        SET `value` = LAST_INSERT_ID(`value` + 1)
                        WHERE `name` = 'pet_id'
                        """.formatted(sequenceTableName));
                try (ResultSet resultSet = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
                    if (!resultSet.next()) {
                        throw new SaveException("Failed to allocate pet id");
                    }
                    long petId = resultSet.getLong(1);
                    connection.commit();
                    return petId;
                }
            } catch (SQLException exception) {
                rollback(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SaveException("Failed to allocate pet id", exception);
        }
    }

    public Optional<PetRecord> findPet(long petId) {
        validateId(petId, "petId");
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `id`, `name`, `type`, `level`, `exp`, `times`, `data`, `show`
                     FROM `%s`
                     WHERE `id` = ?
                     """.formatted(petTableName))) {
            statement.setLong(1, petId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toPetRecord(resultSet));
            }
        } catch (SQLException exception) {
            throw new SaveException("Failed to find pet: " + petId, exception);
        }
    }

    public List<PetRecord> findPetsByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        List<PetRecord> pets = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.`id`, p.`name`, p.`type`, p.`level`, p.`exp`, p.`times`, p.`data`, p.`show`
                     FROM `%s` pp
                     INNER JOIN `%s` p ON p.`id` = pp.`pet_id`
                     WHERE pp.`player_uuid` = ?
                     ORDER BY pp.`created_at` ASC, p.`id` ASC
                     """.formatted(playerPetTableName, petTableName))) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pets.add(toPetRecord(resultSet));
                }
            }
            return pets;
        } catch (SQLException exception) {
            throw new SaveException("Failed to find pets by player: " + playerUuid, exception);
        }
    }

    public void savePet(PetRecord pet) {
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        validateId(pet.id(), "pet.id");
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO `%s` (`id`, `name`, `type`, `level`, `exp`, `times`, `data`, `show`)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         `name` = VALUES(`name`),
                         `type` = VALUES(`type`),
                         `level` = VALUES(`level`),
                         `exp` = VALUES(`exp`),
                         `times` = VALUES(`times`),
                         `data` = VALUES(`data`),
                         `show` = VALUES(`show`),
                         `updated_at` = CURRENT_TIMESTAMP
                     """.formatted(petTableName))) {
            statement.setLong(1, pet.id());
            statement.setString(2, pet.name());
            statement.setString(3, pet.type());
            statement.setInt(4, pet.level());
            statement.setInt(5, pet.exp());
            statement.setDouble(6, pet.times());
            statement.setString(7, pet.data());
            statement.setBoolean(8, pet.show());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new SaveException("Failed to save pet: " + pet.id(), exception);
        }
    }

    public void bindPetToPlayer(UUID playerUuid, long petId) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        validateId(petId, "petId");
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO `%s` (`player_uuid`, `pet_id`)
                     VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP
                     """.formatted(playerPetTableName))) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, petId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new SaveException("Failed to bind pet to player: " + petId, exception);
        }
    }

    public boolean deletePet(UUID playerUuid, long petId) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        validateId(petId, "petId");
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int unbound;
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM `%s`
                        WHERE `player_uuid` = ? AND `pet_id` = ?
                        """.formatted(playerPetTableName))) {
                    statement.setString(1, playerUuid.toString());
                    statement.setLong(2, petId);
                    unbound = statement.executeUpdate();
                }
                if (unbound == 0) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM `%s`
                        WHERE `id` = ?
                        """.formatted(petTableName))) {
                    statement.setLong(1, petId);
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollback(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SaveException("Failed to delete pet: " + petId, exception);
        }
    }

    public boolean isAvailable() {
        try (Connection connection = getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private void ensureTables() {
        ensureSequenceTable();
        ensurePetTable();
        ensurePlayerPetTable();
    }

    private void ensureSequenceTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `%s` (
                        `name` VARCHAR(64) NOT NULL,
                        `value` BIGINT NOT NULL,
                        `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.formatted(sequenceTableName));
        } catch (SQLException exception) {
            throw new SaveException("Failed to initialize MySQL sequence table: " + sequenceTableName, exception);
        }
    }

    private void ensurePetTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `%s` (
                        `id` BIGINT NOT NULL,
                        `name` VARCHAR(64) NOT NULL,
                        `type` VARCHAR(64) NOT NULL,
                        `level` INT NOT NULL DEFAULT 1,
                        `exp` INT NOT NULL DEFAULT 0,
                        `times` DOUBLE NOT NULL DEFAULT 0,
                        `data` JSON NOT NULL,
                        `show` BOOLEAN NOT NULL DEFAULT TRUE,
                        `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        KEY `idx_type` (`type`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.formatted(petTableName));
            ensurePetDataColumn(connection, statement);
            addColumnIfMissing(connection, statement, petTableName, "show", "`show` BOOLEAN NOT NULL DEFAULT TRUE");
        } catch (SQLException exception) {
            throw new SaveException("Failed to initialize MySQL pet table: " + petTableName, exception);
        }
    }

    private void ensurePetDataColumn(Connection connection, Statement statement) throws SQLException {
        if (hasColumn(connection, petTableName, "data")) {
            return;
        }
        statement.executeUpdate("""
                ALTER TABLE `%s`
                ADD COLUMN `data` JSON NULL
                """.formatted(petTableName));
        statement.executeUpdate("""
                UPDATE `%s`
                SET `data` = '{}'
                WHERE `data` IS NULL
                """.formatted(petTableName));
        statement.executeUpdate("""
                ALTER TABLE `%s`
                MODIFY COLUMN `data` JSON NOT NULL
                """.formatted(petTableName));
    }

    private static void addColumnIfMissing(Connection connection, Statement statement, String tableName, String columnName, String definition) throws SQLException {
        if (!hasColumn(connection, tableName, columnName)) {
            statement.executeUpdate("ALTER TABLE `%s` ADD COLUMN %s".formatted(tableName, definition));
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
    }

    private void ensurePlayerPetTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `%s` (
                        `player_uuid` CHAR(36) NOT NULL,
                        `pet_id` BIGINT NOT NULL,
                        `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`player_uuid`, `pet_id`),
                        KEY `idx_pet_id` (`pet_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.formatted(playerPetTableName));
        } catch (SQLException exception) {
            throw new SaveException("Failed to initialize MySQL player pet table: " + playerPetTableName, exception);
        }
    }

    private Connection getConnection() throws SQLException {
        ensureOpen();
        return dataSource.getConnection();
    }

    private void ensureOpen() {
        if (dataSource.isClosed()) {
            throw new SaveException("MySQL storage has been closed");
        }
    }

    private static PetRecord toPetRecord(ResultSet resultSet) throws SQLException {
        return new PetRecord(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("type"),
                resultSet.getInt("level"),
                resultSet.getInt("exp"),
                resultSet.getDouble("times"),
                resultSet.getString("data"),
                resultSet.getBoolean("show")
        );
    }

    private static HikariDataSource createDataSource(String jdbcUrl, String username, String password, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password == null ? "" : password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(1);
        config.setPoolName("QcPet-MySQL");
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT_MS);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    private static String buildJdbcUrl(String host, int port, String database) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database cannot be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port is out of range");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";
    }

    private static void validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("tableName can only contain letters, numbers and underscores");
        }
    }

    private static void validateId(long id, String name) {
        if (id < 1) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    public record PetRecord(
            long id,
            String name,
            String type,
            int level,
            int exp,
            double times,
            String data,
            boolean show) {
    }
}
