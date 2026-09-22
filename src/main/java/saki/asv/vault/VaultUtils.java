package saki.asv.vault;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class VaultUtils {

    /**
     * Parses "1-10", "1,2,5,6,7", or a mix like "1-3,5,7-9" into an ordered,
     * de-duplicated list. Invalid tokens are silently skipped.
     */
    public static List<Integer> parseSlots(String raw) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return new ArrayList<>(result);

        for (String part : raw.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                try {
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    if (start <= end) {
                        for (int i = start; i <= end; i++) result.add(i);
                    } else {
                        for (int i = start; i >= end; i--) result.add(i);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed range
                }
            } else {
                try {
                    result.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {
                    // skip malformed token
                }
            }
        }
        return new ArrayList<>(result);
    }
}
