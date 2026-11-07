package by.agro.launcher.mods;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;








public final class ModVersion implements Comparable<ModVersion> {

    public enum Match { YES, NO, UNKNOWN }

    private static final Pattern NUMERIC_CORE = Pattern.compile("\\d+(\\.\\d+)*");

    private final List<Integer> numbers;
    private final String preRelease;

    private ModVersion(List<Integer> numbers, String preRelease) {
        this.numbers = numbers;
        this.preRelease = preRelease;
    }


    public static ModVersion parse(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        int plus = value.indexOf('+');
        if (plus >= 0) value = value.substring(0, plus);
        String pre = "";
        int dash = value.indexOf('-');
        if (dash >= 0) {
            pre = value.substring(dash + 1);
            value = value.substring(0, dash);
        }
        if (!NUMERIC_CORE.matcher(value).matches()) return null;
        List<Integer> numbers = new ArrayList<>();
        for (String part : value.split("\\.")) {
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new ModVersion(numbers, pre);
    }

    int number(int index) {
        return index < numbers.size() ? numbers.get(index) : 0;
    }

    int size() {
        return numbers.size();
    }

    @Override
    public int compareTo(ModVersion other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int i = 0; i < length; i++) {
            int compared = Integer.compare(number(i), other.number(i));
            if (compared != 0) return compared;
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0;
        if (preRelease.isEmpty()) return 1;
        if (other.preRelease.isEmpty()) return -1;
        String[] mine = preRelease.split("\\.");
        String[] theirs = other.preRelease.split("\\.");
        for (int i = 0; i < Math.min(mine.length, theirs.length); i++) {
            boolean myNumber = mine[i].matches("\\d+");
            boolean theirNumber = theirs[i].matches("\\d+");
            int compared;
            if (myNumber && theirNumber) {
                compared = Long.compare(Long.parseLong(mine[i]), Long.parseLong(theirs[i]));
            } else if (myNumber) {
                compared = -1;
            } else if (theirNumber) {
                compared = 1;
            } else {
                compared = mine[i].compareTo(theirs[i]);
            }
            if (compared != 0) return compared;
        }
        return Integer.compare(mine.length, theirs.length);
    }


    public static Match matchesAnyFabric(String version, List<String> predicates) {
        if (predicates == null || predicates.isEmpty()) return Match.YES;
        boolean unknown = false;
        for (String predicate : predicates) {
            Match match = matchesFabric(version, predicate);
            if (match == Match.YES) return Match.YES;
            if (match == Match.UNKNOWN) unknown = true;
        }
        return unknown ? Match.UNKNOWN : Match.NO;
    }


    public static Match matchesFabric(String versionText, String predicate) {
        if (predicate == null) return Match.YES;
        String trimmed = predicate.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return Match.YES;
        ModVersion version = parse(versionText);
        if (version == null) return Match.UNKNOWN;
        for (String part : trimmed.split("\\s+")) {
            Match match = matchesFabricPart(version, part);
            if (match != Match.YES) return match;
        }
        return Match.YES;
    }

    private static Match matchesFabricPart(ModVersion version, String part) {
        if ("*".equals(part)) return Match.YES;
        String operator = "";
        for (String candidate : new String[]{">=", "<=", ">", "<", "=", "~", "^"}) {
            if (part.startsWith(candidate)) {
                operator = candidate;
                break;
            }
        }
        String target = part.substring(operator.length()).trim();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".x") || lower.endsWith(".*")) {
            if (!operator.isEmpty() && !"=".equals(operator)) return Match.UNKNOWN;
            ModVersion prefix = parse(target.substring(0, target.length() - 2));
            if (prefix == null) return Match.UNKNOWN;
            for (int i = 0; i < prefix.size(); i++) {
                if (version.number(i) != prefix.number(i)) return Match.NO;
            }
            return Match.YES;
        }
        ModVersion bound = parse(target);
        if (bound == null) return Match.UNKNOWN;
        int compared = version.compareTo(bound);
        return switch (operator) {
            case ">=" -> bool(compared >= 0);
            case "<=" -> bool(compared <= 0);
            case ">" -> bool(compared > 0);
            case "<" -> bool(compared < 0);
            case "~" -> bool(compared >= 0 && version.number(0) == bound.number(0)
                    && version.number(1) == bound.number(1));
            case "^" -> bool(compared >= 0 && version.number(0) == bound.number(0));
            default -> bool(compared == 0);
        };
    }





    public static Match matchesMaven(String versionText, String range) {
        if (range == null) return Match.YES;
        String value = range.trim();
        if (value.isEmpty() || "*".equals(value)) return Match.YES;
        if (value.charAt(0) != '[' && value.charAt(0) != '(') return Match.YES;
        ModVersion version = parse(versionText);
        if (version == null) return Match.UNKNOWN;
        boolean unknown = false;
        int index = 0;
        while (index < value.length()) {
            char open = value.charAt(index);
            if (open == ',' || Character.isWhitespace(open)) {
                index++;
                continue;
            }
            if (open != '[' && open != '(') return Match.UNKNOWN;
            int close = index + 1;
            while (close < value.length() && value.charAt(close) != ']' && value.charAt(close) != ')') close++;
            if (close >= value.length()) return Match.UNKNOWN;
            Match match = matchesMavenInterval(version, open, value.substring(index + 1, close), value.charAt(close));
            if (match == Match.YES) return Match.YES;
            if (match == Match.UNKNOWN) unknown = true;
            index = close + 1;
        }
        return unknown ? Match.UNKNOWN : Match.NO;
    }

    private static Match matchesMavenInterval(ModVersion version, char open, String body, char close) {
        int comma = body.indexOf(',');
        if (comma < 0) {
            ModVersion exact = parse(body);
            if (exact == null) return Match.UNKNOWN;
            return bool(version.compareTo(exact) == 0);
        }
        String lowText = body.substring(0, comma).trim();
        String highText = body.substring(comma + 1).trim();
        if (!lowText.isEmpty()) {
            ModVersion low = parse(lowText);
            if (low == null) return Match.UNKNOWN;
            int compared = version.compareTo(low);
            if (open == '[' ? compared < 0 : compared <= 0) return Match.NO;
        }
        if (!highText.isEmpty()) {
            ModVersion high = parse(highText);
            if (high == null) return Match.UNKNOWN;
            int compared = version.compareTo(high);
            if (close == ']' ? compared > 0 : compared >= 0) return Match.NO;
        }
        return Match.YES;
    }

    private static Match bool(boolean value) {
        return value ? Match.YES : Match.NO;
    }
}
