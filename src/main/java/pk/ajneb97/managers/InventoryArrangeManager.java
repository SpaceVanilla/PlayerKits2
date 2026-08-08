package pk.ajneb97.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.MainConfigManager;
import pk.ajneb97.model.Kit;
import pk.ajneb97.model.KitAction;
import pk.ajneb97.model.KitArrangement;
import pk.ajneb97.model.inventory.ArrangeSession;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.model.item.KitItem;
import pk.ajneb97.utils.InventoryUtils;
import pk.ajneb97.utils.ItemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the arrange inventory, where a player can move the items of a kit to
 * decide how the kit is shown on the preview and in which order it is received.
 * Items can only be moved between the slots of this inventory, they can never
 * be taken from it or added to it.
 */
public class InventoryArrangeManager {

    public static final String INVENTORY_NAME = "arrange_inventory";

    private static final String SAVE_TYPE = "arrange_save";
    private static final String REVERT_TYPE = "arrange_revert";

    private PlayerKits2 plugin;
    private InventoryManager inventoryManager;

    public InventoryArrangeManager(PlayerKits2 plugin, InventoryManager inventoryManager){
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
    }

    public boolean isEnabled(){
        return plugin.getConfigsManager().getMainConfigManager().isKitArrangement();
    }

    /**
     * Checks if the player can arrange the kit of the inventory, sending the
     * corresponding error message if it can't.
     */
    public boolean canArrange(InventoryPlayer inventoryPlayer){
        Player player = inventoryPlayer.getPlayer();
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        MessagesManager msgManager = plugin.getMessagesManager();

        if(!isEnabled()){
            msgManager.sendMessage(player,messagesConfig.getString("kitArrangementDisabled"),true);
            return false;
        }

        if(inventoryManager.getInventory(INVENTORY_NAME) == null){
            msgManager.sendMessage(player,messagesConfig.getString("inventoryNotExists"),true);
            return false;
        }

        Kit kit = plugin.getKitsManager().getKitByName(inventoryPlayer.getKitName());
        if(kit == null){
            msgManager.sendMessage(player,messagesConfig.getString("kitDoesNotExists")
                    .replace("%kit%",String.valueOf(inventoryPlayer.getKitName())),true);
            return false;
        }

        if(!kit.playerHasPermission(player)){
            msgManager.sendMessage(player,messagesConfig.getString("cantArrangeError"),true);
            return false;
        }

        return true;
    }

    /**
     * Arrangement saved by the player, null if there is none or if the feature is disabled.
     */
    public KitArrangement getArrangement(Player player, String kitName){
        if(!isEnabled()){
            return null;
        }
        return plugin.getPlayerDataManager().getKitArrangement(player,kitName);
    }

    /**
     * All the items shown for a kit: the items of the kit and the display items of its actions.
     */
    public static List<KitItem> getAllKitItems(Kit kit){
        List<KitItem> allItems = new ArrayList<>(kit.getItems());
        for(KitAction kitAction : kit.getClaimActions()){
            KitItem kitItem = kitAction.getDisplayItem();
            if(kitItem != null){
                allItems.add(kitItem);
            }
        }
        return allItems;
    }

    /**
     * Slots already used by the items of the menu, they can't be used by kit items.
     * It must be called before placing the items of the kit.
     */
    public static boolean[] getReservedSlots(Inventory inv){
        boolean[] reservedSlots = new boolean[inv.getSize()];
        for(int slot=0;slot<reservedSlots.length;slot++){
            ItemStack item = inv.getItem(slot);
            reservedSlots[slot] = item != null && !item.getType().equals(Material.AIR);
        }
        return reservedSlots;
    }

    /**
     * Places the items of a kit, using the arrangement of the player if it has one.
     * Returns the kit item position used on every slot of the inventory.
     *
     * @param reservedLocked if true, items are never placed on the slots of the menu items.
     */
    public int[] layoutKitItems(Inventory inv, Player player, Kit kit, List<KitItem> allItems,
                                KitArrangement arrangement, boolean[] reservedSlots, boolean reservedLocked){
        KitItemManager kitItemManager = plugin.getKitItemManager();
        int slots = inv.getSize();
        int amount = allItems.size();

        int[] slotItems = new int[slots];
        Arrays.fill(slotItems,-1);
        boolean[] placedItems = new boolean[amount];

        //Items with a fixed slot: the one of the player arrangement or the one of the config.
        for(int i=0;i<amount;i++){
            KitItem kitItem = allItems.get(i);
            int slot = arrangement != null ? arrangement.getSlot(i) : -1;
            boolean fromArrangement = slot != -1;
            if(!fromArrangement){
                slot = kitItem.getPreviewSlot();
            }

            if(slot < 0 || slot >= slots || slotItems[slot] != -1){
                continue;
            }
            if(reservedSlots[slot] && (fromArrangement || reservedLocked)){
                continue;
            }

            inv.setItem(slot,kitItemManager.createItemFromKitItem(kitItem,player,kit));
            slotItems[slot] = i;
            placedItems[i] = true;
        }

        //Remaining items, on the first free slots.
        int slot = 0;
        for(int i=0;i<amount;i++){
            if(placedItems[i]){
                continue;
            }

            while(slot < slots && (slotItems[slot] != -1 || reservedSlots[slot])){
                slot++;
            }
            if(slot >= slots){
                break;
            }

            inv.setItem(slot,kitItemManager.createItemFromKitItem(allItems.get(i),player,kit));
            slotItems[slot] = i;
        }

        return slotItems;
    }

