package works.tycho.frame;

import org.junit.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SharedAlbumClientTest {
    private static final String ALBUM = "https://photos.google.com/share/synthetic?key=synthetic";
    private static String item(String id) { return "[\"" + id + "\",[\"https://lh3.googleusercontent.com/synthetic\",800,600],123,null,null,456]"; }
    private static String data(String items, String token) { return "[null,[" + items + "]," + token + "]"; }
    private static String html(String data) { return "<script>AF_initDataCallback({key:'ds:1',data:" + data + ",sideChannel:{}});</script> snAcKc request:[\"album\",null,null,\"auth\"]"; }
    private static String batch(String data) { return ")]}'\n123\n[[\"wrb.fr\",\"snAcKc\"," + SharedAlbumClient.quote(data) + "]]\n"; }
    private interface Bad { void run() throws Exception; }
    private static void rejects(Bad run) throws Exception { try { run.run(); fail("Expected rejection"); } catch (IOException expected) { } }
    @Test public void fullPaginationAndDeduplication() throws Exception {
        AtomicInteger count = new AtomicInteger();
        SharedAlbumClient client = new SharedAlbumClient((url, body) -> {
            if (count.getAndIncrement() == 0) return html(data(item("one"), "\"next\""));
            assertTrue(url.startsWith("https://photos.google.com/u/0/_/PhotosUi/data/batchexecute?"));
            assertTrue(body.startsWith("f.req="));
            return batch(data(item("one") + "," + item("two"), "null"));
        });
        List<SharedAlbumClient.Item> result = client.fetch(ALBUM);
        assertEquals(2, result.size()); assertEquals("two", result.get(1).id); assertEquals(2, count.get());
    }
    @Test public void repeatedTokenAndMalformedTokenFailWholeEnumeration() throws Exception {
        rejects(() -> new SharedAlbumClient((url, body) -> body == null ? html(data(item("one"), "\"same\"")) : batch(data(item("two"), "\"same\""))).fetch(ALBUM));
        rejects(() -> new SharedAlbumClient((url, body) -> html(data(item("one"), "42"))).fetch(ALBUM));
        rejects(() -> new SharedAlbumClient((url, body) -> body == null ? html(data(item("one"), "\"next\"")) : batch(data(item("two"), "[]"))).fetch(ALBUM));
    }
    @Test public void malformedOrEmptyLaterPageNeverReturnsFirstPage() throws Exception {
        rejects(() -> new SharedAlbumClient((url, body) -> body == null ? html(data(item("one"), "\"next\"")) : batch(data("", "null"))).fetch(ALBUM));
        rejects(() -> new SharedAlbumClient((url, body) -> body == null ? html(data(item("one"), "\"next\"")) : "login required").fetch(ALBUM));
        rejects(() -> new SharedAlbumClient((url, body) -> html(data(item("one") + ",[null]", "null"))).fetch(ALBUM));
        rejects(() -> new SharedAlbumClient((url, body) -> html(data("", "null"))).fetch(ALBUM));
    }
    @Test public void pageLimitAndFailureAreClosed() throws Exception {
        AtomicInteger count = new AtomicInteger();
        rejects(() -> new SharedAlbumClient((url, body) -> {
            int i = count.incrementAndGet();
            String page = data(item("item" + i), "\"token" + i + "\"");
            return body == null ? html(page) : batch(page);
        }).fetch(ALBUM));
        assertEquals(100, count.get());
        rejects(() -> new SharedAlbumClient((url, body) -> { if (body == null) return html(data(item("one"), "\"next\"")); throw new IOException("Synthetic interruption"); }).fetch(ALBUM));
    }
    @Test public void literalsAreDataOnlyAndBounded() throws Exception {
        assertNotNull(JsDataParser.parse("{key:'x',data:[1,2,],sideChannel:{},}"));
        rejects(() -> JsDataParser.parse("{data:run()}"));
        rejects(() -> JsDataParser.parse("[[1]"));
        rejects(() -> JsDataParser.parse("{x:{}}garbage"));
        rejects(() -> JsDataParser.parse("{x:1,x:2}"));
        StringBuilder deep = new StringBuilder(); for (int i = 0; i < 100; i++) deep.append('[');
        rejects(() -> JsDataParser.parse(deep.toString()));
        assertEquals("ab", JsDataParser.parse("'a\\x62'"));
    }
    @Test public void endpointAllowlistRejectsCredentialLeakRoutes() throws Exception {
        AlbumHttps.validateAlbumUrl(ALBUM);
        AlbumHttps.validateAlbumUrl("https://photos.app.goo.gl/synthetic");
        AlbumHttps.validatePhotoUrl("https://lh3.googleusercontent.com/synthetic=d");
        for (String url : new String[]{"http://photos.google.com/share/x", "https://photos.google.com.evil.test/x", "https://user:pass@photos.google.com/x", "https://photos.google.com:8443/x", "https://127.0.0.1/x", "https://photos.google.com/x#secret"}) rejects(() -> AlbumHttps.validateAlbumUrl(url));
        rejects(() -> AlbumHttps.validatePhotoUrl("https://googleusercontent.com.evil.test/x"));
        rejects(() -> AlbumHttps.validatePhotoUrl("https://photos.google.com/x"));
    }
}
