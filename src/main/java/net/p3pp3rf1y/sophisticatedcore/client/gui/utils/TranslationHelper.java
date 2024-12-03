package net.p3pp3rf1y.sophisticatedcore.client.gui.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.p3pp3rf1y.sophisticatedcore.SophisticatedCore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TranslationHelper {
	public static final String TOOLTIP_SUFFIX = ".tooltip";
	private static final String BUTTONS_SUFFIX = "buttons.";
	private static final String MESSAGE_SUFFIX = "message.";
	private static final String CONTROLS_SUFFIX = "controls.";

	public static final TranslationHelper INSTANCE = new TranslationHelper(SophisticatedCore.MOD_ID);

	private final String guiPrefix;
	private final String guiUpgradePrefix;
	private final String guiSettingsPrefix;
	private final String guiStatusPrefix;
	private final String buttonsPrefix;
	private final String itemUpgradePrefix;
	private final String blockPrefix;
	private final String upgradeGroupPrefix;
	private final String upgradeButtonsPrefix;
	private final String upgradeControlsPrefix;
	private final String keybindPrefix;

	public TranslationHelper(String modId) {
		guiPrefix = "gui." + modId + ".";
		keybindPrefix = "keybind." + modId + ".";
		itemUpgradePrefix = "item." + modId + ".";
		blockPrefix = "block." + modId + ".";
		upgradeGroupPrefix = "upgrade_group." + modId + ".";
		guiUpgradePrefix = guiPrefix + "upgrades.";
		guiSettingsPrefix = guiPrefix + "settings.";
		guiStatusPrefix = guiPrefix + "status.";
		buttonsPrefix = guiPrefix + BUTTONS_SUFFIX;
		upgradeButtonsPrefix = guiUpgradePrefix + BUTTONS_SUFFIX;
		upgradeControlsPrefix = guiUpgradePrefix + CONTROLS_SUFFIX;
	}

	public Component translStatusMessage(String statusMessage, Object... params) {
		return Component.translatable(guiStatusPrefix + statusMessage, params);
	}

	public MutableComponent translUpgrade(String upgradeName, Object... params) {
		return Component.translatable(translUpgradeKey(upgradeName), params);
	}

	public Component translUpgradeSlotTooltip(String tooltipName) {
		return Component.translatable(guiUpgradePrefix + "slots." + tooltipName + TOOLTIP_SUFFIX);
	}

	public String translUpgradeKey(String upgradeName) {
		return guiUpgradePrefix + upgradeName;
	}
	public String translBlockTooltipKey(String blockName) {
		return blockPrefix + blockName + TOOLTIP_SUFFIX;
	}

	public String translSettings(String categoryName) {
		return guiSettingsPrefix + categoryName;
	}

	public String translSettingsButton(String buttonName) {
		return translSettings(BUTTONS_SUFFIX + buttonName);
	}

	public String translSettingsMessage(String messageName) {
		return translSettings(MESSAGE_SUFFIX + messageName);
	}

	public Component translUpgradeTooltip(String upgradeName) {
		return Component.translatable(translUpgradeKey(upgradeName) + TOOLTIP_SUFFIX);
	}

	public String translSettingsTooltip(String categoryName) {
		return translSettings(categoryName) + TOOLTIP_SUFFIX;
	}

	public Component translColoredButton(String buttonName, ChatFormatting color) {
		return Component.translatable(translButton(buttonName)).withStyle(color);
	}

	public String translButton(String buttonName) {
		return buttonsPrefix + buttonName;
	}

	public Component translError(String key, Object... params) {
		return Component.translatable(guiPrefix + "error." + key, params);
	}

	public String translUpgradeGroup(String groupName) {
		return upgradeGroupPrefix + groupName;
	}

	public String translUpgradeButton(String buttonName) {
		return upgradeButtonsPrefix + buttonName;
	}

	public String translUpgradeControl(String controlName) {
		return upgradeControlsPrefix + controlName;
	}

	public String translItemTooltip(String itemName) {
		return itemUpgradePrefix + itemName + TOOLTIP_SUFFIX;
	}

	public List<Component> getTranslatedLines(String translateKey, @Nullable Object parameters, ChatFormatting... textFormattings) {
		List<Component> ret = new ArrayList<>();
		for (Component translatedLine : getTranslatedLines(translateKey, parameters)) {
			if (translatedLine instanceof MutableComponent mutableComponent) {
				mutableComponent.withStyle(textFormattings);
				ret.add(translatedLine);
			}
		}

		return ret;
	}

	public List<Component> getTranslatedLines(String translateKey) {
		return getTranslatedLines(translateKey, null);
	}

	public List<Component> getTranslatedLines(String translateKey, @Nullable Object parameters) {
		String text = translate(translateKey, parameters);

		String[] lines = text.split("\n");

		List<Component> ret = new ArrayList<>();
		for (String line : lines) {
			ret.add(Component.literal(line));
		}

		return ret;
	}

	public String translate(String translateKey, Object... parameters) {
		return I18n.get(translateKey, parameters);
	}

	public String translKeybind(String keybindName) {
		return keybindPrefix + keybindName;
	}

	public String translGui(String guiTranslateKey) {
		return guiPrefix + guiTranslateKey;
	}

	public String translGuiTooltip(String guiTranslateKey) {
		return guiPrefix.substring(0, guiPrefix.length() - 1) + TOOLTIP_SUFFIX + "." + guiTranslateKey;
	}
}
