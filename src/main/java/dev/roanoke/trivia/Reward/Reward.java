package dev.roanoke.trivia.Reward;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import dev.roanoke.trivia.Trivia;

public class Reward {

    public String itemName;
    public String itemDisplayName;
    public Integer quantity;
    public ItemStack itemStack;
    public String command;

    public Reward(String itemName, String itemDisplayName, Integer quantity, String command) {
        this.itemName = itemName;
        this.itemDisplayName = itemDisplayName;
        this.quantity = quantity;
        this.itemStack = getItemStack(itemName);
        this.command = command;

        Trivia.LOGGER.info("Reward item: " + itemDisplayName + " - itemName : " + itemName + " ItemStack: " + itemStack.toString() + " command: " + command);
    }

    // take the itemName and return an ItemStack
    public ItemStack getItemStack(String itemName) {
        return new ItemStack(Registries.ITEM.get(Identifier.tryParse(itemName)), quantity);
    }

}
