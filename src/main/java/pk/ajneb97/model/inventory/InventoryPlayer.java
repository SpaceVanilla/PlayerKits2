package pk.ajneb97.model.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InventoryPlayer {

    //Max amount of inventories remembered to go back to.
    private static final int MAX_PREVIOUS_INVENTORIES = 10;

    private Player player;
    private String inventoryName;

    private final List<String> previousInventoryNames;
    private String kitName;
    private ItemStack[] savedInventoryContents;
    private ArrangeSession arrangeSession;

    public InventoryPlayer(Player player, String inventoryName) {
        this.player = player;
        this.inventoryName = inventoryName;
        this.previousInventoryNames = new ArrayList<>();
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public String getInventoryName() {
        return inventoryName;
    }

    public void setInventoryName(String inventoryName) {
        this.inventoryName = inventoryName;
    }

    public String getPreviousInventoryName() {
        if(previousInventoryNames.isEmpty()){
            return null;
        }
        return previousInventoryNames.get(previousInventoryNames.size()-1);
    }

    public void setPreviousInventoryName(String previousInventoryName) {
        if(previousInventoryName == null){
            return;
        }
        if(previousInventoryNames.size() >= MAX_PREVIOUS_INVENTORIES){
            previousInventoryNames.remove(0);
        }
        previousInventoryNames.add(previousInventoryName);
    }

    /**
     * Returns the inventory to go back to, removing it from the history.
     */
    public String removePreviousInventoryName() {
        if(previousInventoryNames.isEmpty()){
            return null;
        }
        return previousInventoryNames.remove(previousInventoryNames.size()-1);
    }

    public String getKitName() {
        return kitName;
    }

    public void setKitName(String kitName) {
        this.kitName = kitName;
    }

    public ArrangeSession getArrangeSession() {
        return arrangeSession;
    }

    public void setArrangeSession(ArrangeSession arrangeSession) {
        this.arrangeSession = arrangeSession;
    }

    public void restoreSavedInventoryContents() {
        if(savedInventoryContents != null){
            player.getInventory().setContents(savedInventoryContents);
            //player.updateInventory();
            savedInventoryContents = null;
        }
    }

    public void saveInventoryContents() {
        if(this.savedInventoryContents == null){
            this.savedInventoryContents = player.getInventory().getContents();
        }
    }
}
