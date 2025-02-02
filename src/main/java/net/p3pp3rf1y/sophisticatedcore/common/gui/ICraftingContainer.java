package net.p3pp3rf1y.sophisticatedcore.common.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

public interface ICraftingContainer {
	List<Slot> getRecipeSlots();

	Container getCraftMatrix();

	void setRecipeUsed(ResourceLocation recipeId);

	RecipeType<?> getRecipeType();
}
