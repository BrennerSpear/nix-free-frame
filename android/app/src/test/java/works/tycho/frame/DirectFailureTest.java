package works.tycho.frame;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class DirectFailureTest {
 @Test public void neverRetainsExceptionTextOrCause(){
  DirectAlbumSync.Failure failure=new DirectAlbumSync.Failure("normalize",new IOException("private request detail"));
  assertEquals("normalize",failure.code);assertEquals("normalize",failure.getMessage());assertNull(failure.getCause());
  assertEquals("sync",DirectAlbumSync.safeCode("arbitrary private string",new IOException()));
 }
 @Test public void networkStageHasOnlyWhitelistedReason(){
  assertEquals("enumeration_tls",DirectAlbumSync.safeCode("enumeration",AlbumHttps.classified(new javax.net.ssl.SSLException("secret"))));
  assertEquals("download_timeout",DirectAlbumSync.safeCode("download",AlbumHttps.classified(new java.net.SocketTimeoutException("secret"))));
  assertEquals("cache",DirectAlbumSync.safeCode("cache",AlbumHttps.classified(new IOException("secret"))));
 }
}
