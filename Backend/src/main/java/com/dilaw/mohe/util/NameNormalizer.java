package com.dilaw.mohe.util;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive name normalization utility for Arabic and English names.
 * Handles spacing, special characters, Arabic character normalization,
 * name variations, and fuzzy matching preparation.
 */
public class NameNormalizer {

    // Patterns for normalization
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[.,;:!?/_\\-]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    
    // Arabic character mappings
    private static final Map<Character, Character> ARABIC_NORMALIZATION_MAP = new HashMap<>();
    static {
        // أ, إ, آ → ا
        ARABIC_NORMALIZATION_MAP.put('\u0623', '\u0627'); // أ
        ARABIC_NORMALIZATION_MAP.put('\u0625', '\u0627'); // إ
        ARABIC_NORMALIZATION_MAP.put('\u0622', '\u0627'); // آ
        
        // ى → ي
        ARABIC_NORMALIZATION_MAP.put('\u0649', '\u064A'); // ى
        
        // ة → ه
        ARABIC_NORMALIZATION_MAP.put('\u0629', '\u0647'); // ة
    }
    
    // Common prefix variations
    private static final Set<String> NAME_PREFIXES = Set.of("al", "el", "al-", "el-", "bin", "ibn", "abu", "umm", "abdo", "abdul");
    
    // Common name variations
    private static final Map<String, String> NAME_VARIATIONS = new HashMap<>();
    static {
        NAME_VARIATIONS.put("mohammed", "mohamed");
        NAME_VARIATIONS.put("mohamad", "mohamed");
        NAME_VARIATIONS.put("muhammad", "mohamed");
        NAME_VARIATIONS.put("aly", "ali");
        NAME_VARIATIONS.put("sa'ad", "saad");
        NAME_VARIATIONS.put("saad", "saad");
        NAME_VARIATIONS.put("hussain", "hassan");
        NAME_VARIATIONS.put("hussein", "hassan");
        NAME_VARIATIONS.put("husain", "hassan");
        NAME_VARIATIONS.put("alsaud", "al saud");
        NAME_VARIATIONS.put("alsoud", "al saud");
        NAME_VARIATIONS.put("ahmad", "ahmed");
        NAME_VARIATIONS.put("mohd", "mohamed");
        NAME_VARIATIONS.put("abdo", "abdulrahman");
        NAME_VARIATIONS.put("abdul", "abdulrahman");
    }

    /**
     * Normalizes a name for comparison purposes.
     * Handles all spacing, special characters, and Arabic character normalization.
     */
    public static String normalize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        
        // Step 1: Remove diacritics (harakat)
        String normalized = removeDiacritics(name);
        
        // Step 2: Normalize Arabic characters
        normalized = normalizeArabicCharacters(normalized);
        
        // Step 3: Convert to lowercase for case-insensitive comparison
        normalized = normalized.toLowerCase();
        
        // Step 4: Replace special characters with spaces
        normalized = SPECIAL_CHARS.matcher(normalized).replaceAll(" ");
        
        // Step 5: Normalize spacing (remove multiple spaces, trim)
        normalized = normalizeSpacing(normalized);
        
        // Step 6: Remove non-alphanumeric characters (keep letters, numbers, spaces)
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("");
        
        // Step 7: Normalize name prefixes
        normalized = normalizePrefixes(normalized);
        
        // Step 8: Final spacing normalization
        normalized = normalizeSpacing(normalized);
        
