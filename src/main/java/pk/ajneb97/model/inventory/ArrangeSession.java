package pk.ajneb97.model.inventory;

/**
 * State of a player that is arranging a kit. Everything the arrange inventory
 * needs is kept here, so moving items around never has to read the items again.
 */
public class ArrangeSession {

    //Kit item position placed on every slot of the inventory (-1 if empty).
    private final int[] slotItems;
    //Slots used by the menu items (buttons), they can't hold kit items.
    private final boolean[] reservedSlots;
    private final int itemsAmount;

    //Kit item position currently on the cursor of the player (-1 if none).
    private int cursorItem;

    public ArrangeSession(int[] slotItems, boolean[] reservedSlots, int itemsAmount) {
        this.slotItems = slotItems;
        this.reservedSlots = reservedSlots;
        this.itemsAmount = itemsAmount;
        this.cursorItem = -1;
    }

    public int[] getSlotItems() {
        return slotItems;
    }

    public int getItemsAmount() {
        return itemsAmount;
    }

    public int getCursorItem() {
        return cursorItem;
    }

    public void setCursorItem(int cursorItem) {
        this.cursorItem = cursorItem;
    }

    public boolean isReserved(int slot) {
        return slot < 0 || slot >= reservedSlots.length || reservedSlots[slot];
    }

    public int getSlotItem(int slot) {
        if(slot < 0 || slot >= slotItems.length){
            return -1;
        }
        return slotItems[slot];
    }

    public void setSlotItem(int slot, int itemPosition) {
        if(slot < 0 || slot >= slotItems.length){
            return;
        }
        slotItems[slot] = itemPosition;
    }

    public int getFirstFreeSlot() {
        for(int slot=0;slot<slotItems.length;slot++){
            if(slotItems[slot] == -1 && !reservedSlots[slot]){
                return slot;
            }
        }
        return -1;
    }
}
