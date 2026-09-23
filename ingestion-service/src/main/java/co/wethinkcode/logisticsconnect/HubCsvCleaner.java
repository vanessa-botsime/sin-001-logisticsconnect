package co.wethinkcode.logisticsconnect;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HubCsvCleaner {

    // canonical spellings for provinces that appear multiple ways in the source data
    private static final Map<String, String> PROVINCE_ALIASES = Map.of(
        "kwazulu-natal", "KwaZulu-Natal",
        "kwa-zulu natal", "KwaZulu-Natal",
        "kwazulu natal", "KwaZulu-Natal"
    );

    private static final Set<String> TRUE_VALUES = Set.of("y", "yes", "1", "true");
    private static final Set<String> FALSE_VALUES = Set.of("n", "no", "0", "false");
    // anything else (unknown, N/A, blank, TBD, -, NaN) -> null

    public static List<HubRecord> loadAndClean(InputStream csvStream) throws IOException {
        List<HubRecord> raw = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header row
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                raw.add(cleanRow(parts));
            }
        }

        return deduplicate(raw);
    }

    private static HubRecord cleanRow(String[] parts) {
        String hubId = normalizeId(parts[0]);
        String province = normalizeProvince(parts[1]);
        String sortingCenter = normalizeName(parts[2]);
        Boolean active = normalizeBoolean(parts[3]);
        return new HubRecord(hubId, province, sortingCenter, active);
    }

    private static String normalizeId(String raw) {
        return raw.trim().toUpperCase();
    }

    private static String normalizeName(String raw) {
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return null;
        // Title Case each word
        String[] words = trimmed.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String normalizeProvince(String raw) {
        String cleaned = normalizeName(raw);
        if (cleaned == null) return null;
        String alias = PROVINCE_ALIASES.get(cleaned.toLowerCase());
        return alias != null ? alias : cleaned;
    }

    private static Boolean normalizeBoolean(String raw) {
        String v = raw.trim().toLowerCase();
        if (TRUE_VALUES.contains(v)) return true;
        if (FALSE_VALUES.contains(v)) return false;
        return null; // unknown, N/A, blank, etc.
    }

    /**
     * Groups rows by normalized sorting_center (the most stable real-world identity
     * in this dataset), then merges each group field-by-field: a later row's non-null
     * value overwrites an earlier one, but a null/unknown value never overwrites a
     * known one. The canonical hub_id is the lowest id in the group.
     */
    private static List<HubRecord> deduplicate(List<HubRecord> raw) {
        Map<String, List<HubRecord>> groups = new LinkedHashMap<>();
        for (HubRecord r : raw) {
            if (r.sortingCenter == null) continue; // can't dedup what we can't identify
            groups.computeIfAbsent(r.sortingCenter.toLowerCase(), k -> new ArrayList<>()).add(r);
        }

        List<HubRecord> result = new ArrayList<>();
        for (List<HubRecord> group : groups.values()) {
            group.sort(Comparator.comparing(r -> r.hubId));

            String canonicalId = group.get(0).hubId; // first-seen id = identity
            String province = null;
            Boolean active = null;

            for (HubRecord r : group) {
                if (r.province != null) province = r.province;
                if (r.active != null) active = r.active;
            }

            result.add(new HubRecord(canonicalId, province, group.get(0).sortingCenter, active));
        }
        return result;
    }
}