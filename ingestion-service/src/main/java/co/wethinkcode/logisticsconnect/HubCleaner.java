package co.wethinkcode.logisticsconnect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the messy raw CSV rows into clean, de-duplicated hubs. */
public class HubCleaner {

    private static final Set<String> PLACEHOLDERS = Set.of("", "n/a", "na", "tbd", "unknown", "-", "nan", "null");

    // Key = lower-case letters only, so "Kwa-Zulu Natal", "KwaZulu Natal" and "kwazulu-natal" all match.
    private static final Map<String, String> PROVINCES = Map.of(
            "gauteng", "Gauteng",
            "westerncape", "Western Cape",
            "easterncape", "Eastern Cape",
            "northerncape", "Northern Cape",
            "kwazulunatal", "KwaZulu-Natal",
            "freestate", "Free State",
            "limpopo", "Limpopo",
            "mpumalanga", "Mpumalanga",
            "northwest", "North West");

    private record Row(String hubId, String province, String center, Boolean active) {
    }

    public static List<Hub> clean(List<String[]> rawRows) {
        List<Row> rows = new ArrayList<>();
        for (String[] raw : rawRows) {
            Row row = cleanRow(raw);
            if (row != null) {
                rows.add(row);
            }
        }
        return deduplicate(rows);
    }

    // ---------- step 1: clean one row ----------

    /** Columns by position: hub_id, province, sorting_center, active. */
    private static Row cleanRow(String[] fields) {
        String id = tidy(field(fields, 0));
        if (id == null || !id.toUpperCase().matches("H-\\d{1,9}")) {
            System.err.println("Skipping row with invalid hub id: " + Arrays.toString(fields));
            return null;
        }
        return new Row(id.toUpperCase(),
                cleanProvince(field(fields, 1)),
                cleanName(field(fields, 2)),
                parseBoolean(field(fields, 3)));
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : null;
    }

    /** Trims, collapses repeated spaces, and turns placeholders (N/A, TBD, blank...) into null. */
    private static String tidy(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return PLACEHOLDERS.contains(cleaned.toLowerCase()) ? null : cleaned;
    }

    private static String cleanProvince(String value) {
        String tidy = tidy(value);
        if (tidy == null) {
            return null;
        }
        String key = tidy.toLowerCase().replaceAll("[^a-z]", "");
        return PROVINCES.getOrDefault(key, titleCase(tidy));
    }

    private static String cleanName(String value) {
        String tidy = tidy(value);
        return tidy == null ? null : titleCase(tidy);
    }

    private static String titleCase(String text) {
        String[] words = text.toLowerCase().split(" ");
        for (int i = 0; i < words.length; i++) {
            words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
        }
        return String.join(" ", words);
    }

    /** Y/yes/true/1 -> true, N/no/false/0 -> false, anything else (unknown, N/A, ...) -> null. */
    private static Boolean parseBoolean(String value) {
        String tidy = tidy(value);
        if (tidy == null) {
            return null;
        }
        return switch (tidy.toLowerCase()) {
            case "y", "yes", "true", "t", "1" -> Boolean.TRUE;
            case "n", "no", "false", "f", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    // ---------- step 2: merge duplicates ----------

    private static List<Hub> deduplicate(List<Row> rows) {
        // For each sorting center, which provinces is it listed under?
        Map<String, Set<String>> provincesByCenter = new HashMap<>();
        for (Row row : rows) {
            if (row.center() != null && row.province() != null) {
                provincesByCenter
                        .computeIfAbsent(row.center().toLowerCase(), key -> new HashSet<>())
                        .add(row.province());
            }
        }

        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) {
            groups.computeIfAbsent(groupKey(row, provincesByCenter), key -> new ArrayList<>()).add(row);
        }

        List<Hub> hubs = new ArrayList<>();
        for (List<Row> group : groups.values()) {
            hubs.add(merge(group));
        }
        hubs.sort(Comparator.comparingInt(hub -> idNumber(hub.hubId())));
        return hubs;
    }

    /**
     * Two rows are the same hub when they have the same sorting center in the same province.
     * A row with a blank province joins that center's province if the center only ever
     * appears in one province; a row with no center name can't be matched, so it stays alone.
     */
    private static String groupKey(Row row, Map<String, Set<String>> provincesByCenter) {
        if (row.center() == null) {
            return "ID:" + row.hubId();
        }
        String centerKey = row.center().toLowerCase();
        String province = row.province();
        if (province == null) {
            Set<String> known = provincesByCenter.get(centerKey);
            if (known != null && known.size() == 1) {
                province = known.iterator().next();
            }
        }
        return centerKey + "|" + (province == null ? "?" : province);
    }

    private static Hub merge(List<Row> group) {
        group.sort(Comparator.comparingInt(row -> idNumber(row.hubId())));
        Row canonical = group.get(0); // lowest ID = the original record

        List<String> aliases = new ArrayList<>();
        String province = null;
        String center = null;
        for (Row row : group) {
            if (row != canonical) {
                aliases.add(row.hubId());
            }
            if (province == null) {
                province = row.province();
            }
            if (center == null) {
                center = row.center();
            }
        }
        return new Hub(canonical.hubId(), province, center, decideActive(group), aliases);
    }

    /** Majority vote among known values; on a tie, the newest (highest ID) record wins. */
    private static Boolean decideActive(List<Row> groupOldestFirst) {
        int yes = 0;
        int no = 0;
        Boolean newest = null;
        for (Row row : groupOldestFirst) {
            if (row.active() == null) {
                continue;
            }
            if (row.active()) {
                yes++;
            } else {
                no++;
            }
            newest = row.active();
        }
        if (yes + no == 0) {
            return null;
        }
        return yes != no ? yes > no : newest;
    }

    private static int idNumber(String hubId) {
        return Integer.parseInt(hubId.substring(2));
    }
}
