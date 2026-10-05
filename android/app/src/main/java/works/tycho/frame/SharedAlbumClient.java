package works.tycho.frame;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared-link protocol follows the pinned extractor; unsupported metadata fails the whole sync. */
final class SharedAlbumClient {
    static final int MAX_PAGES = 100, MAX_ITEMS = 10000;
    private static final Pattern REQUEST = Pattern.compile("snAcKc[^}]*?request:\\s*\\[\\s*\"([A-Za-z0-9_-]+)\"\\s*,\\s*null\\s*,\\s*null\\s*,\\s*\"([A-Za-z0-9_-]+)\"", Pattern.DOTALL);
    interface Transport { String request(String url, String body) throws IOException; }
    private final Transport transport;
    SharedAlbumClient() { this(AlbumHttps::text); }
    SharedAlbumClient(Transport transport) { this.transport = transport; }
    static final class Item {
        final String id, url;
        final int width, height;
        final boolean isVideo;
        final long imageUpdateDate, albumAddDate;
        Item(String id, String url, int width, int height, boolean video, long updated, long added) {
            this.id = id; this.url = url; this.width = width; this.height = height;
            this.isVideo = video; this.imageUpdateDate = updated; this.albumAddDate = added;
        }
    }
    List<Item> fetch(String albumUrl) throws IOException {
        AlbumHttps.validateAlbumUrl(albumUrl);
        long deadline = System.nanoTime() + 300_000_000_000L;
        String html = transport.request(albumUrl, null);
        List<?> data = firstPage(html);
        Map<String,Item> items = new LinkedHashMap<>();
        append(items, data);
        String token = token(data);
        Matcher request = REQUEST.matcher(html);
        String album = null, auth = null;
        if (token != null) {
            if (!request.find()) throw new IOException("Missing album pagination request");
            album = request.group(1); auth = request.group(2);
        }
        Set<String> seen = new HashSet<>();
        int pages = 1;
        while (token != null) {
            if (pages++ >= MAX_PAGES || !seen.add(token) || System.nanoTime() > deadline || Thread.currentThread().isInterrupted())
                throw new IOException("Album pagination safety limit");
            String inner = "[" + quote(album) + "," + quote(token) + ",null," + quote(auth) + "]";
            String envelope = "[[[\"snAcKc\"," + quote(inner) + ",null,\"generic\"]]]";
            String endpoint = "https://photos.google.com/u/0/_/PhotosUi/data/batchexecute?rpcids=snAcKc&source-path=" + encode("/share/" + album);
            data = batchPage(transport.request(endpoint, "f.req=" + encode(envelope)));
            append(items, data); token = token(data);
        }
        if (items.isEmpty()) throw new IOException("Empty album retained previous cache");
        return new ArrayList<>(items.values());
    }
    static List<?> firstPage(String html) throws IOException {
        // Match callback boundaries as in the extractor, then parse only its literal data.
        String marker = "AF_initDataCallback(";
        int offset = 0, bestStart = -1, bestEnd = -1;
        while ((offset = html.indexOf(marker, offset)) >= 0) {
            int start = offset + marker.length();
            int end = html.indexOf(");</script>", start);
            if (end < 0) throw JsDataParser.bad();
            if (end - start > bestEnd - bestStart) { bestStart = start; bestEnd = end; }
            offset = end + 11;
        }
        if (bestStart < 0) throw JsDataParser.bad();
        Object parsed = JsDataParser.parse(html.substring(bestStart, bestEnd));
        if (!(parsed instanceof Map)) throw JsDataParser.bad();
        return list(((Map<?,?>) parsed).get("data"));
    }
    static List<?> batchPage(String text) throws IOException {
        for (String line : text.split("\n")) {
            if (!line.trim().startsWith("[[")) continue;
            List<?> outer = list(JsDataParser.parse(line.trim()));
            for (Object candidate : outer) {
                if (!(candidate instanceof List)) continue;
                List<?> entry = (List<?>) candidate;
                if (entry.size() >= 3 && "wrb.fr".equals(entry.get(0)) && "snAcKc".equals(entry.get(1)) && entry.get(2) instanceof String)
                    return list(JsDataParser.parse((String) entry.get(2)));
            }
            throw JsDataParser.bad();
        }
        throw JsDataParser.bad();
    }
    private static String token(List<?> data) throws IOException {
        if (data.size() < 3) throw JsDataParser.bad();
        Object token = data.get(2);
        if (token == null) return null;
        if (!(token instanceof String) || ((String) token).length() > 16384) throw JsDataParser.bad();
        return ((String) token).isEmpty() ? null : (String) token;
    }
    private static void append(Map<String,Item> items, List<?> data) throws IOException {
        if (data.size() < 3) throw JsDataParser.bad();
        List<?> entries = list(data.get(1));
        if (entries.isEmpty()) throw new IOException("Empty album page");
        for (Object raw : entries) {
            List<?> entry = list(raw);
            if (entry.size() < 6) throw JsDataParser.bad();
            String id = string(entry.get(0));
            if (id.length() > 1024) throw JsDataParser.bad();
            List<?> detail = list(entry.get(1));
            if (detail.size() < 3) throw JsDataParser.bad();
            String url = string(detail.get(0)); AlbumHttps.validatePhotoUrl(url);
            long width = integer(detail.get(1)), height = integer(detail.get(2));
            if (width < 1 || height < 1 || width > 100000 || height > 100000) throw JsDataParser.bad();
            long updated = integer(entry.get(2)), added = integer(entry.get(5));
            boolean video = entry.size() > 9 && entry.get(9) instanceof Map && ((Map<?,?>) entry.get(9)).containsKey("76647426");
            if (!items.containsKey(id)) items.put(id, new Item(id, url, (int) width, (int) height, video, updated, added));
            if (items.size() > MAX_ITEMS) throw new IOException("Album item limit exceeded");
        }
    }
    private static List<?> list(Object value) throws IOException { if (!(value instanceof List)) throw JsDataParser.bad(); return (List<?>) value; }
    private static String string(Object value) throws IOException { if (!(value instanceof String) || ((String)value).isEmpty()) throw JsDataParser.bad(); return (String)value; }
    private static long integer(Object value) throws IOException {
        if (!(value instanceof Number)) throw JsDataParser.bad();
        double n = ((Number)value).doubleValue();
        if (n != Math.rint(n) || Math.abs(n) > 9007199254740991d) throw JsDataParser.bad();
        return (long)n;
    }
    private static String encode(String value) throws IOException { return URLEncoder.encode(value, "UTF-8"); }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