    /**
     * Places the kit items on the arrange inventory and creates the session of the player.
     */
    public ArrangeSession setKitArrangeItems(Inventory inv, InventoryPlayer inventoryPlayer){
        Kit kit = plugin.getKitsManager().getKitByName(inventoryPlayer.getKitName());
        if(kit == null){
            return null;
        }

        Player player = inventoryPlayer.getPlayer();
        List<KitItem> allItems = getAllKitItems(kit);
        boolean[] reservedSlots = getReservedSlots(inv);
        KitArrangement arrangement = getArrangement(player,kit.getName());

        int[] slotItems = layoutKitItems(inv,player,kit,allItems,arrangement,reservedSlots,true);
        return new ArrangeSession(slotItems,reservedSlots,allItems.size());
    }

    public void clickInventory(InventoryPlayer inventoryPlayer, InventoryClickEvent event){
        //Nothing is ever moved by the server, this manager does every change.
        event.setCancelled(true);

        ArrangeSession session = inventoryPlayer.getArrangeSession();
        if(session == null){
            return;
        }

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();
        if(slot < 0 || slot >= inv.getSize()){
            //Clicks on the inventory of the player or outside of the menu.
            return;
        }

        if(session.isReserved(slot)){
            if(session.getCursorItem() != -1){
                //The client leaves the item it is carrying on the button, it must be updated back.
                updateInventoryLater(inventoryPlayer.getPlayer());
            }
            clickOnMenuItem(inventoryPlayer,inv.getItem(slot));
            return;
        }

        ClickType clickType = event.getClick();
        if(!clickType.equals(ClickType.LEFT) && !clickType.equals(ClickType.RIGHT)){
            //Shift clicks, number keys, drops and swaps would move items in or out of the menu.
            return;
        }

        moveItem(inventoryPlayer,session,inv,slot);
    }

    /**
     * Takes the item of the slot with the cursor, or leaves the one of the cursor on it.
     */
    private void moveItem(InventoryPlayer inventoryPlayer, ArrangeSession session, Inventory inv, int slot){
        Player player = inventoryPlayer.getPlayer();
        int cursorItem = session.getCursorItem();
        int slotItem = session.getSlotItem(slot);

        if(cursorItem == -1){
            if(slotItem == -1){
                return;
            }

            //Take the item of the slot.
            ItemStack item = inv.getItem(slot);
            if(item == null || item.getType().equals(Material.AIR)){
                session.setSlotItem(slot,-1);
                return;
            }

            inv.setItem(slot,null);
            session.setSlotItem(slot,-1);
            session.setCursorItem(slotItem);
            player.setItemOnCursor(item.clone());
        }else{
            ItemStack item = player.getItemOnCursor();
            if(item == null || item.getType().equals(Material.AIR)){
                //The item was lost, the session is restored to avoid duplicating items.
                session.setCursorItem(-1);
                updateInventoryLater(player);
                return;
            }

            //Leave the item of the cursor and take the one of the slot (if there is one).
            ItemStack previousItem = inv.getItem(slot);
            inv.setItem(slot,item);
            session.setSlotItem(slot,cursorItem);
            session.setCursorItem(slotItem);
            player.setItemOnCursor(previousItem != null ? previousItem.clone() : null);
        }

        updateInventoryLater(player);
    }

    private void clickOnMenuItem(InventoryPlayer inventoryPlayer, ItemStack item){
        if(item == null || item.getType().equals(Material.AIR)){
            return;
        }

        String itemActions = ItemUtils.getTagStringItem(plugin,item,"playerkits_item_actions");
        if(itemActions != null){
            inventoryManager.clickOnActionItem(inventoryPlayer,itemActions);
        }

        String type = ItemUtils.getTagStringItem(plugin,item,"playerkits_arrange");
        if(type != null){
            if(type.equals(SAVE_TYPE)){
                save(inventoryPlayer);
            }else if(type.equals(REVERT_TYPE)){
                revert(inventoryPlayer);
            }
            return;
        }

        String openInventory = ItemUtils.getTagStringItem(plugin,item,"playerkits_open_inventory");
        if(openInventory != null){
            inventoryManager.clickOnOpenInventoryItem(inventoryPlayer,openInventory);
        }
    }

