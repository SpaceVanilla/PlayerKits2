package pk.ajneb97.configs;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.model.CommonConfig;
import pk.ajneb97.managers.InventoryArrangeManager;
import pk.ajneb97.managers.InventoryManager;
import pk.ajneb97.managers.KitItemManager;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.inventory.ItemKitInventory;
import pk.ajneb97.model.inventory.KitInventory;
import pk.ajneb97.model.item.KitItem;
import pk.ajneb97.utils.OtherUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryConfigManager {

    private static final String FILE_NAME = "inventory.yml";

    /**
     * Items of the default inventories that don't exist on legacy versions, so they
     * are replaced when a default inventory is restored on one of those servers.
     */
    private static final Map<String,String> LEGACY_ITEM_IDS = new HashMap<>();
    static {
        LEGACY_ITEM_IDS.put("BLACK_STAINED_GLASS_PANE","STAINED_GLASS_PANE:15");
        LEGACY_ITEM_IDS.put("RED_STAINED_GLASS_PANE","STAINED_GLASS_PANE:14");
        LEGACY_ITEM_IDS.put("GREEN_STAINED_GLASS_PANE","STAINED_GLASS_PANE:13");
        LEGACY_ITEM_IDS.put("LIME_STAINED_GLASS_PANE","STAINED_GLASS_PANE:5");
        LEGACY_ITEM_IDS.put("YELLOW_STAINED_GLASS_PANE","STAINED_GLASS_PANE:4");
        LEGACY_ITEM_IDS.put("CRAFTING_TABLE","WORKBENCH");
        LEGACY_ITEM_IDS.put("BARREL","CHEST");
    }

    private PlayerKits2 plugin;
    private CommonConfig configFile;


    public InventoryConfigManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.configFile = new CommonConfig(FILE_NAME,plugin,null, false);
        this.configFile.registerConfig();
        if(this.configFile.isFirstTime() && OtherUtils.isLegacy()){
            checkAndFix();
        }
        checkClickCommands();
        checkDefaultInventories();
        checkArrangeInventory();
    }

    public void checkAndFix(){
        FileConfiguration config = configFile.getConfig();
        config.set("inventories.main_inventory.0;1;7;8;9;17;36;44;45;46;52;53.item.id","STAINED_GLASS_PANE:15");
        config.set("inventories.premium_kits_inventory.0;1;7;8;9;17;36;44;45;46;52;53.item.id","STAINED_GLASS_PANE:4");
        config.set("inventories.buy_requirements_inventory.10.item.id","STAINED_GLASS_PANE:14");
        config.set("inventories.buy_requirements_inventory.16.item.id","STAINED_GLASS_PANE:5");
        configFile.saveConfig();
    }

    public void checkClickCommands(){
        boolean needsSave = false;
        FileConfiguration config = configFile.getConfig();
        if(config.contains("inventories")) {
            for (String key : config.getConfigurationSection("inventories").getKeys(false)) {
                for(String slotString : config.getConfigurationSection("inventories."+key).getKeys(false)) {
                    String path = "inventories."+key+"."+slotString;
                    if(config.contains(path+".click_commands")){
                        List<String> clickActions = new ArrayList<>();
                        List<String> clickCommands = config.getStringList(path+".click_commands");
                        for(String c : clickCommands){
                            if(c.startsWith("msg %player% ")) {
                                String text = c.replace("msg %player% ", "");
                                clickActions.add("message: "+text);
                            }else if(c.equals("close_inventory")){
                                clickActions.add(c);
                            }else{
                                clickActions.add("console_command: "+c);
                            }
                        }
                        config.set(path+".click_actions",clickActions);
                        needsSave = true;
                    }
                }
            }
        }

        if(needsSave){
            configFile.saveConfig();
        }
    }

    /**
     * Restores the inventories the plugin can't work without, using the default file
     * included on the jar, when they are not found on the config. Without them the
     * plugin reports a critical error and can't be used at all, so they are recreated
     * instead of only being reported by the verify manager.
     */
    public void checkDefaultInventories(){
        FileConfiguration config = configFile.getConfig();

        List<String> missingInventories = new ArrayList<>();
        for(String inventoryName : InventoryManager.REQUIRED_INVENTORY_NAMES){
            if(!config.contains("inventories."+inventoryName)){
                missingInventories.add(inventoryName);
            }
        }
        if(missingInventories.isEmpty()){
            return;
        }

        FileConfiguration defaultConfig = getDefaultConfig();
        if(defaultConfig == null){
            //The default file can't be read, so the verify manager reports the error.
            return;
        }

        boolean legacy = OtherUtils.isLegacy();
        boolean restored = false;
        for(String inventoryName : missingInventories){
            ConfigurationSection defaultInventory = defaultConfig.getConfigurationSection("inventories."+inventoryName);
            if(defaultInventory == null){
                continue;
            }

            restoreDefaultInventory(config,defaultInventory,inventoryName,legacy);
            restored = true;
            sendConsoleMessage("&eInventory &b"+inventoryName+" &ewas not found on "+FILE_NAME
                    +", it has been restored with its default values.");
        }

        if(restored){
            configFile.saveConfig();
        }
    }

    /**
     * Copies a whole inventory from the default file. Items pointing to a kit or to
     * another inventory are only copied when they still make sense on this config,
     * so restoring an inventory can't add new errors to the plugin.
     */
    private void restoreDefaultInventory(FileConfiguration config,ConfigurationSection defaultInventory,
                                         String inventoryName,boolean legacy){
        String path = "inventories."+inventoryName;
        config.set(path+".slots",defaultInventory.getInt("slots"));
        config.set(path+".title",defaultInventory.getString("title"));

        for(String slotString : defaultInventory.getKeys(false)){
            if(slotString.equals("slots") || slotString.equals("title")){
                continue;
            }

            ConfigurationSection defaultSlot = defaultInventory.getConfigurationSection(slotString);
            if(defaultSlot == null){
                continue;
            }

            String type = defaultSlot.getString("type");
            if(type != null && type.startsWith("kit: ")){
                //The kits of the default file are not the ones of this server.
                continue;
            }

            String openInventory = defaultSlot.getString("open_inventory");
            if(openInventory != null && !openInventory.equals("previous")
                    && !config.contains("inventories."+openInventory)){
                //The item would open an inventory that doesn't exist on this config.
                continue;
            }

            for(String key : defaultSlot.getKeys(true)){
                Object value = defaultSlot.get(key);
                if(value == null || value instanceof ConfigurationSection){
                    continue;
                }
                if(legacy && key.equals("item.id")){
                    value = LEGACY_ITEM_IDS.getOrDefault(value.toString(),value.toString());
                }
                config.set(path+"."+slotString+"."+key,value);
            }
        }
    }

    /**
     * Reads the default inventory.yml included on the jar, returning null if it can't be read.
     */
    private FileConfiguration getDefaultConfig(){
        try(InputStream resource = plugin.getResource(FILE_NAME)){
            if(resource == null){
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }catch(Exception e){
            return null;
        }
    }

    private void sendConsoleMessage(String message){
        Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(PlayerKits2.prefix+message));
    }

    /**
     * Creates the arrange inventory on configs made by older versions of the plugin.
     */
    public void checkArrangeInventory(){
        FileConfiguration config = configFile.getConfig();
        String path = "inventories."+InventoryArrangeManager.INVENTORY_NAME;
        if(config.contains(path)){
            return;
        }

        boolean legacy = OtherUtils.isLegacy();
        config.set(path+".slots",54);
        config.set(path+".title","&8&lArrange kit: &7%kit%");

        config.set(path+".45.item.id",legacy ? "STAINED_GLASS_PANE:14" : "RED_STAINED_GLASS_PANE");
        config.set(path+".45.item.name","&e&lREVERT &8(&7/kits&8)");
        config.set(path+".45.item.lore",Arrays.asList(
                "&8[&5PLAYER KITS&8]",
                "",
                "&aDescription:",
                "&5| &aClick to reset the",
                "&5| &akit to its default &esetting!",
                "",
                "&8▸ &e&lCLICK &eto Revert"));
        config.set(path+".45.type","arrange_revert");

        config.set(path+".52.item.id",legacy ? "CHEST" : "BARREL");
        config.set(path+".52.item.name","&c&lBACK &8(&7/kits&8)");
        config.set(path+".52.item.lore",Arrays.asList(
                "&8[&5PLAYER KITS&8]",
                "",
                "&cDescription:",
                "&5| &cExit this menu to",
                "&5| &chave no changes &4saved!",
                "",
                "&8▸ &c&lCLICK &cto Return"));
        config.set(path+".52.open_inventory","previous");

        config.set(path+".53.item.id",legacy ? "STAINED_GLASS_PANE:5" : "LIME_STAINED_GLASS_PANE");
        config.set(path+".53.item.name","&a&lSAVE &8(&7/kits&8)");
        config.set(path+".53.item.lore",Arrays.asList(
                "&8[&5PLAYER KITS&8]",
                "",
                "&aDescription:",
                "&5| &aClick to save your",
                "&5| &acurrent kit &earrangement!",
                "",
                "&8▸ &e&lCLICK &eto Save"));
        config.set(path+".53.type","arrange_save");

        addArrangeItemToPreviewInventory(config,legacy);

        configFile.saveConfig();
    }

    /**
     * Adds the item used to open the arrange inventory, on the last slot of the preview one.
     */
    private void addArrangeItemToPreviewInventory(FileConfiguration config,boolean legacy){
        String previewPath = "inventories."+InventoryManager.PREVIEW_INVENTORY_NAME;
        if(!config.contains(previewPath)){
            return;
        }

        int slot = config.getInt(previewPath+".slots")-1;
        if(slot < 0 || isSlotUsed(config,previewPath,slot)){
            return;
        }

        config.set(previewPath+"."+slot+".item.id",legacy ? "WORKBENCH" : "CRAFTING_TABLE");
        config.set(previewPath+"."+slot+".item.name","&e&lARRANGE &8(&7/kits&8)");
        config.set(previewPath+"."+slot+".item.lore",Arrays.asList(
                "&8[&5PLAYER KITS&8]",
                "",
                "&aDescription:",
                "&5| &aChoose the position of",
                "&5| &aevery item of the &ekit!",
                "",
                "&8▸ &e&lCLICK &eto Arrange"));
        config.set(previewPath+"."+slot+".open_inventory",InventoryArrangeManager.INVENTORY_NAME);
    }

    /**
     * Adds the items to save and revert the arrangement to the preview inventory, needed
     * by the configs made before the kit could be arranged directly on it. Both items are
     * only shown when they are needed, so they can be left on the config of any server.
     */
    public void addPreviewArrangeItems(){
        FileConfiguration config = configFile.getConfig();
        String previewPath = "inventories."+InventoryManager.PREVIEW_INVENTORY_NAME;
        if(!config.contains(previewPath)){
            return;
        }

        boolean legacy = OtherUtils.isLegacy();
        int slots = config.getInt(previewPath+".slots");

        //Revert item, on the middle of the last row.
        boolean revertAdded = addArrangeItem(config,previewPath,slots-5,
                legacy ? "STAINED_GLASS_PANE:14" : "RED_STAINED_GLASS_PANE",
                "&e&lREVERT &8(&7/kits&8)",Arrays.asList(
                        "&8[&5PLAYER KITS&8]",
                        "",
                        "&aDescription:",
                        "&5| &aClick to reset the",
                        "&5| &akit to its default &esetting!",
                        "",
                        "&8▸ &e&lCLICK &eto Revert"),
                InventoryArrangeManager.REVERT_TYPE);

        //Save item, only shown when the changes are not saved on close.
        boolean saveAdded = addArrangeItem(config,previewPath,slots-2,
                legacy ? "STAINED_GLASS_PANE:5" : "LIME_STAINED_GLASS_PANE",
                "&a&lSAVE &8(&7/kits&8)",Arrays.asList(
                        "&8[&5PLAYER KITS&8]",
                        "",
                        "&aDescription:",
                        "&5| &aClick to save your",
                        "&5| &acurrent kit &earrangement!",
                        "",
                        "&8▸ &e&lCLICK &eto Save"),
                InventoryArrangeManager.SAVE_TYPE);

        if(revertAdded || saveAdded){
            configFile.saveConfig();
        }
    }

    /**
     * Adds an item of the arrangement on a slot, doing nothing if that slot is already used.
     * Returns true if the item was added.
     */
    private boolean addArrangeItem(FileConfiguration config,String inventoryPath,int slot,
                                   String id,String name,List<String> lore,String type){
        if(slot < 0 || isSlotUsed(config,inventoryPath,slot)){
            return false;
        }

        String path = inventoryPath+"."+slot;
        config.set(path+".item.id",id);
        config.set(path+".item.name",name);
        config.set(path+".item.lore",lore);
        config.set(path+".type",type);
        return true;
    }

    private boolean isSlotUsed(FileConfiguration config,String inventoryPath,int slot){
        ConfigurationSection section = config.getConfigurationSection(inventoryPath);
        if(section == null){
            return true;
        }

        for(String key : section.getKeys(false)){
            if(key.equals("slots") || key.equals("title")){
                continue;
            }

            for(String slotString : key.split(";")){
                try{
                    if(slotString.contains("-")){
                        String[] range = slotString.split("-");
                        if(slot >= Integer.parseInt(range[0]) && slot <= Integer.parseInt(range[1])){
                            return true;
                        }
                    }else if(Integer.parseInt(slotString) == slot){
                        return true;
                    }
                }catch(NumberFormatException e){
                    //Invalid slot, it is checked by the verify manager.
                }
            }
        }
        return false;
    }

    public void configure(){
        FileConfiguration config = configFile.getConfig();

        ArrayList<KitInventory> inventories = new ArrayList<KitInventory>();
        KitItemManager kitItemManager = plugin.getKitItemManager();
        if(config.contains("inventories")) {
            for(String key : config.getConfigurationSection("inventories").getKeys(false)) {
                int slots = config.getInt("inventories."+key+".slots");
                String title = config.getString("inventories."+key+".title");

                List<ItemKitInventory> items = new ArrayList<>();
                for(String slotString : config.getConfigurationSection("inventories."+key).getKeys(false)) {
                    if(!slotString.equals("slots") && !slotString.equals("title")) {
                        String path = "inventories."+key+"."+slotString;
                        KitItem item = null;
                        if(config.contains(path+".item")){
                            item = kitItemManager.getKitItemFromConfig(config, path+".item");
                        }

                        String openInventory = config.contains(path+".open_inventory") ?
                                config.getString(path+".open_inventory") : null;

                        List<String> clickActions = config.contains(path+".click_actions") ?
                                config.getStringList(path+".click_actions") : null;

                        String type = config.contains(path+".type") ?
                                config.getString(path+".type") : null;

                        ItemKitInventory itemCraft = new ItemKitInventory(slotString,item,openInventory,clickActions,type);
                        items.add(itemCraft);
                    }
                }

                KitInventory inv = new KitInventory(key,slots,title,items);
                inventories.add(inv);
            }
        }

        plugin.getInventoryManager().setInventories(inventories);
    }

    public void saveKitItemOnConfig(String inventoryName,int slot,String kitName){
        FileConfiguration config = configFile.getConfig();
        config.set("inventories."+inventoryName+"."+slot+".type","kit: "+kitName);

        configFile.saveConfig();
    }

    public void save(){
        FileConfiguration config = configFile.getConfig();
        config.set("inventories",null);

        KitItemManager kitItemManager = plugin.getKitItemManager();
        for(KitInventory kitInventory : plugin.getInventoryManager().getInventories()){
            String name = kitInventory.getName();
            config.set("inventories."+name+".slots",kitInventory.getSlots());
            config.set("inventories."+name+".title",kitInventory.getTitle());
            for(ItemKitInventory item : kitInventory.getItems()){
                String slotsString = item.getSlotsString();
                String path = "inventories."+name+"."+slotsString;
                if(item.getType() != null){
                    config.set(path+".type",item.getType());
                }
                if(item.getItem() != null){
                    kitItemManager.saveKitItemOnConfig(item.getItem(),config,path+".item");
                }
                if(item.getOpenInventory() != null){
                    config.set(path+".open_inventory",item.getOpenInventory());
                }
                if(item.getClickActions() != null && !item.getClickActions().isEmpty()){
                    config.set(path+".click_actions",item.getClickActions());
                }
            }
        }

        configFile.saveConfig();
    }

    public boolean reloadConfig(){
        if(!configFile.reloadConfig()){
            return false;
        }
        //The inventories needed by the plugin are also restored on reload, so a mistake
        //while editing the file doesn't leave the plugin unusable until the next restart.
        checkDefaultInventories();
        checkArrangeInventory();
        configure();
        return true;
    }

    public FileConfiguration getConfig(){
        return configFile.getConfig();
    }
}
