package saki.asv.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data holder, serialized to config/autopv.json via Gson.
 */
public class Config {

    public boolean notificationsEnabled = false;

    /** Send a chat alert once used slots reach this count. */
    public int milestoneAlertSlots = 36;

    public Mode mode = Mode.CUSTOM;

    /** Auto-store triggers once used inventory slots reach this value (0-36). */
    public int thresholdSlots = 36;

    /**
     * Which vault numbers to cycle through.
     * Accepts "1-10", "1,2,5,6,7", or a mix like "1-3,5,7-9".
     */
    public String vaultSlots = "1,2,5,6,7";

    /** Server command that opens a vault, without slash or number, e.g. "pv" -> sends "/pv 3". */
    public String vaultOpenCommand = "pv";

    /**
     * Inventory slots to never touch — never counted toward the used-slot total and
     * never shift-clicked into a vault. Uses the same numbering as the vanilla
     * survival inventory screen: main inventory is 9-35, hotbar is 36-44.
     * Accepts "36-44", "36,37,44", or a mix. Handy for keeping a tool/weapon
     * parked in a specific hotbar slot.
     */
    public String ignoredSlots = "";

    /**
     * Ticks to wait between each item shift-clicked into the vault (20 ticks = 1 second).
     * 0 = instant, same as before. A small delay makes the process visible and is
     * gentler on servers that scrutinize rapid inventory actions.
     */
    public int insertDelayTicks = 2;

    // --- chat feedback for open/close/full events (separate from the milestone alert above) ---
    public boolean actionFeedbackEnabled = true;

    /** Sent right after the open command fires. {vault} is replaced with the vault number. */
    public String msgOpeningVault = "[ASV] opening pv {vault}";

    /** Sent once the deposit finishes normally and the screen closes. */
    public String msgClosingVault = "[ASV] closing pv {vault}";

    /** Sent instead of the closing message when the vault couldn't take everything.
     * {vault} = the vault that was full, {next} = the vault about to be tried next. */
    public String msgVaultFull = "[ASV] pv {vault} full, moving to {next}";

    /**
     * Item IDs (e.g. "minecraft:cod", "minecraft:salmon") stored via CUSTOM mode.
     * Managed with /asv add <item>, /asv add hand, /asv remove <item>, /asv list.
     * Empty list + CUSTOM mode = nothing gets stored until you add something.
     */
    public List<String> itemWhitelist = new ArrayList<>();

    /**
     * Item display names stored via CUSTOM mode (case-insensitive exact match against
     * the item's shown name, e.g. "Cod", "Diamond Sword", or a custom/anvil-renamed name).
     * Lets you whitelist by name instead of registry id — handy for custom items whose
     * id you don't know, or items you've renamed. Managed with /asv add name <name>,
     * /asv add name hand, /asv remove name <name>, /asv list.
     */
    public List<String> itemNameWhitelist = new ArrayList<>();

    public enum Mode {
        OFF,
        ALL,
        CUSTOM
    }
}