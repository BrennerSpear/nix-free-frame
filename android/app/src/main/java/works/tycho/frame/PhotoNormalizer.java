package works.tycho.frame;

import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;

final class PhotoNormalizer {
 static String normalize(File source,File output)throws Exception{
  String month="";int orientation=1;
  try{ExifInterface exif=new ExifInterface(source.getPath());month=captureMonth(exif.getAttribute("DateTimeOriginal"));orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),options);
  if(options.outWidth<=0||options.outHeight<=0||options.outWidth>100000||options.outHeight>100000)throw new IOException("Invalid image dimensions");
  options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2560||options.outHeight/options.inSampleSize>2560)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;
  Bitmap bitmap=null,transformed=null;
  try{
   bitmap=BitmapFactory.decodeFile(source.getPath(),options);if(bitmap==null)throw new IOException("Cannot decode image");
   Matrix matrix=new Matrix();
   matrix.setValues(orientationMatrix(orientation));
   transformed=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
   try(FileOutputStream out=new FileOutputStream(output)){if(!transformed.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Cannot encode image");out.getFD().sync();}
   return month;
  }catch(OutOfMemoryError e){throw new IOException("Image memory limit");}
  finally{if(transformed!=null&&transformed!=bitmap)transformed.recycle();if(bitmap!=null)bitmap.recycle();}
 }
 static float[] orientationMatrix(int orientation){
  switch(orientation){
   case 2:return new float[]{-1,0,0,0,1,0,0,0,1};
   case 3:return new float[]{-1,0,0,0,-1,0,0,0,1};
   case 4:return new float[]{1,0,0,0,-1,0,0,0,1};
   case 5:return new float[]{0,1,0,1,0,0,0,0,1};
   case 6:return new float[]{0,-1,0,1,0,0,0,0,1};
   case 7:return new float[]{0,-1,0,-1,0,0,0,0,1};
   case 8:return new float[]{0,1,0,-1,0,0,0,0,1};
   default:return new float[]{1,0,0,0,1,0,0,0,1};
  }
 }
 static String captureMonth(String date){
  if(date==null||!date.matches("[0-9]{4}:(0[1-9]|1[0-2]):[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")||date.startsWith("0000"))return "";
  java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss",java.util.Locale.ROOT);format.setLenient(false);
  // EXIF is a civil timestamp: validate its calendar fields without device DST rules.
  format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
  try{format.parse(date);return date.substring(0,4)+"-"+date.substring(5,7);}catch(java.text.ParseException e){return "";}
 }
}