        return normalized.trim();
    }

    /**
     * Normalizes a name and splits it into parts.
     */
    public static List<String> normalizeAndSplit(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Normalizes name parts and returns all possible orderings for matching.
     */
    public static Set<String> getAllPossibleOrderings(String name) {
        List<String> parts = normalizeAndSplit(name);
        if (parts.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<String> orderings = new HashSet<>();
        
        // Original order
        orderings.add(String.join(" ", parts));
        
        // Reverse order
        Collections.reverse(parts);
        orderings.add(String.join(" ", parts));
        Collections.reverse(parts); // Restore
        
        // All permutations if 3 parts or less
        if (parts.size() <= 3) {
            generatePermutations(parts, 0, orderings);
        }
        
        return orderings;
    }

    /**
     * Checks if two names match using comprehensive normalization.
     */
    public static boolean matches(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        
        String norm1 = normalize(name1);
        String norm2 = normalize(name2);
        
        // Exact match after normalization
        if (norm1.equals(norm2)) {
            return true;
        }
        
        // Check name variations
        if (areNameVariations(norm1, norm2)) {
            return true;
        }
        
        // Check if one is substring of another (for partial matches)
        if (norm1.contains(norm2) || norm2.contains(norm1)) {
            return true;
        }
        
        // Check name part matching
        return matchNameParts(norm1, norm2);
    }

    /**
     * Checks if two normalized names match by name parts.
     */
    private static boolean matchNameParts(String norm1, String norm2) {
        List<String> parts1 = normalizeAndSplit(norm1);
        List<String> parts2 = normalizeAndSplit(norm2);
        
        if (parts1.isEmpty() || parts2.isEmpty()) {
            return false;
        }
        
        // Check if all parts of shorter name exist in longer name
        List<String> shorter = parts1.size() <= parts2.size() ? parts1 : parts2;
        List<String> longer = parts1.size() > parts2.size() ? parts1 : parts2;
        
        int matches = 0;
        for (String part : shorter) {
            if (longer.contains(part)) {
                matches++;
            }
        }
        
        // Match if at least 2 parts match, or if single part and it matches
        return matches >= Math.min(2, shorter.size()) || 
               (shorter.size() == 1 && longer.contains(shorter.get(0)));
    }

    /**
     * Removes Arabic diacritics (harakat).
     */
    private static String removeDiacritics(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized).replaceAll("");
    }

    /**
     * Normalizes Arabic characters according to rules.
     */
    private static String normalizeArabicCharacters(String text) {
        if (text == null) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            Character replacement = ARABIC_NORMALIZATION_MAP.get(c);
            result.append(replacement != null ? replacement : c);
        }
        return result.toString();
    }

    /**
     * Normalizes spacing (removes multiple spaces, trims).
     */
    private static String normalizeSpacing(String text) {
        if (text == null) {
            return "";
        }
        return MULTIPLE_SPACES.matcher(text.trim()).replaceAll(" ");
    }

    /**
     * Normalizes name prefixes (Al, El, Bin, etc.).
     */
    private static String normalizePrefixes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String lower = text.toLowerCase();
        for (String prefix : NAME_PREFIXES) {
            // Replace "al-" or "al " with "al "
            if (lower.startsWith(prefix + "-")) {
                text = "al " + text.substring(prefix.length() + 1);
                lower = text.toLowerCase();
            } else if (lower.startsWith(prefix + " ") && !prefix.equals("al")) {
                // Keep other prefixes as-is, just ensure space after
                text = prefix + " " + text.substring(prefix.length() + 1);
                lower = text.toLowerCase();
            }
        }
        
        return text;
    }

    /**
     * Checks if two names are known variations of each other.
     */
    private static boolean areNameVariations(String norm1, String norm2) {
        String var1 = NAME_VARIATIONS.getOrDefault(norm1, norm1);
        String var2 = NAME_VARIATIONS.getOrDefault(norm2, norm2);
        
        return var1.equals(var2) || 
               NAME_VARIATIONS.containsKey(norm1) && NAME_VARIATIONS.get(norm1).equals(norm2) ||
               NAME_VARIATIONS.containsKey(norm2) && NAME_VARIATIONS.get(norm2).equals(norm1);
    }

    /**
     * Generates all permutations of name parts.
     */
    private static void generatePermutations(List<String> parts, int start, Set<String> result) {
        if (start == parts.size() - 1) {
            result.add(String.join(" ", parts));
            return;
        }
        
        for (int i = start; i < parts.size(); i++) {
            Collections.swap(parts, start, i);
            generatePermutations(parts, start + 1, result);
            Collections.swap(parts, start, i); // Restore
        }
    }

    /**
     * Calculates Levenshtein distance between two strings.
     * Used for fuzzy matching.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        
        int len1 = s1.length();
        int len2 = s2.length();
        
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;
        
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[len1][len2];
    }

    /**
     * Checks if two names match within a Levenshtein distance threshold.
     */
    public static boolean fuzzyMatch(String name1, String name2, int maxDistance) {
        String norm1 = normalize(name1);
        String norm2 = normalize(name2);
        
        int distance = levenshteinDistance(norm1, norm2);
        return distance <= maxDistance;
    }

    /**
     * Gets similarity score between two names (0.0 to 1.0).
     */
    public static double similarity(String name1, String name2) {
        String norm1 = normalize(name1);
        String norm2 = normalize(name2);
        
        if (norm1.isEmpty() && norm2.isEmpty()) {
            return 1.0;
        }
        if (norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        int maxLen = Math.max(norm1.length(), norm2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        
        int distance = levenshteinDistance(norm1, norm2);
        return 1.0 - ((double) distance / maxLen);
    }
}

