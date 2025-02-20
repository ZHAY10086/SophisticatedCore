package net.p3pp3rf1y.sophisticatedcore.upgrades.tank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.SophisticatedCore;
import net.p3pp3rf1y.sophisticatedcore.client.gui.INameableEmptySlot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

import java.util.function.Supplier;

public class TankUpgradeContainer extends UpgradeContainerBase<TankUpgradeWrapper, TankUpgradeContainer> {
	public static final ResourceLocation EMPTY_TANK_INPUT_SLOT_BACKGROUND = SophisticatedCore.getRL("item/empty_tank_input_slot");
	public static final ResourceLocation EMPTY_TANK_OUTPUT_SLOT_BACKGROUND = SophisticatedCore.getRL("item/empty_tank_output_slot");

	public TankUpgradeContainer(Player player, int upgradeContainerId, TankUpgradeWrapper upgradeWrapper, UpgradeContainerType<TankUpgradeWrapper, TankUpgradeContainer> type) {
		super(player, upgradeContainerId, upgradeWrapper, type);
		slots.add(new TankIOSlot(() -> this.upgradeWrapper.getInventory(), TankUpgradeWrapper.INPUT_SLOT, -100, -100, TranslationHelper.INSTANCE.translUpgradeSlotTooltip("tank_input"))
				.setBackground(InventoryMenu.BLOCK_ATLAS, EMPTY_TANK_INPUT_SLOT_BACKGROUND));
		slots.add(new TankIOSlot(() -> this.upgradeWrapper.getInventory(), TankUpgradeWrapper.OUTPUT_SLOT, -100, -100, TranslationHelper.INSTANCE.translUpgradeSlotTooltip("tank_output"))
				.setBackground(InventoryMenu.BLOCK_ATLAS, EMPTY_TANK_OUTPUT_SLOT_BACKGROUND));
		slots.add(new TakeOnlySlot(() -> this.upgradeWrapper.getInventory(), TankUpgradeWrapper.INPUT_RESULT_SLOT, -100, -100));
		slots.add(new TakeOnlySlot(() -> this.upgradeWrapper.getInventory(), TankUpgradeWrapper.OUTPUT_RESULT_SLOT, -100, -100));
	}

	@Override
	public void handleMessage(CompoundTag data) {
		//noop
	}

	public FluidStack getContents() {
		return upgradeWrapper.getContents();
	}

	public int getTankCapacity() {
		return upgradeWrapper.getTankCapacity();
	}

	private static class TankIOSlot extends SlotSuppliedHandler implements INameableEmptySlot {
		private final Component emptyTooltip;

		public TankIOSlot(Supplier<IItemHandler> itemHandlerSupplier, int slot, int xPosition, int yPosition, Component emptyTooltip) {
			super(itemHandlerSupplier, slot, xPosition, yPosition);
			this.emptyTooltip = emptyTooltip;
		}

		@Override
		public boolean hasEmptyTooltip() {
			return true;
		}

		@Override
		public Component getEmptyTooltip() {
			return emptyTooltip;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return getItemHandler().isItemValid(getSlotIndex(), stack);
		}
	}

	private static class TakeOnlySlot extends SlotSuppliedHandler {
		public TakeOnlySlot(Supplier<IItemHandler> itemHandlerSupplier, int slot, int xPosition, int yPosition) {
			super(itemHandlerSupplier, slot, xPosition, yPosition);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
