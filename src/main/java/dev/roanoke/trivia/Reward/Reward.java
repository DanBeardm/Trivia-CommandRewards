package dev.roanoke.trivia.Reward;

import dev.roanoke.trivia.Trivia;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class Reward {

    public String itemName;         // optional
    public String itemDisplayName;  // optional (but recommended)
    public Integer quantity;        // optional (defaults to 1)
    public ItemStack itemStack;     // nullable -> command-only reward
    public String command;          // optional

    public Reward(String itemName, String itemDisplayName, Integer quantity, String command) {
        this.itemName = (itemName == null) ? "" : itemName;
        this.itemDisplayName = (itemDisplayName == null) ? "" : itemDisplayName;
        this.quantity = (quantity == null || quantity <= 0) ? 1 : quantity;
        this.command = (command == null) ? "" : command;

        this.itemStack = getItemStack(this.itemName, this.quantity);

        Trivia.LOGGER.info("Reward loaded: display='{}' item='{}' qty={} hasItem={} command='{}'",
                this.itemDisplayName, this.itemName, this.quantity, hasItem(), this.command);
    }

    public boolean hasCommand() {
        return command != null && !command.isBlank();
    }

    public boolean hasItem() {
        return itemStack != null && !itemStack.isEmpty();
    }

    public boolean isValid() {
        return hasItem() || hasCommand();
    }

    // Returns null if no item_name, invalid identifier, or not registered.
    private static ItemStack getItemStack(String itemName, int quantity) {
        if (itemName == null || itemName.isBlank()) return null;

        Identifier id = Identifier.tryParse(itemName);
        if (id == null || !Registries.ITEM.containsId(id)) { // containsId exists on Registry :contentReference[oaicite:0]{index=0}
            Trivia.LOGGER.warn("Invalid item id in reward: '{}'", itemName);
            return null;
        }

        return new ItemStack(Registries.ITEM.get(id), quantity);
    }
}
