package pk.ajneb97.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.KitArrangement;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.model.internal.GenericCallback;
import pk.ajneb97.model.internal.SimpleCallback;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLConnection {

    private static final String KITS_TABLE = "playerkits_players_kits";

    private PlayerKits2 plugin;
    private HikariConnection connection;
    //False if the arrangement column could not be created, to keep the other data working.
    private volatile boolean arrangementColumn;

    public MySQLConnection(PlayerKits2 plugin){
        this.plugin = plugin;
    }

    public void setupMySql(){
        FileConfiguration config = plugin.getConfigsManager().getMainConfigManager().getConfig();
        try {
            connection = new HikariConnection(config);
            connection.getHikari().getConnection();
            createTables();
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &aSuccessfully connected to the Database."));
        }catch(Exception e) {
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &cError while connecting to the Database."));
        }
    }

    public Connection getConnection() {
        try {
            return connection.getHikari().getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void createTables() {
        try(Connection connection = getConnection()){
            PreparedStatement statement1 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players" +
                    " (UUID varchar(200) NOT NULL, " +
                    " PLAYER_NAME varchar(50), " +
                    " PRIMARY KEY ( UUID ))"
            );
            statement1.executeUpdate();
            PreparedStatement statement2 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + KITS_TABLE +
                    " (ID int NOT NULL AUTO_INCREMENT, " +
                    " UUID varchar(200) NOT NULL, " +
                    " NAME varchar(100), " +
                    " COOLDOWN BIGINT, " +
                    " ONE_TIME BOOLEAN, " +
                    " BOUGHT BOOLEAN, " +
                    " ARRANGEMENT TEXT, " +
                    " PRIMARY KEY ( ID ), " +
                    " FOREIGN KEY (UUID) REFERENCES playerkits_players(UUID))");
            statement2.executeUpdate();

            arrangementColumn = createArrangementColumn(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the arrangement column to tables created by older versions of the plugin.
     */
    private boolean createArrangementColumn(Connection connection) {
        try(ResultSet columns = connection.getMetaData().getColumns(null,null,KITS_TABLE,"ARRANGEMENT")){
            if(columns.next()){
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try(PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE " + KITS_TABLE + " ADD COLUMN ARRANGEMENT TEXT")){
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+
                    " &cError while creating the ARRANGEMENT column, kit arrangements won't be saved."));
            return false;
        }
    }

    public void getPlayer(String uuid, GenericCallback<PlayerData> callback){
        new BukkitRunnable(){
            @Override
            public void run() {
                PlayerData player = null;
                boolean arrangementColumn = MySQLConnection.this.arrangementColumn;
                try(Connection connection = getConnection()){
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT playerkits_players.UUID, playerkits_players.PLAYER_NAME, " +
                                    "playerkits_players_kits.NAME, " +
                                    "playerkits_players_kits.COOLDOWN, " +
                                    "playerkits_players_kits.ONE_TIME, " +
                                    "playerkits_players_kits.BOUGHT" +
                                    (arrangementColumn ? ", playerkits_players_kits.ARRANGEMENT " : " ") +
                                    "FROM playerkits_players LEFT JOIN playerkits_players_kits " +
                                    "ON playerkits_players.UUID = playerkits_players_kits.UUID " +
                                    "WHERE playerkits_players.UUID = ?");

                    statement.setString(1, uuid);
                    ResultSet result = statement.executeQuery();

                    boolean firstFind = true;
                    while(result.next()){
                        UUID uuid = UUID.fromString(result.getString("UUID"));
                        String playerName = result.getString("PLAYER_NAME");
                        String kitName = result.getString("NAME");
                        long cooldown = result.getLong("COOLDOWN");
                        boolean oneTime = result.getBoolean("ONE_TIME");
                        boolean bought = result.getBoolean("BOUGHT");
                        String arrangement = arrangementColumn ? result.getString("ARRANGEMENT") : null;
                        if(player == null){
                            player = new PlayerData(uuid,playerName);
                        }
                        if(kitName != null){
                            PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                            playerDataKit.setCooldown(cooldown);
                            playerDataKit.setOneTime(oneTime);
                            playerDataKit.setBought(bought);
                            playerDataKit.setArrangement(KitArrangement.fromText(arrangement));
                            player.addKit(playerDataKit);
                        }
                    }

                    PlayerData finalPlayer = player;
                    new BukkitRunnable(){
                        @Override
                        public void run() {
                            callback.onDone(finalPlayer);
                        }
                    }.runTask(plugin);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void createPlayer(PlayerData player, SimpleCallback callback){
        new BukkitRunnable(){
            @Override
            public void run() {
                try(Connection connection = getConnection()){
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO playerkits_players " +
                                    "(UUID, PLAYER_NAME) VALUE (?,?)");

                    statement.setString(1, player.getUuid().toString());
                    statement.setString(2, player.getName());
                    statement.executeUpdate();

                    new BukkitRunnable(){
                        @Override
                        public void run() {
                            callback.onDone();
                        }
                    }.runTask(plugin);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void updatePlayerName(PlayerData player){
        new BukkitRunnable(){
            @Override
            public void run() {
                try(Connection connection = getConnection()){
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE playerkits_players SET " +
                                    "PLAYER_NAME=? WHERE UUID=?");

                    statement.setString(1, player.getName());
                    statement.setString(2, player.getUuid().toString());
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void updateKit(PlayerData player,PlayerDataKit kit,boolean mustCreate){
        KitArrangement kitArrangement = kit.getArrangement();
        String arrangement = kitArrangement != null ? kitArrangement.toText() : null;
        new BukkitRunnable(){
            @Override
            public void run() {
                boolean arrangementColumn = MySQLConnection.this.arrangementColumn;
                try(Connection connection = getConnection()){
                    PreparedStatement statement = null;
                    if(mustCreate){
                        // Insert
                        statement = connection.prepareStatement(
                                "INSERT INTO " + KITS_TABLE + " " +
                                        (arrangementColumn ?
                                                "(UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT, ARRANGEMENT) VALUE (?,?,?,?,?,?)" :
                                                "(UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT) VALUE (?,?,?,?,?)"));

                        statement.setString(1, player.getUuid().toString());
                        statement.setString(2, kit.getName());
                        statement.setLong(3, kit.getCooldown());
                        statement.setBoolean(4, kit.isOneTime());
                        statement.setBoolean(5, kit.isBought());
                        if(arrangementColumn){
                            statement.setString(6, arrangement);
                        }
                    }else{
                        // Update
                        statement = connection.prepareStatement(
                                "UPDATE " + KITS_TABLE + " SET " +
                                        (arrangementColumn ?
                                                "COOLDOWN=?, ONE_TIME=?, BOUGHT=?, ARRANGEMENT=? WHERE UUID=? AND NAME=?" :
                                                "COOLDOWN=?, ONE_TIME=?, BOUGHT=? WHERE UUID=? AND NAME=?"));

                        int index = 1;
                        statement.setLong(index++, kit.getCooldown());
                        statement.setBoolean(index++, kit.isOneTime());
                        statement.setBoolean(index++, kit.isBought());
                        if(arrangementColumn){
                            statement.setString(index++, arrangement);
                        }
                        statement.setString(index++, player.getUuid().toString());
                        statement.setString(index, kit.getName());
                    }
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void resetKit(String uuid,String kitName,boolean all){
        new BukkitRunnable(){
            @Override
            public void run() {
                try(Connection connection = getConnection()){
                    PreparedStatement statement;
                    if(all){
                        statement = connection.prepareStatement(
                                "DELETE FROM playerkits_players_kits " +
                                        "WHERE NAME=?");
                        statement.setString(1, kitName);
                    }else{
                        statement = connection.prepareStatement(
                                "DELETE FROM playerkits_players_kits " +
                                        "WHERE UUID=? AND NAME=?");

                        statement.setString(1, uuid);
                        statement.setString(2, kitName);
                    }
                    statement.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
