package works.tycho.frame;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;

/** Private configuration arrives only over the existing authenticated maintenance origin. */
final class DirectConfig {
 static synchronized java.security.KeyPair keyPair(SharedPreferences secrets)throws Exception{
  String encoded=secrets.getString("privateKey","");java.security.KeyFactory factory=java.security.KeyFactory.getInstance("RSA");
  if(!encoded.isEmpty())return new java.security.KeyPair(factory.generatePublic(new java.security.spec.X509EncodedKeySpec(android.util.Base64.decode(secrets.getString("publicKey",""),android.util.Base64.NO_WRAP))),factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP))));
  java.security.KeyPairGenerator generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);java.security.KeyPair pair=generator.generateKeyPair();
  if(!secrets.edit().putString("privateKey",android.util.Base64.encodeToString(pair.getPrivate().getEncoded(),android.util.Base64.NO_WRAP)).putString("publicKey",android.util.Base64.encodeToString(pair.getPublic().getEncoded(),android.util.Base64.NO_WRAP)).commit())throw new IOException("Cannot save provisioning key");return pair;
 }
 static JSONObject decrypt(byte[] wire,java.security.PrivateKey privateKey)throws Exception{
  JSONObject envelope=new JSONObject(new String(wire,"UTF-8"));if(envelope.getInt("version")!=1)throw new IOException("Invalid envelope");
  byte[] nonce=android.util.Base64.decode(envelope.getString("nonce"),android.util.Base64.NO_WRAP),ciphertext=android.util.Base64.decode(envelope.getString("ciphertext"),android.util.Base64.NO_WRAP);
  if(nonce.length!=12||ciphertext.length>20000||ciphertext.length<16)throw new IOException("Invalid envelope");
  javax.crypto.Cipher unwrap=javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");unwrap.init(javax.crypto.Cipher.DECRYPT_MODE,privateKey);
  byte[] key=unwrap.doFinal(android.util.Base64.decode(envelope.getString("wrappedKey"),android.util.Base64.NO_WRAP));if(key.length!=32)throw new IOException("Invalid envelope");
  javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nonce));
  cipher.updateAAD("nix-free-frame-direct-config-v1".getBytes("UTF-8"));return new JSONObject(new String(cipher.doFinal(ciphertext),"UTF-8"));
 }
 static boolean apply(SharedPreferences prefs,JSONObject value)throws Exception{
  if(value.getInt("version")!=1)throw new IOException("Invalid configuration");
  String revision=value.getString("revision"),mode=value.getString("mode");
  if(!revision.matches("[a-zA-Z0-9_-]{1,64}")||!(mode.equals("host")||mode.equals("direct")))throw new IOException("Invalid configuration");
  String album=mode.equals("direct")?value.getString("albumUrl"):"";
  if(mode.equals("direct"))AlbumHttps.validateAlbumUrl(album);
  int hour=value.optInt("syncHour",9),minute=value.optInt("syncMinute",0),interval=value.optInt("intervalSeconds",15);
  String zone=value.optString("syncTimezone","America/New_York");
  if(hour<0||hour>23||minute<0||minute>59||interval<5||interval>3600||!PresentationPolicy.validTimezone(zone))throw new IOException("Invalid configuration");
  if(revision.equals(prefs.getString("configRevision","")))return false;
  if(!prefs.edit().putString("photoMode",mode).putString("albumUrl",album).putString("configRevision",revision).putInt("syncHour",hour).putInt("syncMinute",minute).putString("syncTimezone",zone).putInt("directInterval",interval).commit())throw new IOException("Cannot save configuration");
  return true;
 }
}