    /**
     * Saves the current position of every item of the kit for the player.
     */
    public void save(InventoryPlayer inventoryPlayer){
        ArrangeSession session = inventoryPlayer.getArrangeSession();
        Player player = inventoryPlayer.getPlayer();
        if(session == null){
            return;
        }

        returnCursorItem(player,session,InventoryUtils.getTopInventory(player));

        int[] slotItems = session.getSlotItems();
        int[] slots = new int[session.getItemsAmount()];
        Arrays.fill(slots,-1);
        for(int slot=0;slot<slotItems.length;slot++){
            int itemPosition = slotItems[slot];
            if(itemPosition >= 0 && itemPosition < slots.length){
                slots[itemPosition] = slot;
            }
        }

        String kitName = inventoryPlayer.getKitName();
        plugin.getPlayerDataManager().setKitArrangement(player,kitName,new KitArrangement(slots));

        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        plugin.getMessagesManager().sendMessage(player,messagesConfig.getString("kitArrangementSaved")
                .replace("%kit%",kitName),true);
    }

    /**
     * Removes the arrangement of the player, going back to the default one of the kit.
     */
    public void revert(InventoryPlayer inventoryPlayer){
        Player player = inventoryPlayer.getPlayer();
        String kitName = inventoryPlayer.getKitName();
        plugin.getPlayerDataManager().setKitArrangement(player,kitName,null);

        //Opening the inventory again clears the cursor and places the items on their default slots.
        inventoryManager.openInventory(inventoryPlayer);

        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        plugin.getMessagesManager().sendMessage(player,messagesConfig.getString("kitArrangementReverted")
                .replace("%kit%",kitName),true);
    }

    /**
     * Called when the arrange inventory is closed. The item on the cursor is a preview
     * item, so it is removed instead of being dropped by the server.
     */
    public void closeInventory(InventoryPlayer inventoryPlayer){
        ArrangeSession session = inventoryPlayer.getArrangeSession();
        inventoryPlayer.setArrangeSession(null);
        if(session == null || session.getCursorItem() == -1){
            return;
        }

        Player player = inventoryPlayer.getPlayer();
        session.setCursorItem(-1);
        player.setItemOnCursor(null);
    }

    /**
     * Places the item on the cursor back on the inventory, on the first free slot.
     */
    private void returnCursorItem(Player player, ArrangeSession session, Inventory inv){
        int cursorItem = session.getCursorItem();
        if(cursorItem == -1){
            return;
        }

        ItemStack item = player.getItemOnCursor();
        player.setItemOnCursor(null);
        session.setCursorItem(-1);
        if(inv == null || item == null || item.getType().equals(Material.AIR)){
            return;
        }

        int slot = session.getFirstFreeSlot();
        if(slot == -1){
            return;
        }
        inv.setItem(slot,item);
        session.setSlotItem(slot,cursorItem);
        updateInventoryLater(player);
    }

    /**
     * The click event is cancelled, so the client is updated with the changes made here.
     */
    private void updateInventoryLater(final Player player){
        new BukkitRunnable(){
            @Override
            public void run() {
                player.updateInventory();
            }
        }.runTaskLater(plugin,1L);
    }

    /**
     * Items of the kit, sorted by the slots chosen by the player, so the kit is
     * received on the same order it was arranged. The items of the kit are
     * returned without changes if the player has no arrangement for it.
     */
    public List<KitItem> getArrangedItems(Player player, Kit kit){
        ArrayList<KitItem> items = kit.getItems();
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();
        if(!mainConfigManager.isKitArrangement() || !mainConfigManager.isKitArrangementClaimOrder()){
            return items;
        }

        KitArrangement arrangement = plugin.getPlayerDataManager().getKitArrangement(player,kit.getName());
        if(arrangement == null){
            return items;
        }

        //Every item is stored as slot+position, so sorting the array sorts the items
        //by slot, keeping the config order for the items without a slot.
        int amount = items.size();
        long[] order = new long[amount];
        for(int i=0;i<amount;i++){
            int slot = arrangement.getSlot(i);
            if(slot < 0){
                slot = Integer.MAX_VALUE;
            }
            order[i] = ((long) slot << 32) | i;
        }
        Arrays.sort(order);

        List<KitItem> arrangedItems = new ArrayList<>(amount);
        for(int i=0;i<amount;i++){
            arrangedItems.add(items.get((int)(order[i] & 0xFFFFFFFFL)));
        }
        return arrangedItems;
    }
}
