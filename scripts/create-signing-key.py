#!/usr/bin/env python3
"""Create a NEW frame signing identity only; existing installations require their original key."""
import argparse, os, subprocess
from env_config import load_env, resolve_path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--new-installation',action='store_true',required=True,help='Explicitly confirm this is a new installation')
a=p.parse_args();env=load_env()
for name in ['FRAME_KEYSTORE_PATH','FRAME_KEYSTORE_PASSWORD','FRAME_KEY_ALIAS','FRAME_KEY_PASSWORD']:
 if not env.get(name):p.error('Set private signing path/passwords/alias in .env')
key=resolve_path(env['FRAME_KEYSTORE_PATH']);baseline=resolve_path(env.get('FRAME_BASELINE_APK','runtime/app-installed.apk'))
if key.exists() or baseline.exists():p.error('Existing key/baseline preserved; never regenerate an installed identity')
if len(env['FRAME_KEYSTORE_PASSWORD'])<6 or len(env['FRAME_KEY_PASSWORD'])<6:p.error('Signing passwords must be at least 6 characters')
key.parent.mkdir(parents=True,exist_ok=True);os.chmod(key.parent,0o700)
oldmask=os.umask(0o077)
try:
 subprocess.run([str(resolve_path(env['JAVA_HOME'])/'bin/keytool'),'-genkeypair','-keystore',str(key),'-storetype','JKS','-storepass:env','FRAME_KEYSTORE_PASSWORD','-keypass:env','FRAME_KEY_PASSWORD','-alias',env['FRAME_KEY_ALIAS'],'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Nix Free Frame','-noprompt'],env={**os.environ,**env},capture_output=True,check=True,timeout=60)
 os.chmod(key,0o600)
finally:os.umask(oldmask)
print('New private signing identity created. Preserve and back it up; all future updates must use this exact key.')
