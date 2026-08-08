package pk.ajneb97.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.MainConfigManager;
import pk.ajneb97.model.Kit;
import pk.ajneb97.model.internal.GiveKitInstructions;
import pk.ajneb97.model.internal.KitPosition;
import pk.ajneb97.model.internal.KitVariable;
import pk.ajneb97.model.internal.PlayerKitsMessageResult;
import pk.ajneb97.model.inventory.ArrangeSession;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.model.inventory.ItemKitInventory;
import pk.ajneb97.model.inventory.KitInventory;
import pk.ajneb97.model.item.KitItem;
import pk.ajneb97.utils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InventoryManager {

    public static final String MAIN_INVENTORY_NAME = "main_inventory";
    public static final String PREVIEW_INVENTORY_NAME = "preview_inventory";
    public static final String BUY_REQUIREMENTS_INVENTORY_NAME = "buy_requirements_inventory";

    /**
     * Inventories the plugin can't work without, so they are restored with their default
     * values when they are not found on the inventory.yml file.
     */
    public static final List<String> REQUIRED_INVENTORY_NAMES = Arrays.asList(
            MAIN_INVENTORY_NAME,PREVIEW_INVENTORY_NAME,BUY_REQUIREMENTS_INVENTORY_NAME);

    private PlayerKits2 plugin;
    private ArrayList<KitInventory> inventories;
    private ArrayList<InventoryPlayer> players;
    private InventoryRequirementsManager inventoryRequirementsManager;
    private InventoryArrangeManager inventoryArrangeManager;
    public InventoryManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.players = new ArrayList<>();
        this.inventoryRequirementsManager = new InventoryRequirementsManager(plugin,this);
        this.inventoryArrangeManager = new InventoryArrangeManager(plugin,this);
    }

    public ArrayList<InventoryPlayer> getPlayers() {
        return players;
    }

    public ArrayList<KitInventory> getInventories() {
        return inventories;
    }

    public InventoryRequirementsManager getInventoryRequirementsManager() {
        return inventoryRequirementsManager;
    }

    public InventoryArrangeManager getInventoryArrangeManager() {
        return inventoryArrangeManager;
    }

    public void setInventories(ArrayList<KitInventory> inventories) {
        this.inventories = inventories;
    }

    public KitInventory getInventory(String name){
        for(KitInventory kitInventory : inventories){
            if(kitInventory.getName().equals(name)){
                return kitInventory;
            }
        }
        return null;
    }

    public InventoryPlayer getInventoryPlayer(Player player){
        for(InventoryPlayer inventoryPlayer : players){
            if(inventoryPlayer.getPlayer().equals(player)){
                return inventoryPlayer;
            }
        }
        return null;
    }

    public void removeInventoryPlayer(Player player){
        for(int i=0;i<players.size();i++){
            if(players.get(i).getPlayer().equals(player)){
                players.remove(i);
            }
        }
    }

    public void openInventory(InventoryPlayer inventoryPlayer){
        String inventoryName = inventoryPlayer.getInventoryName();
        KitInventory kitInventory = getInventory(inventoryName);
        if(kitInventory == null){
            //The inventory is not on the config, so it can't be opened. This is already
            //reported by the verify manager, the player is only notified here.
            Player player = inventoryPlayer.getPlayer();
            plugin.getMessagesManager().sendMessage(player,plugin.getConfigsManager().getMessagesConfigManager()
                    .getConfig().getString("pluginCriticalErrors"),true);
            player.closeInventory();
            return;
        }
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();

        String title = kitInventory.getTitle();
        if(inventoryPlayer.getKitName() != null && (inventoryName.equals(BUY_REQUIREMENTS_INVENTORY_NAME)
                || inventoryName.equals(PREVIEW_INVENTORY_NAME) || inventoryName.equals(InventoryArrangeManager.INVENTORY_NAME))){
            title = title.replace("%kit%",inventoryPlayer.getKitName());
        }
        Inventory inv;
        if(mainConfigManager.isUseMiniMessage()){
            inv = MiniMessageUtils.createInventory(kitInventory.getSlots(),title);
        }else{
            inv = Bukkit.createInventory(null,kitInventory.getSlots(), MessagesManager.getLegacyColoredMessage(title));
        }

        List<ItemKitInventory> items = kitInventory.getItems();
        KitItemManager kitItemManager = plugin.getKitItemManager();
        KitsManager kitsManager = plugin.getKitsManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();

        //The items of the kit can be moved on the arrange inventory, and also directly
        //on the preview one if that is enabled and the player can arrange the kit.
        boolean arrangePreview = inventoryName.equals(PREVIEW_INVENTORY_NAME)
                && inventoryArrangeManager.isEnabledOnPreview()
                && inventoryArrangeManager.canArrangeKit(inventoryPlayer.getPlayer(),inventoryPlayer.getKitName());
        boolean arrangeable = arrangePreview || inventoryName.equals(InventoryArrangeManager.INVENTORY_NAME);
        boolean saveOnClose = arrangePreview && inventoryArrangeManager.isAutoSave();

        //Add items for all inventories
        for(ItemKitInventory itemInventory : items){
            for(int slot : itemInventory.getSlots()){
                String type = itemInventory.getType();
                if(type != null && type.startsWith("kit: ")){
                    setKit(type.replace("kit: ",""),inventoryPlayer.getPlayer(),inv,slot,kitsManager
                            ,playerDataManager,kitItemManager,null);
                    continue;
                }

                if(inventoryArrangeManager.isArrangeItemHidden(type,arrangeable,saveOnClose)){
                    //The items to save or revert the arrangement are only shown when they are needed.
                    continue;
                }

                ItemStack item = kitItemManager.createItemFromKitItem(itemInventory.getItem(),inventoryPlayer.getPlayer(),null);

                if(inventoryName.equals(BUY_REQUIREMENTS_INVENTORY_NAME)){
                    inventoryRequirementsManager.configureRequirementsItem(item,inventoryPlayer.getKitName(),inventoryPlayer.getPlayer());
                    if(type != null){
                        item = ItemUtils.setTagStringItem(plugin,item, "playerkits_buy", type);
                    }
                }else if(InventoryArrangeManager.isArrangeType(type)){
                    item = ItemUtils.setTagStringItem(plugin,item, "playerkits_arrange", type);
                }

                String openInventory = itemInventory.getOpenInventory();
                if(openInventory != null) {
                    if(openInventory.equals(InventoryArrangeManager.INVENTORY_NAME)
                            && (!inventoryArrangeManager.isEnabled() || inventoryArrangeManager.isEnabledOnPreview())){
                        //The item to open the arrange inventory is hidden if the feature is
                        //disabled or if the items can already be moved on the preview.
                        continue;
                    }
                    item = ItemUtils.setTagStringItem(plugin,item, "playerkits_open_inventory", openInventory);
                }
                item = setItemActions(itemInventory,item);

                inv.setItem(slot,item);
            }
        }

        //Special items for some inventories
        ArrangeSession arrangeSession = null;
        if(arrangeable){
            arrangeSession = inventoryArrangeManager.setKitArrangeItems(inv,inventoryPlayer,saveOnClose);
        }
        if(arrangeSession == null && inventoryName.equals(PREVIEW_INVENTORY_NAME)){
            setKitPreviewItems(inv,inventoryPlayer);
        }


        //Opening the inventory closes the previous one, so the session is set after it.
        inventoryPlayer.getPlayer().openInventory(inv);
        inventoryPlayer.setArrangeSession(arrangeSession);
        players.add(inventoryPlayer);
    }

    private ItemStack setItemActions(ItemKitInventory itemInventory, ItemStack item) {
        List<String> clickActions = itemInventory.getClickActions();
        if(clickActions != null && !clickActions.isEmpty()) {
            String actionsList = "";
            for(int i=0;i<clickActions.size();i++) {
                if(i==clickActions.size()-1) {
                    actionsList=actionsList+clickActions.get(i);
                }else {
                    actionsList=actionsList+clickActions.get(i)+"|";
                }
            }
            item = ItemUtils.setTagStringItem(plugin, item, "playerkits_item_actions", actionsList);
        }
        return item;
    }

    public void setKitPreviewItems(Inventory inv,InventoryPlayer inventoryPlayer){
        Kit kit = plugin.getKitsManager().getKitByName(inventoryPlayer.getKitName());
        if(kit == null){
            return;
        }

        // All items, including actions display items, on the slots arranged by the player
        Player player = inventoryPlayer.getPlayer();
        List<KitItem> allItems = InventoryArrangeManager.getAllKitItems(kit);
        inventoryArrangeManager.layoutKitItems(inv,player,kit,allItems,
                inventoryArrangeManager.getArrangement(player,kit.getName()),
                InventoryArrangeManager.getReservedSlots(inv),false);
    }

    public void clickInventory(InventoryPlayer inventoryPlayer, ItemStack item, ClickType clickType){
        String kitName = ItemUtils.getTagStringItem(plugin,item,"playerkits_kit");
        if(kitName != null){
            clickOnKitItem(inventoryPlayer,kitName,clickType);
            return;
        }

        String itemActions = ItemUtils.getTagStringItem(plugin,item,"playerkits_item_actions");
        if(itemActions != null){
            clickOnActionItem(inventoryPlayer,itemActions);
        }

        String openInventory = ItemUtils.getTagStringItem(plugin,item,"playerkits_open_inventory");
        if(openInventory != null){
            clickOnOpenInventoryItem(inventoryPlayer,openInventory);
            return;
        }

        //Requirements inventory
        if(inventoryPlayer.getInventoryName().equals(BUY_REQUIREMENTS_INVENTORY_NAME)){
            String buyTag = ItemUtils.getTagStringItem(plugin,item,"playerkits_buy");
            if(buyTag == null){
                return;
            }
            if(buyTag.equals("yes") || buyTag.equals("buy_yes")){
                inventoryRequirementsManager.requirementsInventoryBuy(inventoryPlayer);
            }else{
                inventoryRequirementsManager.requirementsInventoryCancel(inventoryPlayer);
            }
        }
    }

    public void clickOnKitItem(InventoryPlayer inventoryPlayer,String kitName,ClickType clickType){
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();
        Player player = inventoryPlayer.getPlayer();
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        MessagesManager msgManager = plugin.getMessagesManager();

        if(clickType.equals(ClickType.RIGHT)){
            //Preview
            if(!mainConfigManager.isKitPreview()){
                return;
            }

            Kit kit = plugin.getKitsManager().getKitByName(kitName);
            if(kit.isPermissionRequired()){
                if(mainConfigManager.isKitPreviewRequiresKitPermission() && !kit.playerHasPermission(player)){
                    msgManager.sendMessage(player,messagesConfig.getString("cantPreviewError"),true);
                    return;
                }
            }
            inventoryPlayer.setPreviousInventoryName(inventoryPlayer.getInventoryName());
            inventoryPlayer.setInventoryName(PREVIEW_INVENTORY_NAME);
            inventoryPlayer.setKitName(kitName);
            openInventory(inventoryPlayer);
            return;
        }


        PlayerKitsMessageResult result = plugin.getKitsManager().giveKit(player,kitName,
                new GiveKitInstructions());
        if(result.isError()){
            msgManager.sendMessage(player,result.getMessage(),true);
            return;
        }else{
            if(result.isProceedToBuy()){
                //Open requirements inventory
                inventoryPlayer.setPreviousInventoryName(inventoryPlayer.getInventoryName());
                inventoryPlayer.setInventoryName(BUY_REQUIREMENTS_INVENTORY_NAME);
                inventoryPlayer.setKitName(kitName);
                openInventory(inventoryPlayer);
                return;
            }
            msgManager.sendMessage(player,messagesConfig.getString("kitReceived").replace("%kit%",kitName),true);
        }

        if(mainConfigManager.isCloseInventoryOnClaim()){
            player.closeInventory();
            return;
        }

        openInventory(inventoryPlayer);
    }

    public void clickOnOpenInventoryItem(InventoryPlayer inventoryPlayer,String openInventory){
        if(openInventory.equals("previous")){
            String previousInventory = inventoryPlayer.removePreviousInventoryName();
            if(previousInventory == null || getInventory(previousInventory) == null){
                inventoryPlayer.getPlayer().closeInventory();
                return;
            }
            inventoryPlayer.setInventoryName(previousInventory);
        }else{
            if(openInventory.equals(InventoryArrangeManager.INVENTORY_NAME)
                    && !inventoryArrangeManager.canArrange(inventoryPlayer)){
                return;
            }
            inventoryPlayer.setPreviousInventoryName(inventoryPlayer.getInventoryName());
            inventoryPlayer.setInventoryName(openInventory);
        }
        openInventory(inventoryPlayer);
    }

    public void clickOnActionItem(InventoryPlayer inventoryPlayer,String itemActions) {
        String[] sep = itemActions.split("\\|");

        KitsManager kitsManager = plugin.getKitsManager();
        Player player = inventoryPlayer.getPlayer();
        for(String action : sep){
            kitsManager.executeAction(player,action);
        }
    }

    public void setKit(String kitName, Player player, Inventory inv, int slot, KitsManager kitsManager, PlayerDataManager playerDataManager
        ,KitItemManager kitItemManager,ItemStack currentItem){
        Kit kit = kitsManager.getKitByName(kitName);
        if(kit == null){
            return;
        }

        // Check status before
        String currentStatus = "default";
        if(currentItem != null){
            currentStatus = ItemUtils.getTagStringItem(plugin,currentItem,"playerkits_kit_status");
            if(currentStatus == null){
                currentStatus = "default";
            }
        }
        String newStatus = "default";

        ArrayList<KitVariable> variablesToReplace = new ArrayList<>();
        variablesToReplace.add(new KitVariable("%kit_name%",kit.getName()));

        KitItem kitItem = null;
        if(!kit.playerHasPermission(player)){
            kitItem = kit.getDisplayItemNoPermission();
            newStatus = "no_permission";
        }else{
            //One time
            if(kit.isOneTime() && !PlayerUtils.isPlayerKitsAdmin(player) && playerDataManager.isKitOneTime(player,kit.getName())){
                kitItem = kit.getDisplayItemOneTime();
                newStatus = "one_time";
            }

            //One time requirements
            if(kit.getRequirements() != null && kit.getRequirements().isOneTimeRequirements()
                && playerDataManager.isKitBought(player,kit.getName())){
                kitItem = kit.getDisplayItemOneTimeRequirements();
                newStatus = "one_time_requirements";
            }

            //Cooldown
            long playerCooldown = playerDataManager.getKitCooldown(player,kit.getName());
            if(kit.getCooldown() != 0 && !PlayerUtils.isPlayerKitsAdmin(player)){
                String timeStringMillisDif = playerDataManager.getKitCooldownString(playerCooldown);
                if(!timeStringMillisDif.isEmpty()) {
                    kitItem = kit.getDisplayItemCooldown();
                    variablesToReplace.add(new KitVariable("%time%",timeStringMillisDif));
                    newStatus = "cooldown";
                }
            }
        }
        if(kitItem == null){
            kitItem = kit.getDisplayItemDefault();
            newStatus = "default";
        }

        kitItem = kitItem.clone();

        boolean useMiniMessage = plugin.getConfigsManager().getMainConfigManager().isUseMiniMessage();
        if(newStatus.equals(currentStatus) && currentItem != null){
            // Name and Lore update
            kitItemManager.replaceVariables(kitItem,variablesToReplace,player);

            ItemMeta meta = currentItem.getItemMeta();

            String name = kitItem.getName();
            if(name != null){
                if(useMiniMessage){
                    MiniMessageUtils.setItemName(meta,name);
                }else{
                    meta.setDisplayName(MessagesManager.getLegacyColoredMessage(name));
                }
            }

            List<String> lore = kitItem.getLore();
            if(lore != null) {
                List<String> loreCopy = new ArrayList<>(lore);
                if(useMiniMessage){
                    MiniMessageUtils.setItemLore(meta,lore,player,plugin);
                }else{
                    for(int i=0;i<loreCopy.size();i++) {
                        String line = OtherUtils.replaceGlobalVariables(loreCopy.get(i),player,plugin);
                        loreCopy.set(i, MessagesManager.getLegacyColoredMessage(line));
                    }
                    meta.setLore(loreCopy);
                }
            }

            currentItem.setItemMeta(meta);
        }else{
            // Full update
            kitItemManager.replaceVariables(kitItem,variablesToReplace,player);
            ItemStack item = kitItemManager.createItemFromKitItem(kitItem,player,kit);
            item = ItemUtils.setTagStringItem(plugin,item, "playerkits_kit", kitName);
            item = ItemUtils.setTagStringItem(plugin,item, "playerkits_kit_status", newStatus);
            inv.setItem(slot,item);
        }
    }



    public KitPosition getKitPositionByKitName(String kitName){
        for(KitInventory kitInventory : inventories){
            for(ItemKitInventory itemKitInventory : kitInventory.getItems()){
                if(itemKitInventory.getType() != null && itemKitInventory.getType().equals("kit: "+kitName)){
                    return new KitPosition(itemKitInventory.getSlots().get(0), kitInventory.getName());
                }
            }
        }
        return null;
    }

    public void removeKitFromInventory(String kitName){
        for(KitInventory inventory : inventories){
            List<ItemKitInventory> items = inventory.getItems();
            for(int i=0;i<items.size();i++){
                ItemKitInventory item = items.get(i);
                if(item.getType() != null && item.getType().equals("kit: "+kitName)){
                    items.remove(i);
                    i--;
                }
            }
        }
    }
}
