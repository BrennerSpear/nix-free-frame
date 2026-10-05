package works.tycho.frame;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import static org.junit.Assert.*;

public class AlbumHttpsTest {
    private static final String URL = "https://photos.google.com/share/synthetic";
    private static final class Response extends HttpsURLConnection {
        int status = 200, size = -1;
        String location;
        byte[] bytes = {1,2,3};
        boolean disconnected;
        Response() throws Exception { super(new URL(URL)); }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() { }
        public String getCipherSuite() { return "test"; }
        public Certificate[] getLocalCertificates() { return null; }
        public Certificate[] getServerCertificates() { return null; }
        public int getResponseCode() { return status; }
        public int getContentLength() { return size; }
        public String getHeaderField(String name) { return name.equals("Location") ? location : null; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
    }
    private static void fails(Response response, String body, int limit, String code) throws Exception {
        try { AlbumHttps.transfer(URL, body, false, new ByteArrayOutputStream(), limit, url -> response); fail(); }
        catch (IOException expected) { assertTrue(expected instanceof AlbumHttps.Failure); assertEquals(code, ((AlbumHttps.Failure)expected).code); assertNull(expected.getCause()); }
        assertTrue(response.disconnected);
    }
    @Test public void responseByteLimitEnforcedWithoutContentLength() throws Exception {
        fails(new Response(), null, 2, "limit");
        Response response = new Response(); response.size = 100; fails(response, null, 10, "limit");
        Response empty = new Response(); empty.bytes = new byte[0]; fails(empty, null, 10, "network");
    }
    @Test public void redirectsRevalidateEveryHopAndPostNeverRedirects() throws Exception {
        Response response = new Response(); response.status = 302; response.location = "https://attacker.test/";
        fails(response, null, 10, "redirect");
        response.location = "http://photos.google.com/share/synthetic"; fails(response, null, 10, "redirect");
        response.location = URL; fails(response, "private body", 10, "redirect");
        AtomicInteger count = new AtomicInteger();
        try { AlbumHttps.transfer(URL, null, false, new ByteArrayOutputStream(), 10, url -> { count.incrementAndGet(); return response; }); fail(); }
        catch (IOException expected) { assertEquals(6, count.get()); }
    }
    @Test public void errorsExposeOnlyStableCodes() throws Exception {
        IOException[] errors = {new javax.net.ssl.SSLHandshakeException("private URL"), new java.net.SocketTimeoutException("private URL"), new IOException("private URL")};
        String[] codes = {"tls", "timeout", "network"};
        for (int i = 0; i < errors.length; i++) {
            final IOException error = errors[i];
            try { AlbumHttps.transfer(URL, null, false, new ByteArrayOutputStream(), 10, url -> { throw error; }); fail(); }
            catch (AlbumHttps.Failure failure) { assertEquals(codes[i],failure.code); assertEquals("Album HTTPS " + codes[i],failure.getMessage()); assertNull(failure.getCause()); }
        }
        Response response = new Response(); response.status = 403; fails(response, null, 10, "http");
    }
    @Test public void validResponseClosesConnectionAndDisablesImplicitRedirects() throws Exception {
        Response response = new Response(); ByteArrayOutputStream output = new ByteArrayOutputStream();
        AlbumHttps.transfer(URL, null, false, output, 3, url -> response);
        assertArrayEquals(response.bytes, output.toByteArray()); assertTrue(response.disconnected);
        assertFalse(response.getInstanceFollowRedirects()); assertEquals(15000, response.getReadTimeout());
    }
}
