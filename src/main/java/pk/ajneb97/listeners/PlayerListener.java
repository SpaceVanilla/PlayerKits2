package pk.ajneb97.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.InventoryManager;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.utils.InventoryUtils;

public class PlayerListener implements Listener {

    private PlayerKits2 plugin;
    public PlayerListener(PlayerKits2 plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().manageJoin(player);

        //Update notification
        String latestVersion = plugin.getUpdateCheckerManager().getLatestVersion();
        if(player.isOp() && plugin.getConfigsManager().getMainConfigManager().isUpdateNotify() && !plugin.version.equals(latestVersion)){
            player.sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+"&cThere is a new version available. &e(&7"+latestVersion+"&e)"));
            player.sendMessage(MessagesManager.getLegacyColoredMessage("&cYou can download it at: &ahttps://modrinth.com/plugin/playerkits-2"));
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event){
        plugin.getPlayerDataManager().manageLeave(event.getPlayer());
    }

    @EventHandler
    public void closeInventory(InventoryCloseEvent event){
        Player player = (Player) event.getPlayer();
        InventoryManager invManager = plugin.getInventoryManager();
        InventoryPlayer inventoryPlayer = invManager.getInventoryPlayer(player);
        if(inventoryPlayer != null && inventoryPlayer.getArrangeSession() != null){
            invManager.getInventoryArrangeManager().closeInventory(inventoryPlayer);
        }
        invManager.removeInventoryPlayer(player);
    }

    @EventHandler
    public void clickInventory(InventoryClickEvent event){
        Player player = (Player) event.getWhoClicked();
        InventoryManager invManager = plugin.getInventoryManager();
        InventoryPlayer inventoryPlayer = invManager.getInventoryPlayer(player);
        if(inventoryPlayer != null) {
            if(inventoryPlayer.getArrangeSession() != null){
                invManager.getInventoryArrangeManager().clickInventory(inventoryPlayer,event);
                return;
            }

            event.setCancelled(true);
            if(event.getCurrentItem() == null || event.getSlotType() == null){
                return;
            }

            if(event.getClickedInventory().equals(InventoryUtils.getTopInventory(player))) {
                ClickType clickType = event.getClick();
                invManager.clickInventory(inventoryPlayer,event.getCurrentItem(),clickType);
            }
        }
    }

    @EventHandler
    public void dragInventory(InventoryDragEvent event){
        Player player = (Player) event.getWhoClicked();
        InventoryPlayer inventoryPlayer = plugin.getInventoryManager().getInventoryPlayer(player);
        if(inventoryPlayer == null) {
            return;
        }

        if(inventoryPlayer.getArrangeSession() != null){
            //Dragging could take the item of the cursor out of the arrange inventory.
            event.setCancelled(true);
            return;
        }

        //Items can never be dragged into a PlayerKits inventory.
        int slots = event.getInventory().getSize();
        for(int slot : event.getRawSlots()){
            if(slot < slots){
                event.setCancelled(true);
                return;
            }
        }
    }
}
