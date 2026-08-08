package pk.ajneb97.model;

/**
 * Custom arrangement of a kit, created by a player on the arrange inventory.
 * The position of the item on the kit is used as the index of the array, and
 * the value is the inventory slot chosen by the player (-1 if it has none).
 */
public class KitArrangement {

    private final int[] slots;

    public KitArrangement(int[] slots) {
        this.slots = slots;
    }

    public static KitArrangement fromText(String text){
        if(text == null || text.isEmpty()){
            return null;
        }

        String[] sep = text.split(",");
        int[] slots = new int[sep.length];
        for(int i=0;i<sep.length;i++){
            try{
                slots[i] = Integer.parseInt(sep[i].trim());
            }catch(NumberFormatException e){
                //Invalid data, the kit will use its default arrangement.
                return null;
            }
        }

        KitArrangement arrangement = new KitArrangement(slots);
        return arrangement.isEmpty() ? null : arrangement;
    }

    public String toText(){
        StringBuilder text = new StringBuilder();
        for(int i=0;i<slots.length;i++){
            if(i != 0){
                text.append(',');
            }
            text.append(slots[i]);
        }
        return text.toString();
    }

    public int getSlot(int itemPosition){
        if(itemPosition < 0 || itemPosition >= slots.length){
            return -1;
        }
        return slots[itemPosition];
    }

    public int size(){
        return slots.length;
    }

    public boolean isEmpty(){
        for(int slot : slots){
            if(slot != -1){
                return false;
            }
        }
        return true;
    }
}
