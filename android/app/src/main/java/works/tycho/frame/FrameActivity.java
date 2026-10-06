package works.tycho.frame;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FrameActivity extends Activity {
 private final Handler main = new Handler();
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ExecutorService decoder=Executors.newSingleThreadExecutor();
 private boolean decoding=false;
 private final List<File> photos=new ArrayList<>();
 private ImageView image, alternate; private TextView status, dateLabel; private View nightCover; private File directory; private int index=0, interval=15;
 private boolean active=false, syncing=false, night=false;
 private static volatile boolean resumedForEvidence;
 static boolean isResumedForEvidence(){return resumedForEvidence;}
 private static final java.util.concurrent.atomic.AtomicBoolean syncLock=new java.util.concurrent.atomic.AtomicBoolean();
 private String observedRevision="";
 private Bitmap shown, retiring;
 private int generation=0;
 private Boolean preview=null;
 private long previewUntil=0, wakeUntil=0;
 private final Map<String,String> photoMonths=new HashMap<>();
 private final Runnable fadeDate=()->dateLabel.animate().alpha(0f).setDuration(500).start();
 private final Runnable endWake=()->updateNight();
 private final Runnable clearPreview=()->{preview=null;previewUntil=0;updateNight();};
 private final Runnable nightTick=new Runnable(){public void run(){if(active){updateNight();FrameEvidence.report(FrameActivity.this,night);main.postDelayed(this,60000);}}};
 private final Runnable cycle=new Runnable(){public void run(){if(active&&!night){showNext();main.postDelayed(this,interval*1000L);}}};
 private final Runnable refresh=new Runnable(){public void run(){if(active){scheduledSync();main.postDelayed(this,60000);}}};
 private final Runnable checkUpdate=new Runnable(){public void run(){if(active){startService(new Intent(FrameActivity.this,UpdateService.class));main.postDelayed(this,60000);}}};
 public void onCreate(Bundle state){
  super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  directory=playbackDirectory();directory.mkdirs();
  FrameLayout layout=new FrameLayout(this);layout.setBackgroundColor(Color.BLACK);
  image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);layout.addView(image,new FrameLayout.LayoutParams(-1,-1));
  alternate=new ImageView(this);alternate.setScaleType(ImageView.ScaleType.FIT_CENTER);alternate.setAlpha(0f);layout.addView(alternate,new FrameLayout.LayoutParams(-1,-1));
  dateLabel=new TextView(this);dateLabel.setTextColor(0xffe8e8e8);dateLabel.setTextSize(18);dateLabel.setShadowLayer(2,0,1,Color.BLACK);dateLabel.setAlpha(0f);
  FrameLayout.LayoutParams datePosition=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);datePosition.setMargins(dp(20),dp(16),dp(20),dp(16));layout.addView(dateLabel,datePosition);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);status.setTextSize(20);layout.addView(status,new FrameLayout.LayoutParams(-1,-1));setContentView(layout);
  nightCover=new View(this);nightCover.setBackgroundColor(Color.BLACK);nightCover.setVisibility(View.GONE);layout.addView(nightCover,new FrameLayout.LayoutParams(-1,-1));
  View.OnLongClickListener settings=v->{configure();return true;};image.setOnLongClickListener(settings);alternate.setOnLongClickListener(settings);nightCover.setOnLongClickListener(settings);
  nightCover.setOnClickListener(v->{wakeBriefly();});status.setOnClickListener(v->configure());
  importPrivateConfig();loadCache();readPreview(getIntent());FrameSsh.start(this);
 }
 public void onResume(){super.onResume();active=true;resumedForEvidence=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(!ScreenPowerController.secure(this))getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);getWindow().getDecorView().setSystemUiVisibility(5894);updateNight();if(wakeUntil>SystemClock.elapsedRealtime())main.postDelayed(endWake,wakeUntil-SystemClock.elapsedRealtime());main.removeCallbacks(cycle);if(!night)main.post(cycle);main.post(nightTick);main.post(refresh);main.post(checkUpdate);FrameEvidence.report(this,night);}
 public void onPause(){active=false;resumedForEvidence=false;main.removeCallbacks(cycle);main.removeCallbacks(refresh);main.removeCallbacks(nightTick);main.removeCallbacks(checkUpdate);main.removeCallbacks(endWake);hideDate();finishTransition();super.onPause();}
 public void onDestroy(){generation++;decoder.shutdownNow();main.removeCallbacksAndMessages(null);hideDate();finishTransition();image.setImageDrawable(null);alternate.setImageDrawable(null);if(shown!=null){shown.recycle();shown=null;}super.onDestroy();}
 protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readPreview(intent);updateNight();if(intent.hasExtra("previewIndex"))showNext();}
 public boolean onKeyUp(int key, android.view.KeyEvent event){
  if(key==android.view.KeyEvent.KEYCODE_MENU){configure();return true;}
  if(night&&key==android.view.KeyEvent.KEYCODE_DPAD_CENTER){wakeBriefly();return true;}
  return super.onKeyUp(key,event);
 }
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private void readPreview(Intent intent){
  if(!BuildConfig.DEBUG||intent==null)return;
  String mode=intent.getStringExtra("presentationPreview");
  if("day".equals(mode)||"night".equals(mode)){preview="night".equals(mode);previewUntil=SystemClock.elapsedRealtime()+90000;main.removeCallbacks(clearPreview);main.postDelayed(clearPreview,90000);}
  else if("clear".equals(mode)){preview=null;previewUntil=0;main.removeCallbacks(clearPreview);}
  if(intent.hasExtra("previewIndex")&&!photos.isEmpty()){index=Math.max(0,Math.min(photos.size()-1,intent.getIntExtra("previewIndex",0)));}
 }
 private void wakeBriefly(){wakeUntil=SystemClock.elapsedRealtime()+30000;main.removeCallbacks(endWake);main.postDelayed(endWake,30000);updateNight();}
 private void updateNight(){
  long now=SystemClock.elapsedRealtime();if(preview!=null&&now>=previewUntil)preview=null;
  boolean next=preview!=null?preview:PresentationPolicy.isNight(System.currentTimeMillis(),getPreferences(0).getBoolean("nightEnabled",true),getPreferences(0).getInt("nightStart",22),getPreferences(0).getInt("nightEnd",8),getPreferences(0).getString("nightTimezone","America/New_York"));
  if(now<wakeUntil||ScreenPowerController.temporaryDay(this))next=false;
  boolean changed=next!=night;if(changed){night=next;generation++;hideDate();finishTransition();}
  WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.screenBrightness=night?0.01f:WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;getWindow().setAttributes(attrs);
  nightCover.setVisibility(night?View.VISIBLE:View.GONE);image.setVisibility(night?View.INVISIBLE:View.VISIBLE);alternate.setVisibility(night?View.INVISIBLE:View.VISIBLE);
  status.setVisibility(night||!photos.isEmpty()?View.GONE:View.VISIBLE);
  if(changed){main.removeCallbacks(cycle);if(active&&!night)main.post(cycle);}
  if(active)ScreenPowerController.reconcile(this,night);
 }
 private void hideDate(){main.removeCallbacks(fadeDate);dateLabel.animate().cancel();dateLabel.setAlpha(0f);dateLabel.setText("");}
 private void finishTransition(){
  image.animate().cancel();alternate.animate().cancel();image.setAlpha(shown==null?0f:1f);alternate.setAlpha(0f);alternate.setImageDrawable(null);
  if(retiring!=null){retiring.recycle();retiring=null;}
 }
 private void present(Bitmap bitmap,String month){
  android.content.SharedPreferences power=ScreenPowerController.state(this);power.edit().putLong("presentationCount",power.getLong("presentationCount",0)+1).apply();
  finishTransition();hideDate();
  ImageView previous=image;image=alternate;alternate=previous;
  retiring=shown;shown=bitmap;image.setImageBitmap(bitmap);image.setAlpha(retiring==null?1f:0f);
  image.animate().alpha(1f).setDuration(600).start();
  if(retiring!=null){alternate.animate().alpha(0f).setDuration(600).withEndAction(()->{alternate.setImageDrawable(null);if(retiring!=null){retiring.recycle();retiring=null;}}).start();}
  if(!month.isEmpty()){dateLabel.setText(month);dateLabel.setAlpha(0.9f);main.postDelayed(fadeDate,4000);}
 }
 private void importPrivateConfig(){
  File config=new File(getFilesDir(),"frame-config.json");
  if(!config.isFile())return;
  try {JSONObject values=new JSONObject(new String(read(config),"UTF-8"));
   android.content.SharedPreferences.Editor edit=getPreferences(0).edit().putString("server",values.getString("serverUrl")).putString("token",values.getString("token"));
   // Optional USB config fields only: absent values preserve existing installed preferences.
   if(values.has("nightStart")){int hour=values.getInt("nightStart");if(hour<0||hour>23)throw new IOException("Invalid night start");edit.putInt("nightStart",hour);}
   if(values.has("nightEnd")){int hour=values.getInt("nightEnd");if(hour<0||hour>23)throw new IOException("Invalid night end");edit.putInt("nightEnd",hour);}
   if(values.has("nightTimezone")){String zone=values.getString("nightTimezone");if(!PresentationPolicy.validTimezone(zone))throw new IOException("Invalid night timezone");edit.putString("nightTimezone",zone);}
   if(edit.commit())config.delete();
  }catch(Exception ignored){}
 }
 private String server(){return getPreferences(0).getString("server",BuildConfig.SERVER_URL);}
 private String token(){return getPreferences(0).getString("token",BuildConfig.TOKEN);}
 private void configure(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,12);
  CheckBox direct=new CheckBox(this);direct.setText("Fetch album directly on frame");direct.setChecked("direct".equals(getSharedPreferences("directConfig",0).getString("photoMode","host")));direct.setEnabled(!getSharedPreferences("directConfig",0).getString("albumUrl","").isEmpty());box.addView(direct);
  CheckBox panelOff=new CheckBox(this);panelOff.setText("Turn the display off at night");panelOff.setChecked(ScreenPowerController.enabled(this));box.addView(panelOff);
  String sleepRequirement=ScreenPowerController.secure(this)?"Display sleep is blocked by a secure screen lock. Remove it on the frame before enabling unattended display sleep.":!ScreenPowerController.state(this).getBoolean("wakeVerified",false)?"Display sleep is waiting for a successful wake-alarm check. Until then, night mode keeps a black screen.":!ScreenPowerController.granted(this)?"Display sleep needs screen-lock permission below.":"Display sleep is ready. The morning wake alarm is scheduled before the screen turns off.";
  TextView sleepStatus=new TextView(this);sleepStatus.setText(sleepRequirement);box.addView(sleepStatus);
  if(!ScreenPowerController.granted(this)){
   TextView explanation=new TextView(this);explanation.setText("Allow screen locking to turn the display off at night. Until allowed, night mode keeps a black screen. Morning wake is scheduled before display sleep.");box.addView(explanation);
   Button allow=new Button(this);allow.setText("Allow display sleep");allow.setOnClickListener(v->{Intent request=new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,new ComponentName(this,FrameAdminReceiver.class)).putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Turn this frame's display off at night; wake is scheduled automatically for morning.");try{startActivity(request);}catch(ActivityNotFoundException e){Toast.makeText(this,"Screen-lock permission setup is unavailable",Toast.LENGTH_LONG).show();}});box.addView(allow);
  }
  EditText url=new EditText(this);url.setSingleLine();url.setHint("Photo server URL");url.setText(server());box.addView(url);
  EditText secret=new EditText(this);secret.setSingleLine();secret.setHint("Access token");secret.setInputType(129);secret.setText(token());box.addView(secret);
  CheckBox nightEnabled=new CheckBox(this);nightEnabled.setText("Night mode · "+getPreferences(0).getString("nightTimezone","America/New_York"));nightEnabled.setChecked(getPreferences(0).getBoolean("nightEnabled",true));box.addView(nightEnabled);
  TextView startCaption=new TextView(this);startCaption.setText("Dim at");box.addView(startCaption);Spinner startHour=hours(getPreferences(0).getInt("nightStart",22));box.addView(startHour);
  TextView endCaption=new TextView(this);endCaption.setText("Resume at");box.addView(endCaption);Spinner endHour=hours(getPreferences(0).getInt("nightEnd",8));box.addView(endHour);
  ScrollView scroll=new ScrollView(this);scroll.addView(box);
  new AlertDialog.Builder(this).setTitle("Photo frame settings").setView(scroll).setPositiveButton("Save",(d,w)->{ScreenPowerController.state(this).edit().putBoolean("nightOffEnabled",panelOff.isChecked()).apply();getSharedPreferences("directConfig",0).edit().putString("photoMode",direct.isChecked()?"direct":"host").apply();getPreferences(0).edit().putString("server",url.getText().toString().trim()).putString("token",secret.getText().toString().trim()).putBoolean("nightEnabled",nightEnabled.isChecked()).putInt("nightStart",startHour.getSelectedItemPosition()).putInt("nightEnd",endHour.getSelectedItemPosition()).apply();updateNight();sync();}).setNegativeButton("Cancel",null).show();
 }
 private Spinner hours(int selected){
  List<String> labels=new ArrayList<>();for(int hour=0;hour<24;hour++)labels.add((hour%12==0?12:hour%12)+(hour<12?" AM":" PM"));
  Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,Math.min(23,selected)));return spinner;
 }
 private byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file)){return readBytes(in,4*1024*1024);}}
 private byte[] readBytes(InputStream in,int limit)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(b,0,n);}return out.toByteArray();}
 File activeCacheDirectory(){return directory;}
 private File playbackDirectory(){
  File direct=new File(getFilesDir(),"direct-photos");
  return "direct".equals(getSharedPreferences("directConfig",0).getString("photoMode","host"))&&new File(direct,"manifest.json").isFile()?direct:new File(getFilesDir(),"photos");
 }
 private void loadCache(){
  try{applyManifest(new JSONObject(new String(read(new File(directory,"manifest.json")),"UTF-8")));}
  catch(Exception e){
   if("direct-photos".equals(directory.getName())){directory=new File(getFilesDir(),"photos");try{applyManifest(new JSONObject(new String(read(new File(directory,"manifest.json")),"UTF-8")));return;}catch(Exception ignored){}}
   status.setText("Photo library not connected");
  }
 }
 private void applyManifest(JSONObject manifest)throws Exception{
  JSONArray list=manifest.getJSONArray("photos");List<File> next=new ArrayList<>();Map<String,String> nextMonths=new HashMap<>();
  for(int n=0;n<list.length();n++){String sha=list.getJSONObject(n).getString("sha256");if(!sha.matches("[a-f0-9]{64}"))throw new IOException("Invalid hash");File f=new File(directory,sha+".jpg");if(f.isFile()){next.add(f);nextMonths.put(f.getName(),PresentationPolicy.monthLabel(list.getJSONObject(n).optString("capturedMonth", "")));}}
  if(next.isEmpty())throw new IOException("No cached photos");photos.clear();photos.addAll(next);photoMonths.clear();photoMonths.putAll(nextMonths);interval=Math.max(5,Math.min(3600,manifest.optInt("intervalSeconds",15)));index=0;status.setVisibility(View.GONE);
 }
 private HttpURLConnection connect(URL url, URL base, String access)throws Exception{
  if(!url.getProtocol().equals(base.getProtocol())||!url.getHost().equals(base.getHost())||url.getPort()!=base.getPort())throw new IOException("Photo URL must use same server");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestProperty("Authorization","Bearer "+access);if(c.getResponseCode()!=200){c.disconnect();throw new IOException("Server request failed");}return c;
 }
 private void scheduledSync(){
  if(syncing||syncLock.get())return;
  String revision=getSharedPreferences("directConfig",0).getString("configRevision","");boolean changed=!revision.equals(observedRevision);observedRevision=revision;
  File selected=playbackDirectory();if(!selected.equals(directory)){directory=selected;loadCache();}
  if("direct".equals(getSharedPreferences("directConfig",0).getString("photoMode","host"))){
   android.content.SharedPreferences state=getSharedPreferences("directSync",0);long now=System.currentTimeMillis();
   if(!revision.equals(state.getString("attemptRevision",""))||DirectSchedule.due(now,revision.equals(state.getString("successRevision",""))?state.getLong("success",0):0,state.getLong("attempt",0),state.getInt("failures",0),getSharedPreferences("directConfig",0).getInt("syncHour",9),getSharedPreferences("directConfig",0).getInt("syncMinute",0),getSharedPreferences("directConfig",0).getString("syncTimezone","America/New_York")))sync();
  }else if(changed||System.currentTimeMillis()-getSharedPreferences("directSync",0).getLong("hostAttempt",0)>=24*60*60*1000L)sync();
 }
 private void syncDirect(){
  if(photos.isEmpty())status.setText("Loading photo library");
  final android.content.SharedPreferences config=getSharedPreferences("directConfig",0),state=getSharedPreferences("directSync",0);
  final File directDirectory=new File(getFilesDir(),"direct-photos");directDirectory.mkdirs();
  final String album=config.getString("albumUrl",""),revision=config.getString("configRevision","");final int seconds=config.getInt("directInterval",15);
  state.edit().putLong("attempt",System.currentTimeMillis()).putString("attemptRevision",revision).putString("state","running").putString("failureCode","").putInt("downloadedCount",0).commit();
  if(active)FrameEvidence.report(this,night);
  worker.execute(()->{try{
   if(BuildConfig.DEBUG&&state.getInt("imageChecksVersion",0)!=BuildConfig.VERSION_CODE){
    state.edit().putBoolean("imageChecksPassed",false).commit();NativeImageChecks.run(getCacheDir());state.edit().putBoolean("imageChecksPassed",true).putInt("imageChecksVersion",BuildConfig.VERSION_CODE).commit();
   }
   JSONObject manifest=DirectAlbumSync.run(directDirectory,album,seconds,()->revision.equals(config.getString("configRevision",""))&&"direct".equals(config.getString("photoMode","host")),count->state.edit().putInt("downloadedCount",count).apply());
   int dated=0;JSONArray directPhotos=manifest.getJSONArray("photos");for(int i=0;i<directPhotos.length();i++)if(!directPhotos.getJSONObject(i).optString("capturedMonth","").isEmpty())dated++;
   state.edit().putInt("datedCount",dated).putLong("success",System.currentTimeMillis()).putString("successRevision",revision).putString("state","success").putString("failureCode","").putInt("failures",0).putInt("photoCount",manifest.getJSONArray("photos").length()).commit();
   main.post(()->{if(!isDestroyed()&&!isFinishing())try{directory=playbackDirectory();loadCache();showNext();}catch(Exception ignored){}});
  }catch(Exception e){state.edit().putString("state","failed").putString("failureCode",e instanceof DirectAlbumSync.Failure?((DirectAlbumSync.Failure)e).code:"sync").putInt("failures",Math.min(16,state.getInt("failures",0)+1)).commit();}
  finally{syncLock.set(false);main.post(()->{syncing=false;if(!isDestroyed()&&!isFinishing()){if(photos.isEmpty())status.setText("Waiting for your photo library");if(active)FrameEvidence.report(this,night);}});}});
 }
 private void sync(){
  if(syncing||!syncLock.compareAndSet(false,true))return;syncing=true;
  if("direct".equals(getSharedPreferences("directConfig",0).getString("photoMode","host"))){syncDirect();return;}
  directory=new File(getFilesDir(),"photos");directory.mkdirs();loadCache();
  getSharedPreferences("directSync",0).edit().putLong("hostAttempt",System.currentTimeMillis()).apply();if(photos.isEmpty())status.setText("Loading photo library");final String endpoint=server(), access=token(),hostRevision=getSharedPreferences("directConfig",0).getString("configRevision","");
  worker.execute(()->{try{
   URL base=new URL(endpoint.endsWith("/")?endpoint:endpoint+"/");HttpURLConnection c=connect(new URL(base,"manifest.json"),base,access);byte[] bytes;
   try(InputStream in=c.getInputStream()){bytes=readBytes(in,1024*1024);}finally{c.disconnect();}
   JSONObject manifest=new JSONObject(new String(bytes,"UTF-8"));JSONArray list=manifest.getJSONArray("photos");if(list.length()==0||list.length()>5000)throw new IOException("Invalid photo count");
   for(int n=0;n<list.length();n++){
    JSONObject photo=list.getJSONObject(n);String sha=photo.getString("sha256");if(!sha.matches("[a-f0-9]{64}"))throw new IOException("Invalid hash");File target=new File(directory,sha+".jpg");
    if(!target.isFile()||!sha.equals(CacheStore.digest(target))){
    HttpURLConnection download=connect(new URL(base,photo.getString("url")),base,access);
    try(InputStream in=download.getInputStream()){CacheStore.verifiedCopy(in,target,sha);}finally{download.disconnect();}
    }
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.getPath(),bounds);if(bounds.outWidth<=0)throw new IOException("Invalid photo");
   }
   if(!hostRevision.equals(getSharedPreferences("directConfig",0).getString("configRevision",""))||!"host".equals(getSharedPreferences("directConfig",0).getString("photoMode","host")))throw new IOException("Configuration changed");
   CacheStore.publish(new File(directory,"manifest.json"),bytes);
   // Delete only after the complete replacement manifest was published.
   Set<String> keep=new HashSet<>();for(int n=0;n<list.length();n++)keep.add(list.getJSONObject(n).getString("sha256")+".jpg");File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".jpg")&&!keep.contains(f.getName()))f.delete();
   main.post(()->{if(!isFinishing()&&!isDestroyed())try{applyManifest(manifest);showNext();if(active)FrameEvidence.report(this,night);}catch(Exception ignored){}});
  }catch(Exception e){main.post(()->{if(photos.isEmpty())status.setText("Waiting for your photo library");getSharedPreferences("directSync",0).edit().putLong("hostAttempt",System.currentTimeMillis()-24*60*60*1000L+5*60*1000L).apply();});}finally{syncLock.set(false);main.post(()->syncing=false);}});
 }
 private void showNext(){
  if(!active||night||photos.isEmpty()||decoding)return;File file=photos.get(index++%photos.size());String month=photoMonths.get(file.getName());final int requestGeneration=generation;decoding=true;
  int width=Math.max(1,getResources().getDisplayMetrics().widthPixels),height=Math.max(1,getResources().getDisplayMetrics().heightPixels);
  decoder.execute(()->{
   Bitmap next=null;
   try{
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opts);
    opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>width*2||opts.outHeight/opts.inSampleSize>height*2)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;
    next=BitmapFactory.decodeFile(file.getPath(),opts);
   }catch(OutOfMemoryError ignored){}
   final Bitmap result=next;
   main.post(()->{decoding=false;if(result==null)return;if(isDestroyed()||isFinishing()||!active||night||requestGeneration!=generation){result.recycle();return;}present(result,month==null?"":month);});
  });
 }
}
