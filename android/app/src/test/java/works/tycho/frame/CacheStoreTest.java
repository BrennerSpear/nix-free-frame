package works.tycho.frame;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
public class CacheStoreTest {
 @Test public void failedDownloadPreservesLastGoodFile() throws Exception {
  File dir=Files.createTempDirectory("frame-test").toFile(), target=new File(dir,"photo.jpg");
  Files.write(target.toPath(),"old".getBytes());
  try {CacheStore.verifiedCopy(new ByteArrayInputStream("bad".getBytes()),target,"00");fail();}catch(IOException expected){}
  assertEquals("old",new String(Files.readAllBytes(target.toPath()))); assertFalse(new File(dir,"photo.jpg.part").exists());
 }
 @Test public void verifiedDownloadPublishes() throws Exception {
  File dir=Files.createTempDirectory("frame-test").toFile(), target=new File(dir,"photo.jpg"), source=new File(dir,"source");
  Files.write(source.toPath(),"new".getBytes()); CacheStore.verifiedCopy(new FileInputStream(source),target,CacheStore.digest(source));
  assertEquals("new",new String(Files.readAllBytes(target.toPath())));
 }
}
