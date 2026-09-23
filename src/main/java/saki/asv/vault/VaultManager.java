package saki.asv.vault;

import saki.asv.config.Config;
import saki.asv.config.Manager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flow:
 *  1. Client tick counts used inventory slots. Once >= threshold, sends "/<vaultOpenCommand> <n>".
 *  2. When any GenericContainerScreen opens right after that, we treat it as the vault, queue up
 *     every matching player-side slot, then shift-click them one at a time — insertDelayTicks
 *     ticks apart — instead of all in the same tick. Once the queue drains, the screen closes.
 *
 * NOTE: this assumes the vault GUI is a plain chest-style container (GenericContainerScreen)
 * and that the last 36 slots of its ScreenHandler are the player's own inventory — true for
 * vanilla chest-shaped containers, which is how most vault plugins (e.g. PlayerVaults) render.
 * If your server's vault plugin uses a different command/GUI shape, adjust here.
 */
public class VaultManager {

    private static final int PLAYER_INV_SIZE = 36; // 27 main + 9 hotbar
    private static final long TIMEOUT_TICKS = 100;  // ~5s safety timeout waiting for GUI to open

    private static boolean awaitingVaultScreen = false;
    private static long commandSentTick = -1;
    private static int cycleIndex = 0;
    private static boolean milestoneAlertActive = false;

    // --- deposit state machine (drips items into the vault with a delay) ---
    private static boolean depositing = false;
    private static ScreenHandler depositHandler;
    private static Deque<Integer> depositQueue = new ArrayDeque<>();
    private static long lastInsertTick = -1;
    private static boolean depositStalled = false; // true if any click this cycle failed to move its item
    private static int currentVaultNumber = -1;    // vault number this deposit cycle is/was targeting

    // --- "every configured vault is full" tracking ---
    private static int consecutiveFullVaults = 0; // resets to 0 on any successful (non-full) deposit
    private static boolean allVaultsFull = false; // true once every configured vault has reported full in a row

    public static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        Config cfg = Manager.get();

        if (depositing) {
            processDeposit(client, cfg);
            return;
        }

        if (cfg.mode == Config.Mode.OFF) return;

        Set<Integer> ignored = ignoredInventoryIndices(cfg);
        int occupied = countOccupied(client.player.getInventory(), ignored);

        // Ignored slots are permanently excluded from the count, so the highest achievable
        // "occupied" value shrinks by however many slots are ignored. Clamp the configured
        // thresholds down to that ceiling, otherwise a default threshold of 36 (or any value
        // above what's actually reachable) can never be hit once slots are ignored, and
        // auto-store silently stops triggering forever, leaving a full inventory stuck.
        int countable = PLAYER_INV_SIZE - ignored.size();
        int effectiveThreshold = Math.min(cfg.thresholdSlots, countable);
        int effectiveMilestone = Math.min(cfg.milestoneAlertSlots, countable);

        handleMilestone(client, cfg, occupied, effectiveMilestone);

        // Inventory dropped back below threshold (items used, dropped, manually moved, etc.) —
        // give auto-store another chance next time it fills up instead of staying paused forever.
        if (occupied < effectiveThreshold && allVaultsFull) {
            allVaultsFull = false;
            consecutiveFullVaults = 0;
        }

        if (awaitingVaultScreen) {
            if (client.world.getTime() - commandSentTick > TIMEOUT_TICKS) {
                awaitingVaultScreen = false; // give up, will retry next tick if still over threshold
            }
            return;
        }

