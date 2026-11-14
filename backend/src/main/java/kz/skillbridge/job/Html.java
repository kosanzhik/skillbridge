package kz.skillbridge.job;

/** Converts vacancy HTML to plain text with line breaks preserved for section detection. */
public final class Html {

    private Html() {
    }

    public static String toText(String html) {
        if (html == null) {
            return null;
        }
        String s = html
                .replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div|/ul|/ol|p|li|h[1-6])\\b[^>]*>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&laquo;", "«").replace("&raquo;", "»")
                .replace("&mdash;", "—").replace("&ndash;", "–").replace("&amp;", "&");
        return s.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
