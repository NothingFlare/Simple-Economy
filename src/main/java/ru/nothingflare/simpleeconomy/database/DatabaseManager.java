/*
 * MIT License
 *
 * Copyright (c) 2026 NothingFlare
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.nothingflare.simpleeconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.database.models.AuctionClaim;
import ru.nothingflare.simpleeconomy.database.models.AuctionListing;
import ru.nothingflare.simpleeconomy.market.DynamicMarketManager;
import ru.nothingflare.simpleeconomy.util.ItemSerializer;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private final SimpleEconomyPlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    private final Map<Material, Double> buyerPricesCache = new ConcurrentHashMap<>();

    public DatabaseManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        String typeStr = plugin.getConfig().getString("database.type", "sqlite");
        this.databaseType = DatabaseType.fromString(typeStr);

        HikariConfig config = new HikariConfig();
        if (databaseType == DatabaseType.MYSQL) {
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String db = plugin.getConfig().getString("database.mysql.database", "simple_economy");
            String user = plugin.getConfig().getString("database.mysql.username", "root");
            String pass = plugin.getConfig().getString("database.mysql.password", "");
            int poolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);
            long timeout = plugin.getConfig().getLong("database.mysql.connection-timeout", 30000);

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(poolSize);
            config.setConnectionTimeout(timeout);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.sqlite.file", "database.db"));
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(2);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=10000;");
        }

        config.setPoolName("SimpleEconomyPool");
        try {
            this.dataSource = new HikariDataSource(config);
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database connection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Data source is not initialized.");
        }
        return dataSource.getConnection();
    }

    private void createTables() {
        String idDef = databaseType == DatabaseType.SQLITE
                ? "id INTEGER PRIMARY KEY AUTOINCREMENT"
                : "id BIGINT PRIMARY KEY AUTO_INCREMENT";

        String createListings = "CREATE TABLE IF NOT EXISTS se_auction_listings (" +
                idDef + ", " +
                "seller_uuid VARCHAR(36) NOT NULL, " +
                "seller_name VARCHAR(64) NOT NULL, " +
                "item_data TEXT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "published_at BIGINT NOT NULL DEFAULT 0, " +
                "status VARCHAR(16) NOT NULL" +
                ");";

        String createClaims = "CREATE TABLE IF NOT EXISTS se_auction_claims (" +
                idDef + ", " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "item_data TEXT NOT NULL, " +
                "reason VARCHAR(32) NOT NULL, " +
                "created_at BIGINT NOT NULL" +
                ");";

        String createBuyer = "CREATE TABLE IF NOT EXISTS se_buyer_prices (" +
                "material VARCHAR(64) PRIMARY KEY, " +
                "sell_price DOUBLE NOT NULL" +
                ");";

        String createMarket = "CREATE TABLE IF NOT EXISTS se_dynamic_market (" +
                "market_type VARCHAR(16) NOT NULL, " +
                "item_key VARCHAR(64) NOT NULL, " +
                "current_price DOUBLE NOT NULL, " +
                "base_price DOUBLE NOT NULL, " +
                "last_update BIGINT NOT NULL, " +
                "PRIMARY KEY (market_type, item_key)" +
                ");";

        String createAdminLogs = "CREATE TABLE IF NOT EXISTS se_auction_admin_logs (" +
                idDef + ", " +
                "listing_id BIGINT NOT NULL, " +
                "admin_uuid VARCHAR(36) NOT NULL, " +
                "admin_name VARCHAR(64) NOT NULL, " +
                "seller_uuid VARCHAR(36) NOT NULL, " +
                "seller_name VARCHAR(64) NOT NULL, " +
                "item_data TEXT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "deleted_at BIGINT NOT NULL" +
                ");";

        String idxListingsStatus = "CREATE INDEX IF NOT EXISTS idx_auction_status_created ON se_auction_listings (status, created_at);";
        String idxListingsSeller = "CREATE INDEX IF NOT EXISTS idx_auction_seller ON se_auction_listings (seller_uuid, status);";
        String idxClaimsPlayer = "CREATE INDEX IF NOT EXISTS idx_auction_claims_player ON se_auction_claims (player_uuid);";
        String idxAdminLogs = "CREATE INDEX IF NOT EXISTS idx_auction_admin_logs ON se_auction_admin_logs (deleted_at);";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createListings);
            stmt.execute(createClaims);
            stmt.execute(createBuyer);
            stmt.execute(createMarket);
            stmt.execute(createAdminLogs);
            try {
                stmt.execute(idxListingsStatus);
                stmt.execute(idxListingsSeller);
                stmt.execute(idxClaimsPlayer);
                stmt.execute(idxAdminLogs);
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE se_auction_listings ADD COLUMN published_at BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating database tables: " + e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // --- Auction Listings ---

    public long createListing(UUID seller, String sellerName, ItemStack item, double price) {
        return createListing(seller, sellerName, item, price, 0);
    }

    public long createListing(UUID seller, String sellerName, ItemStack item, double price, long delaySeconds) {
        String sql = "INSERT INTO se_auction_listings (seller_uuid, seller_name, item_data, price, created_at, published_at, status) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            long publishedAt = now + (delaySeconds * 1000L);

            ps.setString(1, seller.toString());
            ps.setString(2, sellerName);
            ps.setString(3, ItemSerializer.toBase64(item));
            ps.setDouble(4, price);
            ps.setLong(5, now);
            ps.setLong(6, publishedAt);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create auction listing: " + e.getMessage());
        }
        return -1;
    }

    public List<AuctionListing> getActiveListings(int offset, int limit) {
        List<AuctionListing> list = new ArrayList<>();
        String sql = "SELECT * FROM se_auction_listings WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapListing(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active auction listings: " + e.getMessage());
        }
        return list;
    }

    public int getTotalActiveListings() {
        String sql = "SELECT COUNT(*) FROM se_auction_listings WHERE status = 'ACTIVE'";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to count active listings: " + e.getMessage());
        }
        return 0;
    }

    public List<AuctionListing> getPlayerActiveListings(UUID player) {
        List<AuctionListing> list = new ArrayList<>();
        String sql = "SELECT * FROM se_auction_listings WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapListing(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player active listings: " + e.getMessage());
        }
        return list;
    }

    public int getPlayerActiveListingCount(UUID player) {
        String sql = "SELECT COUNT(*) FROM se_auction_listings WHERE seller_uuid = ? AND status = 'ACTIVE'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to count player listings: " + e.getMessage());
        }
        return 0;
    }

    public AuctionListing getListing(long id) {
        String sql = "SELECT * FROM se_auction_listings WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapListing(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get listing by id: " + e.getMessage());
        }
        return null;
    }

    public boolean purchaseListing(long listingId) {
        String sql = "UPDATE se_auction_listings SET status = 'SOLD' WHERE id = ? AND status = 'ACTIVE' AND (published_at <= ? OR published_at = 0)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            ps.setLong(2, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to purchase listing atomically: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelListing(long listingId, UUID sellerUuid) {
        String sql = "UPDATE se_auction_listings SET status = 'CANCELLED' WHERE id = ? AND seller_uuid = ? AND status = 'ACTIVE'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            ps.setString(2, sellerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to cancel listing: " + e.getMessage());
            return false;
        }
    }

    public boolean adminDeleteListing(long listingId, UUID adminUuid, String adminName) {
        String selectSql = "SELECT seller_uuid, seller_name, item_data, price FROM se_auction_listings WHERE id = ? AND status = 'ACTIVE'";
        String updateSql = "UPDATE se_auction_listings SET status = 'ADMIN_DELETED' WHERE id = ? AND status = 'ACTIVE'";
        String logSql = "INSERT INTO se_auction_admin_logs (listing_id, admin_uuid, admin_name, seller_uuid, seller_name, item_data, price, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sellerUuid = null;
                String sellerName = null;
                String itemData = null;
                double price = 0.0;

                try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                    psSelect.setLong(1, listingId);
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (rs.next()) {
                            sellerUuid = rs.getString("seller_uuid");
                            sellerName = rs.getString("seller_name");
                            itemData = rs.getString("item_data");
                            price = rs.getDouble("price");
                        } else {
                            conn.rollback();
                            conn.setAutoCommit(originalAutoCommit);
                            return false;
                        }
                    }
                }

                int updated;
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setLong(1, listingId);
                    updated = psUpdate.executeUpdate();
                }

                if (updated <= 0) {
                    conn.rollback();
                    conn.setAutoCommit(originalAutoCommit);
                    return false;
                }

                try (PreparedStatement psLog = conn.prepareStatement(logSql)) {
                    psLog.setLong(1, listingId);
                    psLog.setString(2, adminUuid.toString());
                    psLog.setString(3, adminName);
                    psLog.setString(4, sellerUuid);
                    psLog.setString(5, sellerName);
                    psLog.setString(6, itemData);
                    psLog.setDouble(7, price);
                    psLog.setLong(8, System.currentTimeMillis());
                    psLog.executeUpdate();
                }

                conn.commit();
                conn.setAutoCommit(originalAutoCommit);
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                conn.setAutoCommit(originalAutoCommit);
                throw ex;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to admin-delete listing " + listingId + ": " + e.getMessage());
            return false;
        }
    }

    private static class ExpiredRecord {
        final long id;
        final UUID seller;
        final String itemData;

        ExpiredRecord(long id, UUID seller, String itemData) {
            this.id = id;
            this.seller = seller;
            this.itemData = itemData;
        }
    }

    public void processExpiredListings(long maxAgeMillis) {
        long threshold = System.currentTimeMillis() - maxAgeMillis;
        long now = System.currentTimeMillis();
        List<ExpiredRecord> toExpire = new ArrayList<>();
        String selectSql = "SELECT id, seller_uuid, item_data FROM se_auction_listings WHERE status = 'ACTIVE' AND created_at < ? AND (published_at <= ? OR published_at = 0)";

        try (Connection conn = getConnection();
             PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
            psSelect.setLong(1, threshold);
            psSelect.setLong(2, now);
            try (ResultSet rs = psSelect.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    UUID seller = UUID.fromString(rs.getString("seller_uuid"));
                    String itemData = rs.getString("item_data");
                    toExpire.add(new ExpiredRecord(id, seller, itemData));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to expire old listings: " + e.getMessage());
            return;
        }

        if (toExpire.isEmpty()) {
            return;
        }

        String updateSql = "UPDATE se_auction_listings SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'";
        for (ExpiredRecord record : toExpire) {
            boolean updated = false;
            try (Connection conn = getConnection();
                 PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setLong(1, record.id);
                updated = psUpdate.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update status to EXPIRED for id " + record.id + ": " + e.getMessage());
            }

            if (updated) {
                ItemStack item = ItemSerializer.fromBase64(record.itemData);
                if (item != null) {
                    addClaim(record.seller, item, "EXPIRED");
                }
            }
        }
    }

    // --- Claims ---

    public boolean addClaim(UUID player, ItemStack item, String reason) {
        if (item == null) {
            return false;
        }
        String sql = "INSERT INTO se_auction_claims (player_uuid, item_data, reason, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, ItemSerializer.toBase64(item));
            ps.setString(3, reason);
            ps.setLong(4, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add claim: " + e.getMessage());
            return false;
        }
    }

    public List<AuctionClaim> getPlayerClaims(UUID player) {
        List<AuctionClaim> claims = new ArrayList<>();
        String sql = "SELECT * FROM se_auction_claims WHERE player_uuid = ? ORDER BY created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claims.add(new AuctionClaim(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            ItemSerializer.fromBase64(rs.getString("item_data")),
                            rs.getString("reason"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player claims: " + e.getMessage());
        }
        return claims;
    }

    public boolean removeClaim(long claimId) {
        String sql = "DELETE FROM se_auction_claims WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, claimId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove claim: " + e.getMessage());
            return false;
        }
    }

    // --- Buyer Prices ---

    public void loadBuyerPrices(Map<Material, Double> defaults) {
        buyerPricesCache.clear();
        if (defaults != null) {
            buyerPricesCache.putAll(defaults);
        }

        String sql = "SELECT material, sell_price FROM se_buyer_prices";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String matStr = rs.getString("material");
                try {
                    Material mat = Material.valueOf(matStr.toUpperCase());
                    buyerPricesCache.put(mat, rs.getDouble("sell_price"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load buyer prices from database: " + e.getMessage());
        }
    }

    public double getBuyerPrice(Material material) {
        return buyerPricesCache.getOrDefault(material, 0.0);
    }

    public Map<Material, Double> getAllBuyerPrices() {
        return Collections.unmodifiableMap(buyerPricesCache);
    }

    public void setBuyerPrice(Material material, double price) {
        buyerPricesCache.put(material, price);
        String sql = databaseType == DatabaseType.MYSQL
                ? "INSERT INTO se_buyer_prices (material, sell_price) VALUES (?, ?) ON DUPLICATE KEY UPDATE sell_price = VALUES(sell_price)"
                : "INSERT OR REPLACE INTO se_buyer_prices (material, sell_price) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, material.name());
            ps.setDouble(2, price);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save buyer price to database: " + e.getMessage());
        }
    }

    // --- Dynamic Market ---

    public void loadDynamicMarket(Map<String, DynamicMarketManager.MarketItemData> shopPrices, Map<Material, DynamicMarketManager.MarketItemData> buyerPrices) {
        String sql = "SELECT market_type, item_key, current_price, base_price, last_update FROM se_dynamic_market";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String marketType = rs.getString("market_type");
                String itemKey = rs.getString("item_key");
                double currentPrice = rs.getDouble("current_price");
                double basePrice = rs.getDouble("base_price");
                long lastUpdate = rs.getLong("last_update");

                DynamicMarketManager.MarketItemData data = new DynamicMarketManager.MarketItemData(marketType, itemKey, currentPrice, basePrice, lastUpdate);
                if ("SHOP".equalsIgnoreCase(marketType)) {
                    shopPrices.put(itemKey, data);
                } else if ("BUYER".equalsIgnoreCase(marketType)) {
                    try {
                        Material mat = Material.valueOf(itemKey.toUpperCase());
                        buyerPrices.put(mat, data);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load dynamic market prices from database: " + e.getMessage());
        }
    }

    public void saveDynamicMarketItem(DynamicMarketManager.MarketItemData data) {
        String sql = databaseType == DatabaseType.MYSQL
                ? "INSERT INTO se_dynamic_market (market_type, item_key, current_price, base_price, last_update) VALUES (?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price), base_price = VALUES(base_price), last_update = VALUES(last_update)"
                : "INSERT OR REPLACE INTO se_dynamic_market (market_type, item_key, current_price, base_price, last_update) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getMarketType());
            ps.setString(2, data.getItemKey());
            ps.setDouble(3, data.getCurrentPrice());
            ps.setDouble(4, data.getBasePrice());
            ps.setLong(5, data.getLastUpdate());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save dynamic market item: " + e.getMessage());
        }
    }

    public void saveAllDynamicMarketItems(Collection<DynamicMarketManager.MarketItemData> list) {
        if (list == null || list.isEmpty()) return;
        String sql = databaseType == DatabaseType.MYSQL
                ? "INSERT INTO se_dynamic_market (market_type, item_key, current_price, base_price, last_update) VALUES (?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price), base_price = VALUES(base_price), last_update = VALUES(last_update)"
                : "INSERT OR REPLACE INTO se_dynamic_market (market_type, item_key, current_price, base_price, last_update) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            for (DynamicMarketManager.MarketItemData data : list) {
                ps.setString(1, data.getMarketType());
                ps.setString(2, data.getItemKey());
                ps.setDouble(3, data.getCurrentPrice());
                ps.setDouble(4, data.getBasePrice());
                ps.setLong(5, data.getLastUpdate());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to batch save dynamic market items: " + e.getMessage());
        }
    }

    private AuctionListing mapListing(ResultSet rs) throws SQLException {
        long publishedAt;
        try {
            publishedAt = rs.getLong("published_at");
        } catch (SQLException e) {
            publishedAt = rs.getLong("created_at");
        }
        if (publishedAt == 0) {
            publishedAt = rs.getLong("created_at");
        }

        return new AuctionListing(
            rs.getLong("id"),
            UUID.fromString(rs.getString("seller_uuid")),
            rs.getString("seller_name"),
            ItemSerializer.fromBase64(rs.getString("item_data")),
            rs.getDouble("price"),
            rs.getLong("created_at"),
            publishedAt,
            rs.getString("status")
        );
    }
}
