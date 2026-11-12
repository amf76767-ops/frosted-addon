package com.manu.frosted.update;

/** Loose semantic-version comparison: "v1.4.0" > "1.3.1" > "1.3.1-beta". */
public final class Version {
    private Version() {}

    public static String normalize(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    public static int compare(String a, String b) {
        a = normalize(a);
        b = normalize(b);

        String[] pa = split(a);
        String[] pb = split(b);
        int[] na = numbers(pa[0]);
        int[] nb = numbers(pb[0]);

        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? na[i] : 0;
            int y = i < nb.length ? nb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }

        // same numbers: a release beats a pre-release (1.4.0 > 1.4.0-beta)
        boolean preA = !pa[1].isEmpty();
        boolean preB = !pb[1].isEmpty();
        if (preA != preB) return preA ? -1 : 1;
        return pa[1].compareTo(pb[1]);
    }

    private static String[] split(String v) {
        int cut = -1;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '-' || c == '+') {
                cut = i;
                break;
            }
        }
        return cut < 0 ? new String[] {v, ""} : new String[] {v.substring(0, cut), v.substring(cut + 1)};
    }

    private static int[] numbers(String core) {
        if (core.isEmpty()) return new int[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            try {
                out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
