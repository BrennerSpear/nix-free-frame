package works.tycho.frame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** No credentials, cookies, permissive TLS, or automatic cross-host redirects. */
final class AlbumHttps {
    enum Code { tls, timeout, http, limit, redirect, network }
    static final class Failure extends IOException {
        final String code;
        Failure(Code code) { super("Album HTTPS " + code.name()); this.code = code.name(); }
    }
    static Failure classified(IOException error) {
        if (error instanceof Failure) return (Failure) error;
        if (error instanceof SSLException) return new Failure(Code.tls);
        if (error instanceof SocketTimeoutException) return new Failure(Code.timeout);
        return new Failure(Code.network);
    }
    static final int MAX_PHOTO_BYTES = 32 * 1024 * 1024;
    static void validateAlbumUrl(String value) throws IOException { checked(value, false); }
    static void validatePhotoUrl(String value) throws IOException { checked(value, true); }
    private static URL checked(String value, boolean photo) throws IOException {
        try {
            if (value == null || value.length() > 16384) throw new IOException();
            URL url = new URL(value);
            String host = url.getHost();
            boolean trusted = photo ? host.matches("lh[0-9]+\\.googleusercontent\\.com")
                    : host.equals("photos.google.com") || host.equals("photos.app.goo.gl");
            if (!url.getProtocol().equals("https") || !trusted || url.getUserInfo() != null
                    || (url.getPort() != -1 && url.getPort() != 443) || url.getRef() != null) throw new IOException();
            return url;
        } catch (Exception e) { throw new Failure(Code.redirect); }
    }
    static String text(String url, String body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        transfer(url, body, false, bytes, 8 * 1024 * 1024);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
    static void downloadPhoto(String url, File destination) throws IOException {
        boolean complete = false;
        try (FileOutputStream out = new FileOutputStream(destination)) {
            transfer(url, null, true, out, MAX_PHOTO_BYTES);
            out.getFD().sync(); complete = true;
        } finally { if (!complete) destination.delete(); }
    }
    interface Connections { HttpsURLConnection open(URL url) throws IOException; }
    private static void transfer(String value, String body, boolean photo, OutputStream output, int limit) throws IOException {
        transfer(value, body, photo, output, limit, url -> (HttpsURLConnection) url.openConnection());
    }
    static void transfer(String value, String body, boolean photo, OutputStream output, int limit, Connections connections) throws IOException {
        long deadline = System.nanoTime() + 90_000_000_000L;
        URL url = checked(value, photo);
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpsURLConnection connection = null;
            try {
                if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new Failure(Code.timeout);
                connection = connections.open(url);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 NixFreeFrame/2");
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (body != null) {
                    connection.setRequestMethod("POST"); connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                    connection.setRequestProperty("Origin", "https://photos.google.com");
                    byte[] data = body.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(data.length);
                    try (OutputStream out = connection.getOutputStream()) { out.write(data); }
                }
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    // RPC credentials stay on the exact endpoint; redirects are allowed only for GET.
                    if (body != null || redirects == 5) throw new Failure(Code.redirect);
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new Failure(Code.redirect);
                    try { url = checked(new URL(url, location).toString(), photo); }
                    catch (IOException invalid) { throw new Failure(Code.redirect); }
                    continue;
                }
                if (status != 200) throw new Failure(Code.http);
                if (connection.getContentLength() > limit) throw new Failure(Code.limit);
                int total = 0;
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[16384]; int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new Failure(Code.timeout);
                        if (read > limit - total) throw new Failure(Code.limit);
                        output.write(buffer, 0, read); total += read;
                    }
                }
                if (total == 0) throw new Failure(Code.network);
                return;
            } catch (IOException e) { throw classified(e); }
            finally { if (connection != null) connection.disconnect(); }
        }
        throw new Failure(Code.redirect);
    }
}
