package org.bxwbb.qcpet.utils.saveUtil;

import com.xigua.database.api.DatabaseApi;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MySQL宠物数据持久化工具类
 * 基于HikariCP连接池实现，自动建表、序列自增ID、增删改查宠物数据
 * 实现AutoCloseable，使用完毕需关闭连接池释放资源
 */
public class MySqlSaveUtil implements AutoCloseable  {

    // 默认连接池最大连接数
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    // 获取连接超时时间，单位毫秒
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L;

    // Hikari数据库连接池数据源
    private final HikariDataSource dataSource;
    // 宠物主表名
    private final String petTableName;
    // 玩家-宠物关联中间表名
    private final String playerPetTableName;
    // 自增ID序列表名
    private final String sequenceTableName;

    /**
     * 构造器：通过host、端口、库名拼接JDBC地址初始化
     * 使用默认连接池大小10
     * @param host MySQL地址
     * @param port MySQL端口
     * @param database 数据库名
     * @param username 账号
     * @param password 密码
     * @param tableName 基础表名（会自动拼接后缀生成关联表、序列表）
     */
    public MySqlSaveUtil(String host, int port, String database, String username, String password, String tableName) {
        this(buildJdbcUrl(host, port, database), username, password, tableName);
    }

    /**
     * 构造器：传入完整JDBC链接初始化，使用默认连接池大小10
     * @param jdbcUrl 完整JDBC连接字符串
     * @param username 账号
     * @param password 密码
     * @param tableName 基础表名
     */
    public MySqlSaveUtil(String jdbcUrl, String username, String password, String tableName) {
        this(jdbcUrl, username, password, tableName, DEFAULT_MAXIMUM_POOL_SIZE);
    }

    /**
     * 完整构造器，自定义全部参数与连接池大小
     * @param jdbcUrl 完整JDBC连接地址
     * @param username 数据库账号
     * @param password 数据库密码
     * @param tableName 基础表名
     * @param maximumPoolSize 连接池最大连接数量
     */
    public MySqlSaveUtil(String jdbcUrl, String username, String password, String tableName, int maximumPoolSize) {
        // 参数合法性校验
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl cannot be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be greater than 0");
        }
        // 校验表名仅允许字母数字下划线，防止SQL注入
        validateTableName(tableName);

        // 生成三张数据表名称
        this.petTableName = tableName;
        this.playerPetTableName = tableName + "_players";
        this.sequenceTableName = tableName + "_sequence";

