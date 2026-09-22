package saki.asv.command;

import saki.asv.config.Config;
import saki.asv.config.Manager;
import saki.asv.config.ConfigScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * /asv          -> opens the config GUI
 * /asv gui      -> same as above
 * /asv reload   -> reloads config/autopv.json from disk
 * /asv status   -> prints current settings to chat
 * /asv mode <OFF|ALL|CUSTOM> -> quick mode switch, saved immediately
 * /asv add hand         -> add item currently in main hand to the CUSTOM id whitelist
 * /asv add <item>       -> add by id, e.g. "minecraft:cod" or just "cod"
 * /asv add name hand    -> add the display name of the item in hand to the name whitelist
 * /asv add name <name>  -> add by display name, e.g. "/asv add name Diamond Sword"
 * /asv remove <item>    -> remove by id
 * /asv remove name <name> -> remove by display name
 * /asv list             -> print current id + name whitelists
 */
public class Command {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("asv")
                        .executes(ctx -> {
                            openGui();
                            return 1;
                        })
                        .then(literal("gui").executes(ctx -> {
                            openGui();
                            return 1;
                        }))
                        .then(literal("reload").executes(ctx -> {
                            Manager.load();
                            ctx.getSource().sendFeedback(Text.literal("§a[AutoPV] Config reloaded."));
                            return 1;
                        }))
                        .then(literal("status").executes(ctx -> {
                            Config cfg = Manager.get();
                            ctx.getSource().sendFeedback(Text.literal(
                                    "§b[AutoPV] mode=" + cfg.mode +
                                            " threshold=" + cfg.thresholdSlots + "/36" +
                                            " vaults=" + cfg.vaultSlots));
                            return 1;
                        }))
                        .then(literal("mode")
                                .then(argument("value", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String value = StringArgumentType.getString(ctx, "value").toUpperCase();
                                            try {
                                                Config cfg = Manager.get();
                                                cfg.mode = Config.Mode.valueOf(value);
                                                Manager.save();
                                                ctx.getSource().sendFeedback(Text.literal("§a[AutoPV] Mode set to " + cfg.mode));
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendError(Text.literal("Mode must be OFF, ALL or CUSTOM."));
                                            }
                                            return 1;
                                        })))
                        .then(literal("add")
                                .then(literal("hand").executes(ctx -> {
                                    addHand(ctx.getSource());
                                    return 1;
                                }))
                                .then(literal("name")
                                        .then(literal("hand").executes(ctx -> {
                                            addNameFromHand(ctx.getSource());
                                            return 1;
                                        }))
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    addName(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                                                    return 1;
                                                })))
                                .then(argument("item", StringArgumentType.string())
                                        .executes(ctx -> {
                                            addItem(ctx.getSource(), StringArgumentType.getString(ctx, "item"));
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(literal("name")
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    removeName(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                                                    return 1;
                                                })))
                                .then(argument("item", StringArgumentType.string())
                                        .executes(ctx -> {
                                            removeItem(ctx.getSource(), StringArgumentType.getString(ctx, "item"));
                                            return 1;
                                        })))
                        .then(literal("list").executes(ctx -> {
                            listItems(ctx.getSource());
                            return 1;
                        }))
        ));
    }

    private static void addHand(FabricClientCommandSource source) {
        ClientPlayerEntity player = source.getPlayer();
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            source.sendError(Text.literal("Nothing in your main hand."));
            return;
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        addToWhitelist(source, id);
    }

    private static void addItem(FabricClientCommandSource source, String raw) {
        Identifier id = normalize(raw);
        if (id == null || !Registries.ITEM.containsId(id)) {
            source.sendError(Text.literal("Unknown item: " + raw));
            return;
        }
        addToWhitelist(source, id.toString());
    }

    private static void addToWhitelist(FabricClientCommandSource source, String id) {
        Config cfg = Manager.get();
        if (cfg.itemWhitelist.contains(id)) {
            source.sendFeedback(Text.literal("§e[AutoPV] Already whitelisted: " + id));
            return;
        }
        // Copy-then-reassign: after the config screen saves once, YACL's ListOption binding
        // can replace this field with an immutable list snapshot, so mutating in place throws.
        List<String> updated = new ArrayList<>(cfg.itemWhitelist);
        updated.add(id);
        cfg.itemWhitelist = updated;
        Manager.save();
        source.sendFeedback(Text.literal("§a[AutoPV] Added: " + id));
    }

    private static void addNameFromHand(FabricClientCommandSource source) {
        ClientPlayerEntity player = source.getPlayer();
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            source.sendError(Text.literal("Nothing in your main hand."));
            return;
        }
        addName(source, stack.getName().getString());
    }

    private static void addName(FabricClientCommandSource source, String rawName) {
        String name = rawName.trim();
        if (name.isEmpty()) {
            source.sendError(Text.literal("Name can't be empty."));
            return;
        }
        Config cfg = Manager.get();
        boolean alreadyPresent = cfg.itemNameWhitelist.stream().anyMatch(n -> n.equalsIgnoreCase(name));
        if (alreadyPresent) {
            source.sendFeedback(Text.literal("§e[AutoPV] Already whitelisted: " + name));
            return;
        }
        List<String> updated = new ArrayList<>(cfg.itemNameWhitelist);
        updated.add(name);
        cfg.itemNameWhitelist = updated;
        Manager.save();
        source.sendFeedback(Text.literal("§a[AutoPV] Added by name: " + name));
    }

    private static void removeName(FabricClientCommandSource source, String rawName) {
        String name = rawName.trim();
        Config cfg = Manager.get();
        List<String> updated = new ArrayList<>(cfg.itemNameWhitelist);
        boolean removed = updated.removeIf(n -> n.equalsIgnoreCase(name));
        if (removed) {
            cfg.itemNameWhitelist = updated;
            Manager.save();
            source.sendFeedback(Text.literal("§a[AutoPV] Removed by name: " + name));
        } else {
            source.sendError(Text.literal("Not in name whitelist: " + name));
        }
    }

    private static void removeItem(FabricClientCommandSource source, String raw) {
        Identifier id = normalize(raw);
        String idStr = id != null ? id.toString() : raw;
        Config cfg = Manager.get();
        List<String> updated = new ArrayList<>(cfg.itemWhitelist);
        if (updated.remove(idStr)) {
            cfg.itemWhitelist = updated;
            Manager.save();
            source.sendFeedback(Text.literal("§a[AutoPV] Removed: " + idStr));
        } else {
            source.sendError(Text.literal("Not in whitelist: " + idStr));
        }
    }

    private static void listItems(FabricClientCommandSource source) {
        Config cfg = Manager.get();
        String ids = cfg.itemWhitelist.isEmpty() ? "(none)" : String.join(", ", cfg.itemWhitelist);
        String names = cfg.itemNameWhitelist.isEmpty() ? "(none)" : String.join(", ", cfg.itemNameWhitelist);
        source.sendFeedback(Text.literal("§b[AutoPV] By id: " + ids));
        source.sendFeedback(Text.literal("§b[AutoPV] By name: " + names));
    }

    /** Accepts "cod" or "minecraft:cod"; returns null if unparsable. */
    private static Identifier normalize(String raw) {
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        return Identifier.tryParse(raw);
    }

    private static void openGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(ConfigScreen.create(client.currentScreen)));
    }
}