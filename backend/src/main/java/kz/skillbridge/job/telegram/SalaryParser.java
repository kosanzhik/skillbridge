package kz.skillbridge.job.telegram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses salary strings like "400 000–600 000 KZT", "от 300 000 ₸", "до $2000". Unknown → nulls. */
final class SalaryParser {

    record Salary(Integer from, Integer to, String currency) {
        static final Salary NONE = new Salary(null, null, null);
    }

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s\\u00A0\\u202F.,]*\\d|\\d");

    private SalaryParser() {
    }

    static Salary parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Salary.NONE;
        }
        String s = raw.toLowerCase(Locale.ROOT);
        List<Integer> nums = new ArrayList<>();
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            String digits = m.group().replaceAll("[\\s\\u00A0\\u202F]", "").replaceAll("[.,]\\d{1,2}$", "")
                    .replaceAll("[.,]", "");
            int multiplier = s.substring(m.end()).matches("(?s)^\\s*(?:[kк](?!\\p{L})|тыс).*") ? 1000 : 1;
            try {
                nums.add(Math.multiplyExact(Integer.parseInt(digits), multiplier));
            } catch (NumberFormatException | ArithmeticException e) {
                // ignore implausible numbers
            }
        }
        if (nums.isEmpty()) {
            return Salary.NONE;
        }
        String currency = currency(s);
        boolean onlyTo = s.matches("(?s)^\\s*(?:до|up to|to)(?![\\p{L}]).*");
        if (nums.size() == 1) {
            return onlyTo ? new Salary(null, nums.get(0), currency) : new Salary(nums.get(0), null, currency);
        }
        return new Salary(Math.min(nums.get(0), nums.get(1)), Math.max(nums.get(0), nums.get(1)), currency);
    }

    private static String currency(String s) {
        if (s.contains("₸") || s.contains("kzt") || s.contains("тенге") || s.matches("(?s).*(?<!\\p{L})тг(?!\\p{L}).*")) {
            return "KZT";
        }
        if (s.contains("$") || s.contains("usd")) {
            return "USD";
        }
        if (s.contains("€") || s.contains("eur")) {
            return "EUR";
        }
        if (s.contains("₽") || s.contains("руб") || s.contains("rub")) {
            return "RUR";
        }
        return null;
    }
}
