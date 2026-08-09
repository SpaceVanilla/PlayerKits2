package pk.ajneb97.configs;

import org.bukkit.configuration.file.FileConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.model.CommonConfig;
import pk.ajneb97.model.Kit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class MainConfigManager {

    private PlayerKits2 plugin;
    private CommonConfig configFile;

    //Options
    private Kit newKitDefault;
    private boolean kitPreview;
    private boolean closeInventoryOnClaim;
    private boolean claimKitShortCommand;
    private boolean kitPreviewRequiresKitPermission;
    private boolean kitArrangement;
    private boolean kitArrangementClaimOrder;
    private boolean kitArrangementInPreview;
    private boolean kitArrangementAutoSave;
    private boolean kitArrangementRequiresKitPermission;
    private String kitArrangementPermission;
    private Set<Integer> kitArrangementLockedSlots = new HashSet<>();
    private boolean newKitDefaultSaveModeOriginal;
    private String firstJoinKit;
    private String newKitDefaultInventory;
    private boolean isMySQL;
    private boolean updateNotify;
    private boolean useMiniMessage;

    public MainConfigManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.configFile = new CommonConfig("config.yml",plugin,null, false);
        this.configFile.registerConfig();
        checkUpdate();
    }

    public void configure(){
        FileConfiguration config = configFile.getConfig();
        newKitDefault = KitsConfigManager.getKitFromConfig(config,plugin,null,"new_kit_default_values.");
        kitPreview = config.getBoolean("kit_preview");
        closeInventoryOnClaim = config.getBoolean("close_inventory_on_claim");
        kitPreviewRequiresKitPermission = config.getBoolean("kit_preview_requires_kit_permission");
        kitArrangement = config.getBoolean("kit_arrangement");
        kitArrangementClaimOrder = config.getBoolean("kit_arrangement_claim_order");
        kitArrangementInPreview = config.getBoolean("kit_arrangement_in_preview");
        kitArrangementAutoSave = config.getBoolean("kit_arrangement_auto_save");
        kitArrangementRequiresKitPermission = config.getBoolean("kit_arrangement_requires_kit_permission");
        kitArrangementPermission = config.getString("kit_arrangement_permission");
        kitArrangementLockedSlots = getSlotsFromString(config.getString("kit_arrangement_locked_slots"));
        firstJoinKit = config.getString("first_join_kit");
        newKitDefaultInventory = config.getString("new_kit_default_inventory");
        isMySQL = config.getBoolean("mysql_database.enabled");
        updateNotify = config.getBoolean("update_notify");
        claimKitShortCommand = config.getBoolean("claim_kit_short_command");
        useMiniMessage = config.getBoolean("use_minimessage");
        newKitDefaultSaveModeOriginal = config.getBoolean("new_kit_default_save_mode_original");
    }

    /**
     * Slots of a list like '36;37;40-44', ignoring the ones that are not a number.
     */
    public Set<Integer> getSlotsFromString(String slots){
        Set<Integer> result = new HashSet<>();
        if(slots == null || slots.trim().isEmpty()){
            return result;
        }

        for(String part : slots.split(";")){
            part = part.trim();
            if(part.isEmpty()){
                continue;
            }
            try{
                if(part.contains("-")){
                    String[] range = part.split("-");
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for(int slot=from;slot<=to;slot++){
                        result.add(slot);
                    }
                }else{
                    result.add(Integer.parseInt(part));
                }
            }catch(Exception e){
                plugin.getLogger().warning("Invalid slot '"+part+"' on kit_arrangement_locked_slots.");
            }
        }
        return result;
    }

    public boolean reloadConfig(){
        if(!configFile.reloadConfig()){
            return false;
        }
        configure();
        return true;
    }

    public FileConfiguration getConfig(){
        return configFile.getConfig();
    }

    public void checkUpdate(){
        Path pathConfig = Paths.get(configFile.getRoute());
        try{
            String text = new String(Files.readAllBytes(pathConfig));
            if(!text.contains("use_minimessage:")){
                getConfig().set("use_minimessage",false);
                configFile.saveConfig();
            }
            if(!text.contains("verifyServerCertificate:")){
                getConfig().set("mysql_database.pool.connectionTimeout",5000);
                getConfig().set("mysql_database.advanced.verifyServerCertificate",false);
                getConfig().set("mysql_database.advanced.useSSL",true);
                getConfig().set("mysql_database.advanced.allowPublicKeyRetrieval",true);
                configFile.saveConfig();
            }
            if(!text.contains("new_kit_default_save_mode_original:")){
                getConfig().set("new_kit_default_save_mode_original", true);
                configFile.saveConfig();
            }
            if(!text.contains("kit_arrangement:")){
                getConfig().set("kit_arrangement", true);
                getConfig().set("kit_arrangement_claim_order", true);
                configFile.saveConfig();
            }
            if(!text.contains("kit_arrangement_in_preview:")){
                getConfig().set("kit_arrangement_in_preview", true);
                getConfig().set("kit_arrangement_auto_save", true);
                getConfig().set("kit_arrangement_requires_kit_permission", true);
                getConfig().set("kit_arrangement_permission", "none");
                configFile.saveConfig();
            }
            if(!text.contains("kit_arrangement_locked_slots:")){
                getConfig().set("kit_arrangement_locked_slots", "36-40");
                configFile.saveConfig();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public Kit getNewKitDefault() {
        return newKitDefault;
    }

    public boolean isKitPreview() {
        return kitPreview;
    }

    public boolean isCloseInventoryOnClaim() {
        return closeInventoryOnClaim;
    }

    public boolean isKitPreviewRequiresKitPermission() {
        return kitPreviewRequiresKitPermission;
    }

    public boolean isKitArrangement() {
        return kitArrangement;
    }

    public boolean isKitArrangementClaimOrder() {
        return kitArrangementClaimOrder;
    }

    public boolean isKitArrangementInPreview() {
        return kitArrangementInPreview;
    }

    public boolean isKitArrangementAutoSave() {
        return kitArrangementAutoSave;
    }

    public boolean isKitArrangementRequiresKitPermission() {
        return kitArrangementRequiresKitPermission;
    }

    /**
     * Extra permission needed to arrange kits, null if any player can arrange them.
     */
    public String getKitArrangementPermission() {
        if(kitArrangementPermission == null || kitArrangementPermission.isEmpty()
                || kitArrangementPermission.equalsIgnoreCase("none")){
            return null;
        }
        return kitArrangementPermission;
    }

    /**
     * Slots that players can't move when they arrange a kit.
     */
    public Set<Integer> getKitArrangementLockedSlots() {
        return kitArrangementLockedSlots;
    }

    public String getFirstJoinKit() {
        return firstJoinKit;
    }

    public String getNewKitDefaultInventory() {
        return newKitDefaultInventory;
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public boolean isUpdateNotify() {
        return updateNotify;
    }

    public boolean isClaimKitShortCommand() {
        return claimKitShortCommand;
    }

    public boolean isNewKitDefaultSaveModeOriginal() {
        return newKitDefaultSaveModeOriginal;
    }

    public boolean isUseMiniMessage() {
        return useMiniMessage;
    }
}
