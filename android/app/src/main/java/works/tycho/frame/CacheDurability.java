package works.tycho.frame;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/** Persist rename ordering before a committed manifest permits old-image removal. */
final class CacheDurability {
 static void syncDirectory(File directory)throws IOException{
  if(!directory.isDirectory())throw new IOException("Invalid cache directory");
  FileDescriptor descriptor=null;
  try{
   descriptor=Os.open(directory.getAbsolutePath(),OsConstants.O_RDONLY,0);
   Os.fsync(descriptor);
  }catch(ErrnoException e){throw new IOException("Cannot persist cache directory",e);}
  finally{if(descriptor!=null)try{Os.close(descriptor);}catch(ErrnoException e){throw new IOException("Cannot close cache directory",e);}}
 }
}