        // 创建Hikari连接池实例
        this.dataSource = createDataSource(jdbcUrl, username, password, maximumPoolSize);
        // 自动检测并创建缺失的数据表
        ensureTables();
    }

    /**
     * 获取下一个宠物唯一自增ID（事务保证原子性）
     * 从sequence表维护pet_id计数器，每次调用自增+1
     * @return 新宠物唯一ID
     * @throws SaveException 获取ID失败抛出
     */
    public long nextPetId() {
        try (Connection connection = getConnection()) {
            // 关闭自动提交，开启手动事务
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // 插入/更新序列记录：不存在则初始化最大值，存在则不修改
                statement.executeUpdate("""
                        INSERT INTO `%s` (`name`, `value`)
                        SELECT 'pet_id', COALESCE(MAX(`id`), 0) FROM `%s`
                        ON DUPLICATE KEY UPDATE `value` = `value`
                        """.formatted(sequenceTableName, petTableName));
                // 计数器+1，使用LAST_INSERT_ID获取新值
                statement.executeUpdate("""
                        UPDATE `%s`
                        SET `value` = LAST_INSERT_ID(`value` + 1)
                        WHERE `name` = 'pet_id'
                        """.formatted(sequenceTableName));
                // 查询生成的新ID
                try (ResultSet resultSet = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
                    if (!resultSet.next()) {
                        throw new SaveException("Failed to allocate pet id");
                    }
                    long petId = resultSet.getLong(1);
                    // 事务提交
                    connection.commit();
                    return petId;
                }
            } catch (SQLException exception) {
                // SQL异常回滚事务
                rollback(connection);
                throw exception;
            } finally {
                // 恢复自动提交模式
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SaveException("Failed to allocate pet id", exception);
        }
    }

    /**
     * 根据宠物ID查询单条宠物数据
     * @param petId 宠物唯一ID
     * @return Optional包裹宠物记录，空则代表无数据
     */
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
                // 结果集转换为实体记录
                return Optional.of(toPetRecord(resultSet));
            }
        } catch (SQLException exception) {
            throw new SaveException("Failed to find pet: " + petId, exception);
        }
    }

    /**
     * 根据玩家UUID查询该玩家拥有的全部宠物
     * 通过玩家宠物关联表联查宠物主表
     * @param playerUuid 玩家UUID
     * @return 玩家所有宠物列表
     */
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
                // 遍历所有关联宠物封装记录
                while (resultSet.next()) {
                    pets.add(toPetRecord(resultSet));
                }
            }
            return pets;
        } catch (SQLException exception) {
            throw new SaveException("Failed to find pets by player: " + playerUuid, exception);
        }
    }

    /**
     * 保存/更新宠物数据
     * ON DUPLICATE KEY：主键存在则更新全部字段，不存在则新增
     * @param pet 宠物数据记录实体
     */
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

    /**
     * 绑定宠物归属玩家
     * 玩家-宠物中间表新增关联，重复主键仅更新更新时间
     * @param playerUuid 玩家UUID
     * @param petId 宠物ID
     */
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

    /**
     * 删除玩家名下指定宠物（事务原子操作）
     * 1. 删除玩家宠物关联记录
     * 2. 删除宠物主表数据
     * 关联记录不存在直接回滚，不删除宠物
     * @param playerUuid 玩家UUID
     * @param petId 待删除宠物ID
     * @return true 删除成功；false 该玩家无此宠物关联
     */
    public boolean deletePet(UUID playerUuid, long petId) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        validateId(petId, "petId");
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int unbound;
                // 先删除玩家宠物关联
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM `%s`
                        WHERE `player_uuid` = ? AND `pet_id` = ?
                        """.formatted(playerPetTableName))) {
                    statement.setString(1, playerUuid.toString());
                    statement.setLong(2, petId);
                    unbound = statement.executeUpdate();
                }
                // 无关联数据，回滚直接返回false
                if (unbound == 0) {
                    connection.rollback();
                    return false;
                }
                // 删除宠物主表数据
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

    /**
     * 检测数据库连接池是否可用
     * @return true 连接正常；false 连接失败/池已关闭
     */
    public boolean isAvailable() {
        try (Connection connection = getConnection()) {
            // 2秒超时校验连接活性
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
    }

    /**
     * 实现AutoCloseable接口，关闭Hikari连接池
     * 程序卸载/插件禁用时必须调用，释放数据库资源
     */
    @Override
    public void close() {
        dataSource.close();
    }

    /**
     * 统一初始化全部数据表：序列表、宠物主表、玩家宠物关联表
     */
    private void ensureTables() {
        ensureSequenceTable();
        ensurePetTable();
        ensurePlayerPetTable();
    }

    /**
     * 创建/校验序列自增ID表
     */
    private void ensureSequenceTable() {
        if (hasDatabasePlugin()) {
//            DatabaseApi databaseApi = DatabaseApi.getInstance();
//            databaseApi.mysql().createTable(
//                    sequenceTableName,
//                    "宠物校验序列表",
//                    table -> table
//                            .varchar("name", 64, "名称")
//            );
        } else {
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
    }

    /**
     * 创建/校验宠物主表，兼容旧版本字段自动新增
     */
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
            // 兼容旧库补全data JSON字段
            ensurePetDataColumn(connection, statement);
            // 兼容旧库补全show显示开关字段
            addColumnIfMissing(connection, statement, petTableName, "show", "`show` BOOLEAN NOT NULL DEFAULT TRUE");
        } catch (SQLException exception) {
            throw new SaveException("Failed to initialize MySQL pet table: " + petTableName, exception);
        }
    }

    /**
     * 兼容旧数据库，自动补全JSON扩展字段data
     * @param connection 数据库连接
     * @param statement SQL执行器
     */
    private void ensurePetDataColumn(Connection connection, Statement statement) throws SQLException {
        if (hasColumn(connection, petTableName, "data")) {
            return;
        }
        // 新增允许为空的JSON字段
        statement.executeUpdate("""
                ALTER TABLE `%s`
                ADD COLUMN `data` JSON NULL
                """.formatted(petTableName));
        // 空数据填充默认空JSON
        statement.executeUpdate("""
                UPDATE `%s`
                SET `data` = '{}'
                WHERE `data` IS NULL
                """.formatted(petTableName));
        // 修改字段为NOT NULL约束
        statement.executeUpdate("""
                ALTER TABLE `%s`
                MODIFY COLUMN `data` JSON NOT NULL
                """.formatted(petTableName));
    }

    /**
     * 检测字段不存在时执行新增字段SQL
     * @param connection 连接
     * @param statement SQL执行器
     * @param tableName 表名
     * @param columnName 字段名
     * @param definition 字段完整定义语句
     */
    private static void addColumnIfMissing(Connection connection, Statement statement, String tableName, String columnName, String definition) throws SQLException {
        if (!hasColumn(connection, tableName, columnName)) {
            statement.executeUpdate("ALTER TABLE `%s` ADD COLUMN %s".formatted(tableName, definition));
        }
    }

    /**
     * 查询指定表是否存在目标字段
     * @param connection 数据库连接
     * @param tableName 数据表名
     * @param columnName 待检测字段名
     * @return true 字段存在；false 不存在
     */
    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
    }

    /**
     * 创建玩家-宠物关联中间表
     * 联合主键：player_uuid + pet_id，一个宠物仅归属一个玩家
     */
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

    /**
     * 从连接池获取数据库连接
     * @return 可用Connection
     * @throws SQLException 获取连接失败
     */
    private Connection getConnection() throws SQLException {
        // 先校验连接池未关闭
        ensureOpen();
        return dataSource.getConnection();
    }

    /**
     * 校验连接池未被关闭，关闭则抛出异常
     */
    private void ensureOpen() {
        if (dataSource.isClosed()) {
            throw new SaveException("MySQL storage has been closed");
        }
    }

    /**
     * ResultSet结果集转为PetRecord实体
     * @param resultSet 查询结果集
     * @return 封装好的宠物记录
     * @throws SQLException 读取字段异常
     */
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

    /**
     * 创建并配置HikariCP连接池实例
     * @param jdbcUrl 完整JDBC地址
     * @param username 账号
     * @param password 密码
     * @param maximumPoolSize 最大连接数
     * @return 配置完成的Hikari数据源
     */
    private static HikariDataSource createDataSource(String jdbcUrl, String username, String password, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        // 密码null转为空字符串防止报错
        config.setPassword(password == null ? "" : password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(1);
        config.setPoolName("QcPet-MySQL");
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT_MS);
        // 开启预处理语句缓存，提升查询性能
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    /**
     * 根据地址、端口、数据库名拼接标准MySQL JDBC链接
     * @param host MySQL地址
     * @param port 端口
     * @param database 数据库名称
     * @return 完整jdbc连接字符串
     */
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
        // 配置编码、时区、关闭SSL（本地内网环境）
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";
    }

    /**
     * 校验数据表名合法性，仅允许字母、数字、下划线，防止SQL注入风险
     * @param tableName 待校验表名
     */
    private static void validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("tableName can only contain letters, numbers and underscores");
        }
    }

    /**
     * 校验ID必须大于等于1，不允许0/负数ID
     * @param id 待校验ID值
     * @param name 参数名称（用于报错提示）
     */
    private static void validateId(long id, String name) {
        if (id < 1) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
    }

    /**
     * 事务回滚通用工具方法，捕获忽略回滚异常
     * @param connection 数据库连接
     */
    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    /**
     * 宠物数据记录实体（Java 16+ Record记录类）
     * 存储单条宠物完整属性
     * @param id 唯一自增ID
     * @param name 宠物自定义名称
     * @param type 宠物类型标识
     * @param level 等级
     * @param exp 当前经验值
     * @param times 计时/时长浮点数据
     * @param data 扩展JSON字符串，存储自定义额外属性
     * @param show 是否在头顶显示宠物
     */
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

    /**
     * 检测服务器是否安装并启用名为database的插件
     * @return true 插件存在且正常启用；false 未安装/禁用/加载失败
     */
    public static boolean hasDatabasePlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("database");
        return plugin != null && plugin.isEnabled();
    }
}