        if (occupied >= effectiveThreshold) {
            if (allVaultsFull) return; // every configured vault reported full; wait for the inventory to change
            triggerAutoStore(client, cfg);
        }
    }

    private static void handleMilestone(MinecraftClient client, Config cfg, int occupied, int milestoneThreshold) {
        if (!cfg.notificationsEnabled) return;
        if (occupied >= milestoneThreshold && !milestoneAlertActive) {
            client.player.sendMessage(Text.literal("§b[AutoPV] Inventory at " + occupied + "/" + PLAYER_INV_SIZE + " slots."), false);
            milestoneAlertActive = true;
        } else if (occupied < milestoneThreshold) {
            milestoneAlertActive = false;
        }
    }

    private static void triggerAutoStore(MinecraftClient client, Config cfg) {
        List<Integer> vaults = VaultUtils.parseSlots(cfg.vaultSlots);
        if (vaults.isEmpty()) return;

        int vaultNumber = vaults.get(cycleIndex % vaults.size());
        currentVaultNumber = vaultNumber;
        client.player.networkHandler.sendChatCommand(cfg.vaultOpenCommand + " " + vaultNumber);
        sendFeedback(client, cfg, cfg.msgOpeningVault, vaultNumber, -1);
        awaitingVaultScreen = true;
        commandSentTick = client.world.getTime();
    }

    /** Wire this to ScreenEvents.AFTER_INIT. */
    public static void onScreenOpen(Screen screen) {
        if (!awaitingVaultScreen) return;
        if (!(screen instanceof GenericContainerScreen containerScreen)) return;

        awaitingVaultScreen = false;
        MinecraftClient client = MinecraftClient.getInstance();
        Config cfg = Manager.get();
        ScreenHandler handler = containerScreen.getScreenHandler();

        int totalSlots = handler.slots.size();
        int vaultSlotCount = totalSlots - PLAYER_INV_SIZE;
        if (vaultSlotCount <= 0) {
            client.player.closeHandledScreen();
            return;
        }

        Set<Integer> ignored = ignoredContainerIndices(cfg);
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = vaultSlotCount; i < totalSlots; i++) {
            int playerInvIndex = i - vaultSlotCount;
            if (ignored.contains(playerInvIndex)) continue;
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty() || !shouldStore(cfg, stack)) continue;
            queue.add(i);
        }

        if (queue.isEmpty()) {
            cycleIndex++; // nothing to move here, try the next configured vault next time
            sendFeedback(client, cfg, cfg.msgClosingVault, currentVaultNumber, -1);
            client.player.closeHandledScreen();
            return;
        }

        depositHandler = handler;
        depositQueue = queue;
        depositing = true;
        depositStalled = false;
        lastInsertTick = -1; // forces the first insert on the very next tick
    }

    /** Pops one slot off the queue every insertDelayTicks, until it's drained. */
    private static void processDeposit(MinecraftClient client, Config cfg) {
        if (client.player == null || client.world == null
                || !(client.currentScreen instanceof GenericContainerScreen containerScreen)
                || containerScreen.getScreenHandler() != depositHandler) {
            // screen closed/changed under us (server closed it, player pressed escape, etc.) — bail cleanly
            finishDeposit(client, false);
            return;
        }

        if (depositQueue.isEmpty()) {
            finishDeposit(client, true);
            return;
        }

        long now = client.world.getTime();
        if (lastInsertTick >= 0 && now - lastInsertTick < cfg.insertDelayTicks) return;

        int slotIndex = depositQueue.poll();
        Slot slot = depositHandler.getSlot(slotIndex);
        if (!slot.getStack().isEmpty()) {
            client.interactionManager.clickSlot(depositHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
            // If the item is still sitting there after the shift-click, the vault had no room
            // for it (fully or for that item type) — that's our real "full" signal.
            if (!slot.getStack().isEmpty()) {
                depositStalled = true;
            }
        }
        lastInsertTick = now;
    }

    private static void finishDeposit(MinecraftClient client, boolean closeScreen) {
        Config cfg = Manager.get();

        // Primary signal: did any shift-click fail to actually move its item out? That means
        // the vault (or that item's stack slots) had no room — real reason to rotate vaults.
        boolean vaultLooksFull = depositStalled;

        // Fallback: every single vault-side slot reports occupied. Rarely true in practice
        // (decorative/filler slots, odd layouts), but cheap to check as a second signal.
        if (!vaultLooksFull && depositHandler != null) {
            int vaultSlotCount = depositHandler.slots.size() - PLAYER_INV_SIZE;
            boolean allOccupied = true;
            for (int i = vaultSlotCount; i < depositHandler.slots.size(); i++) {
                if (depositHandler.getSlot(i).getStack().isEmpty()) {
                    allOccupied = false;
                    break;
                }
            }
            vaultLooksFull = allOccupied;
        }

        List<Integer> vaults = VaultUtils.parseSlots(cfg.vaultSlots);

        // Track how many configured vaults have reported full back-to-back. A successful
        // deposit resets the streak; once every configured vault has been tried and found
        // full in a row, stop auto-triggering instead of endlessly cycling through all of them.
        if (vaultLooksFull) {
            consecutiveFullVaults++;
        } else {
            consecutiveFullVaults = 0;
        }

        boolean justStoppedAll = vaultLooksFull && !vaults.isEmpty() && !allVaultsFull
                && consecutiveFullVaults >= vaults.size();
        if (justStoppedAll) allVaultsFull = true;

        if (closeScreen) {
            if (justStoppedAll) {
                client.player.sendMessage(Text.literal(
                        "§c[AutoPV] All configured vaults are full. Auto-store paused until your inventory frees up."), false);
            } else if (vaultLooksFull && !vaults.isEmpty()) {
                int nextVaultNumber = vaults.get((cycleIndex + 1) % vaults.size());
                sendFeedback(client, cfg, cfg.msgVaultFull, currentVaultNumber, nextVaultNumber);
            } else if (!vaultLooksFull) {
                sendFeedback(client, cfg, cfg.msgClosingVault, currentVaultNumber, -1);
            }
        }

        // If the vault couldn't take everything, move to the next configured vault next time.
        if (vaultLooksFull) cycleIndex++;

        // Close via the player entity (not client.setScreen(null) directly) so the
        // CloseHandledScreenC2SPacket actually reaches the server. Skipping that packet
        // left the server thinking the vault container was still open, which blocked
        // opening the next vault and required manually opening another container
        // (e.g. a chest/ender chest) to force a resync.
        if (closeScreen) client.player.closeHandledScreen();

        depositing = false;
        depositHandler = null;
        depositQueue = new ArrayDeque<>();
        depositStalled = false;
        lastInsertTick = -1;
    }

    /** Sends one of the configured feedback templates, substituting {vault} and {next}. */
    private static void sendFeedback(MinecraftClient client, Config cfg, String template, int vaultNumber, int nextVaultNumber) {
        if (!cfg.actionFeedbackEnabled || template == null || template.isBlank() || client.player == null) return;
        String message = template
                .replace("{vault}", String.valueOf(vaultNumber))
                .replace("{next}", String.valueOf(nextVaultNumber));
        client.player.sendMessage(Text.literal(message), false);
    }

    /**
     * CUSTOM mode stores an item if either its registry id (e.g. "minecraft:cod") is in
     * cfg.itemWhitelist, or its displayed name matches (case-insensitive) an entry in
     * cfg.itemNameWhitelist. Manage both with /asv add / /asv add name / /asv remove /
     * /asv remove name / /asv list.
     */
    private static boolean shouldStore(Config cfg, ItemStack stack) {
        return switch (cfg.mode) {
            case OFF -> false;
            case ALL -> true;
            case CUSTOM -> {
                String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                if (cfg.itemWhitelist.contains(id)) yield true;

                String displayName = stack.getName().getString();
                for (String name : cfg.itemNameWhitelist) {
                    if (name.equalsIgnoreCase(displayName)) yield true;
                }
                yield false;
            }
        };
    }

    private static int countOccupied(PlayerInventory inv, Set<Integer> ignored) {
        int count = 0;
        for (int i = 0; i < PLAYER_INV_SIZE; i++) {
            if (ignored.contains(i)) continue;
            if (!inv.getStack(i).isEmpty()) count++;
        }
        return count;
    }

    /**
     * Converts cfg.ignoredSlots (survival-inventory numbering: main inv 9-35, hotbar 36-44)
     * into container-relative indices (0-35: main 0-26, hotbar 27-35) — the layout a vault's
     * ScreenHandler uses when it appends the player's own inventory via the standard
     * addPlayerInventory helper (main storage rows first, then the hotbar row). Only valid
     * for use against a vault ScreenHandler's embedded player-inventory block (onScreenOpen),
     * NOT against PlayerInventory itself — see ignoredInventoryIndices for that. Anything
     * outside 9-44 (armor, offhand, crafting) is meaningless here and gets dropped.
     */
    private static Set<Integer> ignoredContainerIndices(Config cfg) {
        return VaultUtils.parseSlots(cfg.ignoredSlots).stream()
                .map(n -> n - 9)
                .filter(n -> n >= 0 && n < PLAYER_INV_SIZE)
                .collect(Collectors.toSet());
    }

    /**
     * Converts cfg.ignoredSlots (survival-inventory numbering: main inv 9-35, hotbar 36-44)
     * into raw PlayerInventory indices (PlayerInventory#getStack), where — unlike the
     * container-relative layout above — the hotbar comes first (0-8) and main storage
     * follows (9-35). Used by countOccupied, which reads straight from PlayerInventory.
     */
    private static Set<Integer> ignoredInventoryIndices(Config cfg) {
        return VaultUtils.parseSlots(cfg.ignoredSlots).stream()
                .map(VaultManager::survivalSlotToInventoryIndex)
                .filter(n -> n >= 0 && n < PLAYER_INV_SIZE)
                .collect(Collectors.toSet());
    }

    /** Survival-inventory slot id (9-44) -> raw PlayerInventory index (0-35), or -1 if out of range. */
    private static int survivalSlotToInventoryIndex(int slotId) {
        if (slotId >= 9 && slotId <= 35) return slotId;      // main storage: id doubles as the inventory index
        if (slotId >= 36 && slotId <= 44) return slotId - 36; // hotbar: inventory indices 0-8
        return -1;
    }
}
