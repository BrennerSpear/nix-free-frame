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
 @Test public void corruptImageRepairedBeforeManifestSwap() throws Exception {
  File dir=Files.createTempDirectory("frame-transaction").toFile(),staged=new File(dir,"staged"),image=new File(dir,"image.jpg"),manifest=new File(dir,"manifest.json");
  Files.write(staged.toPath(),"good".getBytes());Files.write(image.toPath(),"corrupt".getBytes());CacheStore.publish(manifest,"old manifest".getBytes());
  CacheStore.publishImage(staged,image,CacheStore.digest(staged));
  assertEquals("good",new String(Files.readAllBytes(image.toPath())));
  assertEquals("old manifest",new String(Files.readAllBytes(manifest.toPath())));
  CacheStore.publish(manifest,"new manifest".getBytes());assertEquals("new manifest",new String(Files.readAllBytes(manifest.toPath())));
 }
 @Test public void invalidStagedImageCannotReplaceLastGood() throws Exception {
  File dir=Files.createTempDirectory("frame-transaction").toFile(),staged=new File(dir,"staged"),image=new File(dir,"image.jpg"),manifest=new File(dir,"manifest.json");
  Files.write(staged.toPath(),"partial".getBytes());Files.write(image.toPath(),"good".getBytes());CacheStore.publish(manifest,"old manifest".getBytes());
  try{CacheStore.publishImage(staged,image,"invalid");fail();}catch(IOException expected){}
  assertEquals("good",new String(Files.readAllBytes(image.toPath())));assertEquals("old manifest",new String(Files.readAllBytes(manifest.toPath())));
 }
}
