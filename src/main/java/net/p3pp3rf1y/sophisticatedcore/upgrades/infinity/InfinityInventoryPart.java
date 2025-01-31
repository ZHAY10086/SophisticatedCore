package net.p3pp3rf1y.sophisticatedcore.upgrades.infinity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.inventory.IInventoryPartHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.util.SlotRange;
import org.apache.commons.lang3.function.TriFunction;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

public class InfinityInventoryPart implements IInventoryPartHandler {
	public static final String NAME = "infinity";
	private final InventoryHandler parent;
	private final SlotRange slotRange;
	private final Map<Integer, ItemStack> cachedStacks = new HashMap<>();

	public InfinityInventoryPart(InventoryHandler parent, SlotRange slotRange) {
		this.parent = parent;
		this.slotRange = slotRange;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean isInfinite(int slot) {
		return !parent.getSlotStack(slot).isEmpty();
	}

	@Override
	public int getSlotLimit(int slot) {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack, @Nullable Player player, BiPredicate<Integer, ItemStack> isItemValidSuper) {
		return player != null && player.hasPermissions(1) && parent.getSlotStack(slot).isEmpty() && isItemValidSuper.test(slot, stack);
	}

	@Override
	public boolean isSlotAccessible(int slot) {
		return true;
	}

	@Override
	public int getStackLimit(int slot, ItemStack stack) {
		return Integer.MAX_VALUE;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return parent.getSlotStack(slot).copyWithCount(amount);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, TriFunction<Integer, ItemStack, Boolean, ItemStack> insertSuper) {
		if (!parent.getSlotStack(slot).isEmpty()) {
			return stack;
		}
		cachedStacks.remove(slot);
		return insertSuper.apply(slot, stack, simulate);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack, BiConsumer<Integer, ItemStack> setStackInSlotSuper) {
		if (parent.getSlotStack(slot).isEmpty()) {
			cachedStacks.remove(slot);
			parent.setSlotStack(slot, stack);
		}
	}

	@Override
	public ItemStack getStackInSlot(int slot, IntFunction<ItemStack> getStackInSlotSuper) {
		if (cachedStacks.containsKey(slot) && cachedStacks.get(slot).isEmpty() != parent.getSlotStack(slot).isEmpty()) {
			cachedStacks.remove(slot);
		}

		return cachedStacks.computeIfAbsent(slot, s ->  parent.getSlotStack(s).copyWithCount(Integer.MAX_VALUE));
	}

	@Override
	public int getSlots() {
		return slotRange.numberOfSlots();
	}
}
