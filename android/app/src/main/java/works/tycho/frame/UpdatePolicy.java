package works.tycho.frame;

import java.io.*;
import java.net.URL;
import java.util.*;

/** Validation independent of Android's installation privileges. */
public final class UpdatePolicy {
 public static final String PACKAGE="works.tycho.frame";
 public static final long MAX_BYTES=20L*1024*1024;
 public static final class Failure extends IOException {
  public final String code;
  public Failure(String code){super(code);this.code=code;}
 }
 public static URL manifest(int schema,String pkg,int version,long size,String sha,String path,URL base,int installed)throws Exception{
  if(schema!=1||!PACKAGE.equals(pkg))throw new Failure("package");
  if(version<=installed)throw new Failure("version");
  if(size<1||size>MAX_BYTES)throw new Failure("download");
  if(sha==null||!sha.matches("[0-9a-f]{64}"))throw new Failure("hash");
  if(!("/app-updates/"+sha+".apk").equals(path))throw new Failure("download");
  return sameOrigin(base,new URL(base,path));
 }
 public static URL sameOrigin(URL base,URL target)throws Failure{
  if(!Arrays.asList("http","https").contains(base.getProtocol())||base.getUserInfo()!=null||target.getUserInfo()!=null||target.getRef()!=null
    ||!base.getProtocol().equals(target.getProtocol())||!base.getHost().equalsIgnoreCase(target.getHost())||port(base)!=port(target))throw new Failure("download");
  return target;
 }
 private static int port(URL url){return url.getPort()<0?url.getDefaultPort():url.getPort();}
 public static void copy(InputStream in,File target,long expected,String sha)throws Exception{
  boolean valid=false;
  try{
   long size=0,deadline=System.nanoTime()+120L*1000000000;
   try(FileOutputStream out=new FileOutputStream(target)){
    byte[] buf=new byte[32768];int n;
    while((n=in.read(buf))!=-1){size+=n;if(System.nanoTime()>deadline||size>expected||size>MAX_BYTES)throw new Failure("download");out.write(buf,0,n);}
    out.getFD().sync();
   }
   if(size!=expected)throw new Failure("download");
   if(!sha.equals(CacheStore.digest(target)))throw new Failure("hash");
   valid=true;
  }finally{if(!valid)target.delete();}
 }
 public static void archive(String pkg,int version,int expectedVersion,int installedVersion,String[] signatures,String[] installedSignatures)throws Failure{
  if(!PACKAGE.equals(pkg))throw new Failure("package");
  if(version!=expectedVersion||version<=installedVersion)throw new Failure("version");
  if(signatures==null||installedSignatures==null||signatures.length==0||installedSignatures.length==0)throw new Failure("signature");
  String[] a=signatures.clone(),b=installedSignatures.clone();Arrays.sort(a);Arrays.sort(b);
  if(!Arrays.equals(a,b))throw new Failure("signature");
 }
 private UpdatePolicy(){}
}
