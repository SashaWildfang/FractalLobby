/*
 * FractalLobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.data;

import com.sasha.lobby.Lobby;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.ServicePriority;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The network database (MySQL, on the Lobby's Pterodactyl databases). FractalLobby opens it and shares it as a
 * {@link DataSource} service, so FractalCosmetics and FractalRadio keep their records in the same place, and the
 * CTF servers read and write network levels and boosters there too.
 *
 * Every query runs on the database threads, never the main thread.
 */
public final class NetworkDb {

    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private final Lobby plugin;
    private final HikariDataSource pool;
    private final ExecutorService threads = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "FractalLobby-Database");
        t.setDaemon(true);
        return t;
    });

    private NetworkDb(Lobby plugin, HikariDataSource pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    /** Connects (config.yml database:), makes the tables and shares the pool. Null when it's off or can't connect. */
    public static NetworkDb open(Lobby plugin) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("database");
        if (c == null || !c.getBoolean("enabled", true)) return null;
        String user = c.getString("username", ""), password = c.getString("password", "");
        if (user.isBlank() || password.isBlank()) {
            plugin.getLogger().warning("No database yet (config.yml database: username/password). Records stay in files until it's set.");
            return null;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + c.getString("host", "172.18.0.1") + ":" + c.getInt("port", 3306) + "/"
                + c.getString("name", "s3_network") + "?" + c.getString("options", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"));
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(Math.max(2, c.getInt("pool-size", 6)));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(8000);
        config.setPoolName("FractalLobby");
        try {
            HikariDataSource pool = new HikariDataSource(config);
            NetworkDb db = new NetworkDb(plugin, pool);
            db.createTables();
            plugin.getServer().getServicesManager().register(DataSource.class, pool, plugin, ServicePriority.Normal);
            plugin.getLogger().info("Connected to the network database.");
            return db;
        } catch (Exception e) {
            plugin.getLogger().severe("Couldn't connect to the network database (" + e.getMessage() + "). Records stay in files.");
            return null;
        }
    }

    private void createTables() throws SQLException {
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            // each player's lobby settings (their persistent data for FractalLobby and FractalRadio), as saved by Paper
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lobby_player_data (uuid CHAR(36) NOT NULL, namespace VARCHAR(32) NOT NULL,"
                    + " data MEDIUMBLOB NOT NULL, updated BIGINT NOT NULL, PRIMARY KEY (uuid, namespace))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lobby_parkour (uuid CHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                    + " best INT NOT NULL, time_seconds INT NOT NULL, games INT NOT NULL)");
            // network levels: xp earned anywhere (CTF so far)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_players (uuid CHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                    + " xp BIGINT NOT NULL DEFAULT 0, updated BIGINT NOT NULL)");
            // boosters players own but haven't used, and boosters running or queued per game
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_booster_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL,"
                    + " name VARCHAR(32) NOT NULL, game VARCHAR(16) NOT NULL, multiplier DOUBLE NOT NULL, minutes INT NOT NULL,"
                    + " source VARCHAR(64), created BIGINT NOT NULL, used_at BIGINT, INDEX (uuid))");
            // network level rewards each player has claimed (stars: the Mystery Box it gave, 0 = none)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_level_rewards (uuid CHAR(36) NOT NULL, level INT NOT NULL,"
                    + " stars INT NOT NULL DEFAULT 0, claimed_at BIGINT NOT NULL, PRIMARY KEY (uuid, level))");
            // CTF coins waiting to be paid on CTF (the CTF servers pay and delete them when the player joins)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_pending_coins (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL,"
                    + " name VARCHAR(32) NOT NULL, game VARCHAR(16) NOT NULL, coins INT NOT NULL, source VARCHAR(64), created BIGINT NOT NULL,"
                    + " INDEX (uuid))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_boosters (id BIGINT AUTO_INCREMENT PRIMARY KEY, game VARCHAR(16) NOT NULL,"
                    + " multiplier DOUBLE NOT NULL, starts BIGINT NOT NULL, ends BIGINT NOT NULL, owner_uuid CHAR(36),"
                    + " owner_name VARCHAR(32) NOT NULL, created BIGINT NOT NULL, INDEX (game, ends))");
        }
    }

    public DataSource source() {
        return pool;
    }

    public <T> CompletableFuture<T> run(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = pool.getConnection()) {
                return work.run(c);
            } catch (SQLException e) {
                plugin.getLogger().warning("Database: " + e.getMessage());
                throw new IllegalStateException(e);
            }
        }, threads);
    }

    /** Runs here and now (the caller is off the main thread, or it's start-up / shut-down). */
    public <T> T now(Work<T> work) throws SQLException {
        try (Connection c = pool.getConnection()) {
            return work.run(c);
        }
    }

    public void close() {
        plugin.getServer().getServicesManager().unregister(DataSource.class, pool);
        threads.shutdown();
        try {
            threads.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.close();
    }
}
