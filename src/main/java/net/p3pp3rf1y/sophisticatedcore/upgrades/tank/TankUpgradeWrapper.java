package net.p3pp3rf1y.sophisticatedcore.upgrades.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModFluids;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedTankUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IStackableContentsUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedcore.util.XpHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class TankUpgradeWrapper extends UpgradeWrapperBase<TankUpgradeWrapper, TankUpgradeItem>
		implements IRenderedTankUpgrade, ITickableUpgrade, IStackableContentsUpgrade {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	private static final String CONTENTS_TAG = "contents";
	private Consumer<TankRenderInfo> updateTankRenderInfoCallback;
	private final ItemStackHandler inventory;
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
		inventory = new ItemStackHandler(2) {
			@Override
			protected void onContentsChanged(int slot) {
				super.onContentsChanged(slot);
				upgrade.addTagElement("inventory", serializeNBT());
				save();
			}

			@Override
			public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
				if (slot == INPUT_SLOT) {
					return isValidInputItem(stack) || ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.filledItem && alt.tankContentsEmptyOrMatch(contents) || stack.getItem() == alt.emptyItem);
				} else if (slot == OUTPUT_SLOT) {
					return isValidOutputItem(stack)|| ALTERNATIVE_FLUID_CONTAINER_DEFINITIONS.stream().anyMatch(alt -> stack.getItem() == alt.emptyItem && alt.tankContentsMatch(contents) || stack.getItem() == alt.filledItem);
				}
				return false;
			}

			private boolean isValidInputItem(ItemStack stack) {
				return isValidFluidItem(stack, false);
			}

			private boolean isValidOutputItem(ItemStack stack) {
				return isValidFluidItem(stack, true);
			}

			@Override
			public int getSlotLimit(int slot) {
				return 1;
			}
		};
		NBTHelper.getCompound(upgrade, "inventory").ifPresent(inventory::deserializeNBT);
		contents = getContents(upgrade);
	}

	public static FluidStack getContents(ItemStack upgrade) {
		return NBTHelper.getCompound(upgrade, CONTENTS_TAG).map(FluidStack::loadFluidStackFromNBT).orElse(FluidStack.EMPTY);
	}

	private boolean isValidFluidItem(ItemStack stack, boolean isOutput) {
		return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(fluidHandler ->
				isValidFluidHandler(fluidHandler, isOutput)).orElse(false);
	}

	private boolean isValidFluidHandler(IFluidHandlerItem fluidHandler, boolean isOutput) {
		boolean tankEmpty = contents.isEmpty();
		for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
			FluidStack fluidInTank = fluidHandler.getFluidInTank(tank);
			if ((isOutput && (fluidInTank.isEmpty() || (!tankEmpty && fluidInTank.isFluidEqual(contents))))
					|| (!isOutput && !fluidInTank.isEmpty() && (tankEmpty || contents.isFluidEqual(fluidInTank)))
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

	public IItemHandler getInventory() {
		return inventory;
	}

	private int getMaxInOut() {
		return (int) Math.max(FluidType.BUCKET_VOLUME, upgradeItem.getTankUpgradeConfig().maxInputOutput.get() * storageWrapper.getNumberOfSlotRows() * upgradeItem.getAdjustedStackMultiplier(storageWrapper));
	}

	public int fill(FluidStack resource, IFluidHandler.FluidAction action, boolean ignoreInOutLimit) {
		int capacity = getTankCapacity();

		if (contents.getAmount() >= capacity || (!contents.isEmpty() && !resource.isFluidEqual(contents))) {
			return 0;
		}

		int toFill = Math.min(capacity - contents.getAmount(), resource.getAmount());
		if (!ignoreInOutLimit) {
			toFill = Math.min(getMaxInOut(), toFill);
		}

		if (action == IFluidHandler.FluidAction.EXECUTE) {
			if (contents.isEmpty()) {
				contents = new FluidStack(resource, toFill);
			} else {
				contents.setAmount(contents.getAmount() + toFill);
			}
			serializeContents();
		}

		return toFill;
	}

	private void serializeContents() {
		upgrade.addTagElement(CONTENTS_TAG, contents.writeToNBT(new CompoundTag()));
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

		FluidStack ret = new FluidStack(contents, toDrain);
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
		if (inventory.getStackInSlot(INPUT_SLOT).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(fluidHandler ->
				drainHandler(fluidHandler, stack -> inventory.setStackInSlot(INPUT_SLOT, stack))).orElse(false)) {
			didSomething = true;
		} else {
			didSomething = drainAlternativeFluidContainer(didSomething);
		}
		if (inventory.getStackInSlot(OUTPUT_SLOT).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(fluidHandler ->
				fillHandler(fluidHandler, stack -> inventory.setStackInSlot(OUTPUT_SLOT, stack))).orElse(false)) {
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
					inventory.setStackInSlot(OUTPUT_SLOT, new ItemStack(alt.filledItem));
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
				inventory.setStackInSlot(INPUT_SLOT, new ItemStack(alt.emptyItem));
				serializeContents();
				didSomething = true;
				break;
			}
		}
		return didSomething;
	}

	public boolean fillHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack) {
		if (!contents.isEmpty() && isValidFluidHandler(fluidHandler, true)) {
			int filled = fluidHandler.fill(new FluidStack(contents, Math.min(FluidType.BUCKET_VOLUME, contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);
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
					fluidHandler.drain(new FluidStack(contents, Math.min(FluidType.BUCKET_VOLUME, getTankCapacity() - contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);
			if (extracted.isEmpty()) {
				return false;
			}
			int filled = fill(extracted, IFluidHandler.FluidAction.EXECUTE, false);
			FluidStack toExtract = filled == extracted.getAmount() ? extracted : new FluidStack(extracted, filled);
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

	public boolean isItemValidForPlacement(int slot, ItemStack stack) {
		if (slot == INPUT_SLOT) {
			return isValidInputItem(stack);
		} else if (slot == OUTPUT_SLOT) {
			return isValidOutputItem(stack);
		}
		return false;
	}

	private boolean isValidInputItem(ItemStack stack) {
		return isValidFluidItem(stack, false);
	}

	private boolean isValidOutputItem(ItemStack stack) {
		return isValidFluidItem(stack, true);
	}

}