package net.p3pp3rf1y.sophisticatedcore.upgrades.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
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

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class TankUpgradeWrapper extends UpgradeWrapperBase<TankUpgradeWrapper, TankUpgradeItem>
		implements IRenderedTankUpgrade, ITickableUpgrade, IStackableContentsUpgrade {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	public static final int INPUT_RESULT_SLOT = 2;
	public static final int OUTPUT_RESULT_SLOT = 3;
	private Consumer<TankRenderInfo> updateTankRenderInfoCallback;
	private final TankComponentItemHandler inventory;
	private FluidStack contents;
	private long cooldownTime = 0;

	private static final Map<Item, Function<ItemStack, IFluidHandlerItem>> CUSTOM_FLUIDHANDLER_FACTORIES = Map.of(
			Items.EXPERIENCE_BOTTLE, stack -> new SwapEmptyFluidContainerHandler.Full(stack, Items.GLASS_BOTTLE, Items.EXPERIENCE_BOTTLE, XpHelper.experienceToLiquid(8), ModFluids.XP_STILL.get()),
			Items.GLASS_BOTTLE, stack -> new SwapEmptyFluidContainerHandler.Empty(stack, Items.GLASS_BOTTLE, Items.EXPERIENCE_BOTTLE, XpHelper.experienceToLiquid(8), ModFluids.XP_STILL.get())
	);

	protected TankUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
		contents = getContents(upgrade).copy();
		inventory = new TankComponentItemHandler(upgrade);
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
			if (isOutput && fluidInTank.getAmount() < fluidHandler.getTankCapacity(tank) &&
					(fluidInTank.isEmpty() || (!tankEmpty && FluidStack.isSameFluidSameComponents(fluidInTank, contents)))) {
				return true;
			}
			if (!isOutput && !fluidInTank.isEmpty() && (tankEmpty || FluidStack.isSameFluidSameComponents(contents, fluidInTank))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasNoMatchingFluid(IFluidHandlerItem fluidHandler) {
		boolean tankEmpty = contents.isEmpty();
		for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
			FluidStack fluidInTank = fluidHandler.getFluidInTank(tank);
			if (!tankEmpty && fluidInTank.getFluid() == contents.getFluid()) {
				return false;
			} else if (!fluidInTank.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private boolean matchingTankIsFull(IFluidHandlerItem fluidHandler) {
		boolean tankEmpty = contents.isEmpty();
		for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
			FluidStack fluidInTank = fluidHandler.getFluidInTank(tank);
			int tankCapacity = fluidHandler.getTankCapacity(tank);
			if (tankEmpty && fluidInTank.getAmount() < tankCapacity) {
				return false;
			} else if (contents.getFluid() == fluidInTank.getFluid() && fluidInTank.getAmount() < tankCapacity) {
				return false;
			}
		}
		return true;
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

		boolean didSomething = tryDraining(inventory.getStackInSlot(INPUT_SLOT));
		didSomething |= tryFilling(inventory.getStackInSlot(OUTPUT_SLOT));

		if (didSomething) {
			cooldownTime = level.getGameTime() + upgradeItem.getTankUpgradeConfig().autoFillDrainContainerCooldown.get();
		}
	}

	private boolean tryDraining(ItemStack inputSlotStack) {
		ItemStack stackToDrain = inputSlotStack;
		if (inputSlotStack.getCount() > 1) {
			stackToDrain = inputSlotStack.copyWithCount(1);
			if (!drainStack(stackToDrain, true, stack -> {
			})) {
				return false;
			}
			return drainStack(stackToDrain, false, stack -> inventory.setStackInSlot(INPUT_SLOT, inputSlotStack.copyWithCount(inputSlotStack.getCount() - 1)));
		}

		return drainStack(stackToDrain, false, stack -> inventory.setStackInSlot(INPUT_SLOT, stack));
	}

	private boolean drainStack(ItemStack stackToDrain, boolean simulateFullDrain, Consumer<ItemStack> updateContainerStack) {
		return getFluidHandler(stackToDrain).map(fluidHandler -> drainHandler(fluidHandler, updateContainerStack, true, simulateFullDrain)).orElse(false);
	}

	private Optional<IFluidHandlerItem> getFluidHandler(ItemStack stack) {
		IFluidHandlerItem result = stack.getCapability(Capabilities.FluidHandler.ITEM);
		if (result != null) {
			return Optional.of(result);
		}
		return getCustomFluidHandler(stack);
	}

	private boolean tryFilling(ItemStack outputSlotStack) {
		ItemStack stackToFill = outputSlotStack;
		if (outputSlotStack.getCount() > 1) {
			stackToFill = outputSlotStack.copyWithCount(1);
			if (!fillStack(stackToFill, true, stack -> {
			})) {
				return false;
			}
			return fillStack(stackToFill, false, stack -> inventory.setStackInSlot(OUTPUT_SLOT, outputSlotStack.copyWithCount(outputSlotStack.getCount() - 1)));
		}

		return fillStack(stackToFill, false, stack -> inventory.setStackInSlot(OUTPUT_SLOT, stack));
	}

	private boolean fillStack(ItemStack outputSlotStack, boolean simulateFullFill, Consumer<ItemStack> updateContainerStack) {
		return getFluidHandler(outputSlotStack).map(fluidHandler -> fillHandler(fluidHandler, updateContainerStack, true, simulateFullFill)).orElse(false);
	}

	public void interactWithCursorStack(ItemStack cursorStack, Consumer<ItemStack> updateContainerStack) {
		getFluidHandler(cursorStack).ifPresent(fluidHandler -> {
			FluidStack tankContents = getContents();
			if (tankContents.isEmpty()) {
				drainHandler(fluidHandler, updateContainerStack);
			} else {
				if (!fillHandler(fluidHandler, updateContainerStack, false, false)) {
					drainHandler(fluidHandler, updateContainerStack);
				}
			}
		});
	}

	private static Optional<IFluidHandlerItem> getCustomFluidHandler(ItemStack stack) {
		return CUSTOM_FLUIDHANDLER_FACTORIES.entrySet().stream().filter(e -> stack.getItem() == e.getKey()).map(e -> e.getValue().apply(stack)).findFirst();
	}

	public boolean fillHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack, boolean moveFullToResult, boolean simulateIncludingFullFill) {
		if (!contents.isEmpty() && isValidFluidHandler(fluidHandler, true)) {
			int filled = fluidHandler.fill(new FluidStack(contents.getFluid(), Math.min(FluidType.BUCKET_VOLUME, contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);
			if (filled <= 0) { //checking for less than as well because some mods have incorrect fill logic
				return false;
			}

			if (moveFullToResult) {
				ItemStack containerCopy = fluidHandler.getContainer().copy();
				if (isFullAfterFillButUnableToInsertIntoResult(containerCopy)) {
					return false;
				}
			}

			if (simulateIncludingFullFill) {
				return fluidHandler.getTanks() > 0 && drain(filled, IFluidHandler.FluidAction.SIMULATE, false).getAmount() == fluidHandler.getTankCapacity(0);
			}

			FluidStack drained = drain(filled, IFluidHandler.FluidAction.EXECUTE, false);
			fluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);

			if (moveFullToResult && matchingTankIsFull(fluidHandler)) {
				updateContainerStack.accept(ItemStack.EMPTY);
				inventory.insertItem(OUTPUT_RESULT_SLOT, fluidHandler.getContainer(), false);
			} else {
				updateContainerStack.accept(fluidHandler.getContainer());
			}
			return true;
		}
		return false;
	}

	private Boolean isFullAfterFillButUnableToInsertIntoResult(ItemStack containerCopy) {
		return getFluidHandler(containerCopy).map(copyFluidHandler -> {
			copyFluidHandler.fill(new FluidStack(contents.getFluid(), Math.min(FluidType.BUCKET_VOLUME, contents.getAmount())), IFluidHandler.FluidAction.EXECUTE);
			int tank = getMatchingTank(copyFluidHandler, contents);
			if (tank < 0) {
				return true;
			}
			return copyFluidHandler.getFluidInTank(tank).getAmount() == copyFluidHandler.getTankCapacity(tank) && !inventory.insertItem(OUTPUT_RESULT_SLOT, copyFluidHandler.getContainer(), true).isEmpty();
		}).orElse(true);
	}

	public void drainHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack) {
		drainHandler(fluidHandler, updateContainerStack, false, false);
	}

	public boolean drainHandler(IFluidHandlerItem fluidHandler, Consumer<ItemStack> updateContainerStack, boolean moveEmptyToResult, boolean simulateIncludingFullDrain) {
		if (isValidFluidHandler(fluidHandler, false)) {
			FluidStack extracted = contents.isEmpty() ?
					fluidHandler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE) :
					fluidHandler.drain(new FluidStack(contents.getFluid(), Math.min(FluidType.BUCKET_VOLUME, getTankCapacity() - contents.getAmount())), IFluidHandler.FluidAction.SIMULATE);

			if (extracted.isEmpty()) {
				return false;
			}

			if (moveEmptyToResult) {
				ItemStack containerCopy = fluidHandler.getContainer().copy();
				if (isEmptyAfterDrainButUnableToInsertIntoResult(containerCopy, extracted)) {
					return false;
				}
			}

			if (simulateIncludingFullDrain) {
				return fluidHandler.getTanks() > 0 && fill(extracted, IFluidHandler.FluidAction.SIMULATE, false) == fluidHandler.getTankCapacity(0);
			}

			int filled = fill(extracted, IFluidHandler.FluidAction.EXECUTE, false);
			FluidStack toExtract = filled == extracted.getAmount() ? extracted : new FluidStack(extracted.getFluid(), filled);
			fluidHandler.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);

			if (moveEmptyToResult && hasNoMatchingFluid(fluidHandler)) {
				updateContainerStack.accept(ItemStack.EMPTY);
				inventory.insertItem(INPUT_RESULT_SLOT, fluidHandler.getContainer(), false);
			} else {
				updateContainerStack.accept(fluidHandler.getContainer());
			}

			return true;
		}
		return false;
	}

	private boolean isEmptyAfterDrainButUnableToInsertIntoResult(ItemStack containerCopy, FluidStack extracted) {
		return getFluidHandler(containerCopy).map(copyFluidHandler -> {
			int tank = getMatchingTank(copyFluidHandler, extracted);
			if (tank < 0) {
				return true;
			}
			copyFluidHandler.drain(extracted, IFluidHandler.FluidAction.EXECUTE);
			return copyFluidHandler.getFluidInTank(tank).isEmpty() && !inventory.insertItem(INPUT_RESULT_SLOT, copyFluidHandler.getContainer(), true).isEmpty();
		}).orElse(true);
	}

	private int getMatchingTank(IFluidHandlerItem fluidHandler, FluidStack matchTo) {
		for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
			FluidStack fluidInTank = fluidHandler.getFluidInTank(tank);
			if (FluidStack.isSameFluidSameComponents(fluidInTank, matchTo)) {
				return tank;
			}
		}
		return -1;
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
			super(upgrade, DataComponents.CONTAINER, 4);
			migrateLegacyContents();
		}

		private void migrateLegacyContents() {
			ItemContainerContents currentContents = getContents();
			ItemStack inputStack = getStackFromContents(currentContents, INPUT_SLOT);
			if (!inputStack.isEmpty() && !hasValidFluidHandler(inputStack, false)) {
				setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
				setStackInSlot(INPUT_RESULT_SLOT, inputStack);
			}
			ItemStack outputStack = getStackFromContents(currentContents, OUTPUT_SLOT);
			if (!outputStack.isEmpty() && !hasValidFluidHandler(outputStack, true)) {
				setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);
				setStackInSlot(OUTPUT_RESULT_SLOT, outputStack);
			}
		}

		@Override
		protected void onContentsChanged(int slot, ItemStack oldStack, ItemStack newStack) {
			super.onContentsChanged(slot, oldStack, newStack);
			save();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if (slot == INPUT_SLOT) {
				return stack.isEmpty() || hasValidFluidHandler(stack, false);
			} else if (slot == OUTPUT_SLOT) {
				return stack.isEmpty() || hasValidFluidHandler(stack, true);
			}
			return slot == INPUT_RESULT_SLOT || slot == OUTPUT_RESULT_SLOT;
		}

		private boolean hasValidFluidHandler(ItemStack stack, boolean isOutput) {
			return getFluidHandler(stack).map(fluidHandler -> isValidFluidHandler(fluidHandler, isOutput)).orElse(false);
		}
	}

	private static abstract class SwapEmptyFluidContainerHandler implements IFluidHandlerItem {
		private ItemStack container;
		private final Item empty;
		private final Item full;
		private FluidStack contents;
		private final int capacity;
		private final FluidStack validFluid;

		public static class Empty extends SwapEmptyFluidContainerHandler {
			public Empty(ItemStack container, Item empty, Item full, int capacity, Fluid validFluid) {
				super(container, empty, full, FluidStack.EMPTY, capacity, new FluidStack(validFluid, capacity));
			}
		}

		public static class Full extends SwapEmptyFluidContainerHandler {
			public Full(ItemStack container, Item empty, Item full, int capacity, Fluid validFluid) {
				super(container, empty, full, new FluidStack(validFluid, capacity), capacity, new FluidStack(validFluid, capacity));
			}
		}

		protected SwapEmptyFluidContainerHandler(ItemStack container, Item empty, Item full, FluidStack contents, int capacity, FluidStack validFluid) {
			this.container = container;
			this.empty = empty;
			this.full = full;
			this.contents = contents;
			this.capacity = capacity;
			this.validFluid = validFluid;
		}

		@Override
		public ItemStack getContainer() {
			return container;
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return contents;
		}

		@Override
		public int getTankCapacity(int tank) {
			return capacity;
		}

		@Override
		public boolean isFluidValid(int i, FluidStack fluidStack) {
			return FluidStack.isSameFluidSameComponents(validFluid, fluidStack);
		}

		@Override
		public int fill(FluidStack fluidStack, FluidAction fluidAction) {
			if (!isFluidValid(0, fluidStack) || container.getItem() != empty) {
				return 0;
			}

			int result = 0;
			if (fluidStack.getAmount() >= capacity) {
				result = capacity;
				if (fluidAction == FluidAction.EXECUTE) {
					container = new ItemStack(full);
					contents = fluidStack.copyWithAmount(capacity);
				}
			}

			return result;
		}

		@Override
		public FluidStack drain(FluidStack fluidStack, FluidAction fluidAction) {
			if (fluidStack.isEmpty() || container.getItem() != full) {
				return FluidStack.EMPTY;
			}

			FluidStack result = FluidStack.EMPTY;
			if (isFluidValid(0, fluidStack) && fluidStack.getAmount() >= capacity) {
				result = fluidStack.copyWithAmount(capacity);
				if (fluidAction == FluidAction.EXECUTE) {
					container = new ItemStack(empty);
					contents = FluidStack.EMPTY;
				}
			}

			return result;
		}

		@Override
		public FluidStack drain(int toDrain, FluidAction fluidAction) {
			if (container.getItem() != full || toDrain < capacity) {
				return FluidStack.EMPTY;
			}

			return drain(contents, fluidAction);
		}
	}
}