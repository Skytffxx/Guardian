package com.skyzzz.guardian.core.storage;

import com.skyzzz.guardian.api.violation.ViolationRecord;
import com.skyzzz.guardian.api.violation.ViolationStore;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/**
 * SQLite / MySQL / MariaDB history store. Writes are queued and flushed by a single
 * background thread so a slow disk never stalls packet handling.
 */
public final class SqlViolationStore implements ViolationStore {

    private final GuardianPlugin plugin;
    private final GuardianConfig config;

    private HikariDataSource dataSource;
    private ExecutorService writer;
    private final LinkedBlockingQueue<ViolationRecord> queue = new LinkedBlockingQueue<>(20_000);
    private volatile boolean running;

    public SqlViolationStore(GuardianPlugin plugin, GuardianConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void init() throws Exception {
        String type = config.getString("storage.type", "SQLITE").toUpperCase();

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("Guardian-Pool");
        hikari.setMaximumPoolSize(config.getInt("storage.pool-size", 4));
        hikari.setConnectionTimeout(5_000L);

        if ("SQLITE".equals(type)) {
            File file = new File(plugin.getDataFolder(),
                    config.getString("storage.sqlite.file", "violations.db"));
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("Could not create storage directory");
            }
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
        } else {
            String host = config.getString("storage.mysql.host", "127.0.0.1");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "guardian");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&characterEncoding=utf8");
            hikari.setUsername(config.getString("storage.mysql.username", "root"));
            hikari.setPassword(config.getString("storage.mysql.password", ""));
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        this.dataSource = new HikariDataSource(hikari);
        createSchema();

        this.running = true;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Guardian-Storage");
            thread.setDaemon(true);
            return thread;
        });
        writer.execute(this::drainLoop);
    }

    private void createSchema() throws SQLException {
        String ddl = """
                CREATE TABLE IF NOT EXISTS guardian_violations (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  uuid VARCHAR(36) NOT NULL,
                  player_name VARCHAR(16) NOT NULL,
                  check_name VARCHAR(48) NOT NULL,
                  category VARCHAR(24) NOT NULL,
                  vl DOUBLE NOT NULL,
                  debug TEXT,
                  created_at BIGINT NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_guardian_uuid "
                    + "ON guardian_violations (uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_guardian_created "
                    + "ON guardian_violations (created_at)");
        }
    }

    private void drainLoop() {
        while (running) {
            try {
                ViolationRecord record = queue.take();
                insert(record);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist violation", exception);
            }
        }
    }

    private void insert(ViolationRecord record) throws SQLException {
        String sql = "INSERT INTO guardian_violations "
                + "(uuid, player_name, check_name, category, vl, debug, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.uuid().toString());
            statement.setString(2, record.playerName());
            statement.setString(3, record.checkName());
            statement.setString(4, record.category());
            statement.setDouble(5, record.vl());
            statement.setString(6, record.debug());
            statement.setLong(7, record.timestampEpochMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public void record(ViolationRecord record) {
        if (!running) {
            return;
        }
        if (!queue.offer(record)) {
            // Queue full: drop the oldest rather than block the packet thread.
            queue.poll();
            queue.offer(record);
        }
    }

    @Override
    public List<ViolationRecord> history(UUID uuid, int limit) {
        List<ViolationRecord> result = new ArrayList<>();
        String sql = "SELECT id, uuid, player_name, check_name, category, vl, debug, created_at "
                + "FROM guardian_violations WHERE uuid = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "History query failed", exception);
        }
        return result;
    }

    @Override
    public List<ViolationRecord> recent(int limit) {
        List<ViolationRecord> result = new ArrayList<>();
        String sql = "SELECT id, uuid, player_name, check_name, category, vl, debug, created_at "
                + "FROM guardian_violations ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Recent query failed", exception);
        }
        return result;
    }

    private ViolationRecord map(ResultSet resultSet) throws SQLException {
        return new ViolationRecord(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("player_name"),
                resultSet.getString("check_name"),
                resultSet.getString("category"),
                resultSet.getDouble("vl"),
                resultSet.getString("debug"),
                resultSet.getLong("created_at"));
    }

    @Override
    public void flush() {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (writer != null) {
            writer.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}