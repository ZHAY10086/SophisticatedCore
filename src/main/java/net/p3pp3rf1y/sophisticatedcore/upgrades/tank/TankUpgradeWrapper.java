package net.p3pp3rf1y.sophisticatedcore.upgrades.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.init.ModFluids;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedTankUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IStackableContentsUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.CapabilityHelper;
import net.p3pp3rf1y.sophisticatedcore.util.XpHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class TankUpgradeWrapper extends UpgradeWrapperBase<TankUpgradeWrapper, TankUpgradeItem>
		implements IRenderedTankUpgrade, ITickableUpgrade, IStackableContentsUpgrade {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	private Consumer<TankRenderInfo> updateTankRenderInfoCallback;
	private final TankComponentItemHandler inventory;
	private FluidStack contents;
	private long cooldownTime = 0;

	private record AlternativeFluidContainerDefinition(Item filledItem, Item emptyItem, Fluid fluid, int amount){
		public boolean tankContentsEmptyOrMatch(FluidStack contents) {
			return contents.isEmpty() || fluid.equals(contents.getFluid());
		}
		public boolean tankContentsMatch(FluidStack contents) {
			return !contents.isEmpty() && fluid.equals(contents.getFluid());
		}
	}

	private static final List<AlternativeFluidContainerDefinition> ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS = List.of(
			new AlternativeFluidContainerDefinition(Items.EXPERIENCE_BOTTLE, Items.GLASS_BOTTLE, ModFluids.XP_STILL.get(), XpHelper.experienceToLiquid(8))
	);

	protected TankUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
		inventory = new TankComponentItemHandler(upgrade);
		contents = getContents(upgrade).copy();
	}

	public static SimpleFluidContent getContents(ItemStack upgrade) {
		return upgrade.getOrDefault(ModCoreDataComponents.FLUID_CONTENTS, SimpleFluidContent.EMPTY);
	}

	private boolean isValidFluidItem(ItemStack stack, boolean isOutput) {
		return CapabilityHelper.getFromFluidHandler(stack, fluidHandler -> isValidFluidHandler(fluidHandler, isOutput), false);
	}

	private boolean isValidFluidHandler(IFluidHandlerItem fluidHandler, boolean isOutput) {
		boolean tankEmpty = contents.isEmpty();
		for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
			FluidStack fluidInTank = fluidHandler.getFluidInTank(tank);
			if ((isOutput && (fluidInTank.isEmpty() || (!tankEmpty && FluidStack.isSameFluidSameComponents(fluidInTank, contents))))
					|| (!isOutput && !fluidInTank.isEmpty() && (tankEmpty || FluidStack.isSameFluidSameComponents(contents, fluidInTank)))
			) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setTankRenderInfoUpdateCallback(Consumer<TankRenderInfo> updateTankRenderInfoCallback) {
		this.updateTankRenderInfoCallback = updateTankRenderInfoCallback;
	}

	@Override
	public void forceUpdateTankRenderInfo() {
		TankRenderInfo renderInfo = new TankRenderInfo();
		if (!contents.isEmpty()) {
			renderInfo.setFluid(contents);
			renderInfo.setFillRatio((float) Math.round((float) contents.getAmount() / getTankCapacity() * 10) / 10);
		}
		updateTankRenderInfoCallback.accept(renderInfo);
	}

	public FluidStack getContents() {
		return contents;
	}

	public int getTankCapacity() {
		return upgradeItem.getTankCapacity(storageWrapper);
	}

	public TankComponentItemHandler getInventory() {
		return inventory;
	}

	private int getMaxInOut() {
		return (int) Math.max(FluidType.BUCKET_VOLUME, upgradeItem.getTankUpgradeConfig().maxInputOutput.get() * storageWrapper.getNumberOfSlotRows() * upgradeItem.getAdjustedStackMultiplier(storageWrapper));
	}

	public int fill(FluidStack resource, IFluidHandler.FluidAction action, boolean ignoreInOutLimit) {
		int capacity = getTankCapacity();

		if (contents.getAmount() >= capacity || (!contents.isEmpty() && !FluidStack.isSameFluidSameComponents(resource, contents))) {
			return 0;
		}

		int toFill = Math.min(capacity - contents.getAmount(), resource.getAmount());
		if (!ignoreInOutLimit) {
			toFill = Math.min(getMaxInOut(), toFill);
		}

		if (action == IFluidHandler.FluidAction.EXECUTE) {
			if (contents.isEmpty()) {
				contents = new FluidStack(resource.getFluid(), toFill);
			} else {
				contents.setAmount(contents.getAmount() + toFill);
			}
			serializeContents();
		}

		return toFill;
	}

	private void serializeContents() {
		upgrade.set(ModCoreDataComponents.FLUID_CONTENTS, SimpleFluidContent.copyOf(contents));
		save();
		forceUpdateTankRenderInfo();
	}

	public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action, boolean ignoreInOutLimit) {
		if (contents.isEmpty()) {
			return FluidStack.EMPTY;
		}

		int toDrain = Math.min(maxDrain, contents.getAmount());
		if (!ignoreInOutLimit) {
			toDrain = Math.min(getMaxInOut(), toDrain);
		}

		FluidStack ret = new FluidStack(contents.getFluid(), toDrain);
		if (action == IFluidHandler.FluidAction.EXECUTE) {
			if (toDrain == contents.getAmount()) {
				contents = FluidStack.EMPTY;
			} else {
				contents.setAmount(contents.getAmount() - toDrain);
			}
			serializeContents();
		}

		return ret;
	}

	@Override
	public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
		if (level.getGameTime() < cooldownTime) {
			return;
		}

		boolean didSomething = false;
		if (CapabilityHelper.getFromFluidHandler(inventory.getStackInSlot(INPUT_SLOT), fluidHandler ->
				drainHandler(fluidHandler, stack -> inventory.setStackInSlotWithoutValidation(INPUT_SLOT, stack)), false)) {
			didSomething = true;
		} else {
			didSomething = drainAlternativeFluidContainer(didSomething);
		}
		if (CapabilityHelper.getFromFluidHandler(inventory.getStackInSlot(OUTPUT_SLOT), fluidHandler ->
				fillHandler(fluidHandler, stack -> inventory.setStackInSlotWithoutValidation(OUTPUT_SLOT, stack)), false)) {
			didSomething = true;
		} else {
			didSomething = fillAlternativeFluidContainer(didSomething);
		}

		if (didSomething) {
			cooldownTime = level.getGameTime() + upgradeItem.getTankUpgradeConfig().autoFillDrainContainerCooldown.get();
		}
	}

	private boolean fillAlternativeFluidContainer(boolean didSomething) {
		for (AlternativeFluidContainerDefinition alt : ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS) {
			if (inventory.getStackInSlot(OUTPUT_SLOT).getItem() == alt.emptyItem && alt.tankContentsMatch(contents)) {
				if (drain(alt.amount, IFluidHandler.FluidAction.SIMULATE, false).getAmount() == alt.amount) {
					drain(alt.amount, IFluidHandler.FluidAction.EXECUTE, false);
					inventory.setStackInSlotWithoutValidation(OUTPUT_SLOT, new ItemStack(alt.filledItem));
					serializeContents();
					didSomething = true;
					break;
				}
			}
		}
		return didSomething;
	}

	private boolean drainAlternativeFluidContainer(boolean didSomething) {
		for (AlternativeFluidContainerDefinition alt : ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS) {
			if (inventory.getStackInSlot(INPUT_SLOT).getItem() == alt.filledItem && (alt.tankContentsEmptyOrMatch(contents))
					 && fill(new FluidStack(alt.fluid, alt.amount), IFluidHandler.FluidAction.SIMULATE, true) == alt.amount) {
				fill(new FluidStack(alt.fluid, alt.amount), IFluidHandler.FluidAction.EXECUTE, false);
				inventory.setStackInSlotWithoutValidation(INPUT_SLOT, new ItemStack(alt.emptyItem));
				serializeContents();
				didSomething = true;
				break;
			}
		}
		return didSomething;
	}

	public boolean fillHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack) {
		if (!contents.isEmpty() && isValidFluidHandler(fluidHandler, true)) {
			int filled = fluidHandler.fill(new FluidStack(contents.getFluid(), Math.min(FluidType.BUCKET_VOLUME, contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);
			if (filled <= 0) { //checking for less than as well because some mods have incorrect fill logic
				return false;
			}
			FluidStack drained = drain(filled, IFluidHandler.FluidAction.EXECUTE, false);
			fluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
			updateContainerStack.accept(fluidHandler.getContainer());
			return true;
		}
		return false;
	}

	public boolean drainHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack) {
		if (isValidFluidHandler(fluidHandler, false)) {
			FluidStack extracted = contents.isEmpty() ?
					fluidHandler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE) :
					fluidHandler.drain(new FluidStack(contents.getFluid(), Math.min(FluidType.BUCKET_VOLUME, getTankCapacity() - contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);
			if (extracted.isEmpty()) {
				return false;
			}
			int filled = fill(extracted, IFluidHandler.FluidAction.EXECUTE, false);
			FluidStack toExtract = filled == extracted.getAmount() ? extracted : new FluidStack(extracted.getFluid(), filled);
			fluidHandler.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);
			updateContainerStack.accept(fluidHandler.getContainer());
			return true;
		}
		return false;
	}

	@Override
	public int getMinimumMultiplierRequired() {
		return (int) Math.ceil((float) contents.getAmount() / upgradeItem.getBaseCapacity(storageWrapper));
	}

	@Override
	public boolean canBeDisabled() {
		return false;
	}

	public class TankComponentItemHandler extends ComponentItemHandler {
		public TankComponentItemHandler(ItemStack upgrade) {
			super(upgrade, DataComponents.CONTAINER, 2);
		}

		@Override
		protected void onContentsChanged(int slot, ItemStack oldStack, ItemStack newStack) {
			super.onContentsChanged(slot, oldStack, newStack);
			save();
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {


			if (slot == INPUT_SLOT) {
				return stack.isEmpty() ||  isValidFluidItem(stack, false) || ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.filledItem && alt.tankContentsEmptyOrMatch(contents) || stack.getItem() == alt.emptyItem);
			} else if (slot == OUTPUT_SLOT) {
				return stack.isEmpty() ||  isValidFluidItem(stack, false) || ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.emptyItem && alt.tankContentsMatch(contents) || stack.getItem() == alt.filledItem);
			}
			return false;
		}

		private boolean isValidInputItem(ItemStack stack) {
			return isValidFluidItem(stack, false) || ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.filledItem && alt.tankContentsEmptyOrMatch(contents));
		}

		private boolean isValidOutputItem(ItemStack stack) {
			return isValidFluidItem(stack, true) || ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.emptyItem && alt.tankContentsMatch(contents));
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		public void setStackInSlotWithoutValidation(int slot, ItemStack stack) {
			super.updateContents(getContents(), stack, slot);
		}

		public boolean isItemValidForPlacement(int slot, ItemStack stack) {
			if (slot == INPUT_SLOT) {
				return isValidInputItem(stack);
			} else if (slot == OUTPUT_SLOT) {
				return isValidOutputItem(stack);
			}
			return false;
		}
	}
}