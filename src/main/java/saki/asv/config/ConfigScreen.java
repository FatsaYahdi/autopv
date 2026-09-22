package saki.asv.config;

import saki.asv.config.Config.Mode;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen layout order intentionally mirrors the reference image:
 * Notifications -> Milestone Alert -> Mode -> Threshold -> Vault Slots -> Ignored Slots.
 * A second "Items" tab holds a live-editable whitelist, so you don't have to leave the
 * GUI to run /asv add. YACL renders booleans as tickboxes, sliders with side arrows,
 * strings as plain text boxes — same visual language as the screenshot, no custom widgets.
 */
public class ConfigScreen {

    public static Screen create(Screen parent) {
        Config cfg = Manager.get();
        Config defaults = new Config();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("AutoPV Config"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("General"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Notifications"))
                                .description(OptionDescription.of(Text.literal("Show chat alerts on milestone.")))
                                .binding(defaults.notificationsEnabled, () -> cfg.notificationsEnabled, v -> cfg.notificationsEnabled = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Milestone Alert"))
                                .description(OptionDescription.of(Text.literal("Alert once used slots reach this count.")))
                                .binding(defaults.milestoneAlertSlots, () -> cfg.milestoneAlertSlots, v -> cfg.milestoneAlertSlots = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 36).step(1)
                                        .formatValue(v -> Text.literal(v + " slots")))
                                .build())
                        .option(Option.<Mode>createBuilder()
                                .name(Text.literal("Mode"))
                                .description(OptionDescription.of(Text.literal("OFF disables everything, ALL stores every item, CUSTOM uses the Items tab whitelist.")))
                                .binding(defaults.mode, () -> cfg.mode, v -> cfg.mode = v)
                                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(Mode.class))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Threshold"))
                                .description(OptionDescription.of(Text.literal("Auto-store fires once used slots reach this value.")))
                                .binding(defaults.thresholdSlots, () -> cfg.thresholdSlots, v -> cfg.thresholdSlots = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 36).step(1)
                                        .formatValue(v -> Text.literal(v + "/36 slots")))
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Text.literal("Vault Slots"))
                                .description(OptionDescription.of(Text.literal("Vault numbers to cycle through: \"1-10\" or \"1,2,5,6,7\" or a mix.")))
                                .binding(defaults.vaultSlots, () -> cfg.vaultSlots, v -> cfg.vaultSlots = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Text.literal("Vault Command"))
                                .description(OptionDescription.of(Text.literal("Server command that opens a vault, no slash/number, e.g. \"pv\" sends \"/pv 3\".")))
                                .binding(defaults.vaultOpenCommand, () -> cfg.vaultOpenCommand, v -> cfg.vaultOpenCommand = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        // .option(Option.<String>createBuilder()
                        //         .name(Text.literal("Ignored Slots"))
                        //         .description(OptionDescription.of(Text.literal(
                        //                 "Slots to never touch, using survival-inventory numbering: main inv is 9-35, hotbar is 36-44. " +
                        //                 "e.g. \"36-44\" keeps your whole hotbar, \"36,37\" keeps just those two.")))
                        //         .binding(defaults.ignoredSlots, () -> cfg.ignoredSlots, v -> cfg.ignoredSlots = v)
                        //         .controller(StringControllerBuilder::create)
                        //         .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Insert Delay"))
                                .description(OptionDescription.of(Text.literal(
                                        "Ticks to wait between each item inserted into the vault (20 ticks = 1s). 0 = instant.")))
                                .binding(defaults.insertDelayTicks, () -> cfg.insertDelayTicks, v -> cfg.insertDelayTicks = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 20).step(1)
                                        .formatValue(v -> Text.literal(v + " ticks")))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Items"))
                        .tooltip(Text.literal("Whitelist used by CUSTOM mode. Same lists /asv add / /asv remove edit."))
                        .option(ListOption.<String>createBuilder()
                                .name(Text.literal("Item Whitelist (by id)"))
                                .description(OptionDescription.of(Text.literal(
                                        "Minecraft item ids (\"minecraft:cod\") or your own custom item ids " +
                                        "(anything registered under a modid, e.g. \"mymod:magic_gem\"). " +
                                        "Add/remove rows with the buttons, or use /asv add, /asv add hand, /asv remove in chat.")))
                                .binding(defaults.itemWhitelist, () -> cfg.itemWhitelist, v -> cfg.itemWhitelist = v)
                                .controller(StringControllerBuilder::create)
                                .initial("minecraft:")
                                .build())
                        .option(ListOption.<String>createBuilder()
                                .name(Text.literal("Item Whitelist (by name)"))
                                .description(OptionDescription.of(Text.literal(
                                        "Match by the item's shown name instead of its id — case-insensitive, e.g. \"Cod\" or " +
                                        "\"Diamond Sword\". Works for custom-named/renamed items too. " +
                                        "Add/remove rows here, or use /asv add name, /asv add name hand, /asv remove name in chat.")))
                                .binding(defaults.itemNameWhitelist, () -> cfg.itemNameWhitelist, v -> cfg.itemNameWhitelist = v)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Feedback"))
                        .tooltip(Text.literal("Chat messages sent when opening/closing a vault or switching to a full one."))
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Action Feedback"))
                                .description(OptionDescription.of(Text.literal("Send the messages below to chat on open/close/full events.")))
                                .binding(defaults.actionFeedbackEnabled, () -> cfg.actionFeedbackEnabled, v -> cfg.actionFeedbackEnabled = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Text.literal("Opening Message"))
                                .description(OptionDescription.of(Text.literal("Sent right after the open command fires. {vault} = vault number.")))
                                .binding(defaults.msgOpeningVault, () -> cfg.msgOpeningVault, v -> cfg.msgOpeningVault = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Text.literal("Closing Message"))
                                .description(OptionDescription.of(Text.literal("Sent when the deposit finishes normally. {vault} = vault number.")))
                                .binding(defaults.msgClosingVault, () -> cfg.msgClosingVault, v -> cfg.msgClosingVault = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Text.literal("Full Message"))
                                .description(OptionDescription.of(Text.literal("Sent instead of the closing message when the vault couldn't take everything. {vault} = the full vault, {next} = the vault about to be tried next.")))
                                .binding(defaults.msgVaultFull, () -> cfg.msgVaultFull, v -> cfg.msgVaultFull = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .save(Manager::save)
                .build()
                .generateScreen(parent);
    }
}