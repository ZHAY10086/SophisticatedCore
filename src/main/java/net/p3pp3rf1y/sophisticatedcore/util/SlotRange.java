package net.p3pp3rf1y.sophisticatedcore.util;

public record SlotRange(int firstSlot, int numberOfSlots) {
	public boolean isInRange(int slotIndex) {
		return slotIndex >= firstSlot && slotIndex < firstSlot + numberOfSlots;
	}
}
