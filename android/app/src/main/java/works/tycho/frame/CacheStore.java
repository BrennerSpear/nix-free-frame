package works.tycho.frame;
import java.io.*;
import java.security.*;

/** Writes become visible only after their expected digest is verified. */
public final class CacheStore {
 public static String digest(File file) throws Exception {
  MessageDigest md = MessageDigest.getInstance("SHA-256");
  try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[32768]; int n; while ((n=in.read(b))!=-1) md.update(b,0,n); }
  StringBuilder s=new StringBuilder(); for(byte b:md.digest()) s.append(String.format("%02x",b & 255)); return s.toString();
 }
 public static void verifiedCopy(InputStream in, File target, String sha) throws Exception {
  File tmp = new File(target.getParentFile(),target.getName()+".part");
  try {
   try(OutputStream out = new FileOutputStream(tmp)) { byte[] b=new byte[32768]; int n; long size=0; while((n=in.read(b))!=-1) {size+=n;if(size>32*1024*1024)throw new IOException("Image too large");out.write(b,0,n);} }
   if(!sha.equals(digest(tmp)))throw new IOException("Image checksum mismatch");
   if(!tmp.renameTo(target))throw new IOException("Cannot publish image");
  } finally { tmp.delete(); }
 }
 public static void publish(File target, byte[] data) throws IOException {
  File tmp=new File(target.getParentFile(),target.getName()+".part");
  try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.getFD().sync();}
  if(!tmp.renameTo(target))throw new IOException("Cannot publish manifest");
 }
}
