#!/usr/bin/env python3
"""Build without embedding any private runtime configuration in the APK."""
import argparse, os, subprocess
from env_config import load_env, resolve_path, ROOT
p=argparse.ArgumentParser()
p.add_argument('--version-code',type=int,default=9)
p.add_argument('--version-name',default='1.3.3')
p.add_argument('--checks',action='store_true',help='Also run unit tests and lint')
a=p.parse_args()
if a.version_code<1: p.error('version-code must be positive')
env={**os.environ,**load_env()}
for key, default in [('JAVA_HOME','android/.tools/jdk-17.0.20.1+1/Contents/Home'),('ANDROID_SDK_ROOT','android/.tools/sdk')]:
    env[key]=str(resolve_path(env.get(key, default)))
    if not resolve_path(env[key]).is_dir(): p.error('Missing pinned tools; run scripts/bootstrap-tools.py')
if env.get('FRAME_KEYSTORE_PATH'):
    env['FRAME_KEYSTORE_PATH']=str(resolve_path(env['FRAME_KEYSTORE_PATH']))
    if not resolve_path(env['FRAME_KEYSTORE_PATH']).is_file(): p.error('Signing keystore missing; do not regenerate an installed identity')
env['GRADLE_USER_HOME']=str(ROOT/'android/.tools/gradle-home')
# Runtime values are neither build inputs nor inherited by Gradle.
for key in ['FRAME_TOKEN','ALBUM_URL','FRAME_SERVER_URL']: env.pop(key,None)
command=[str(ROOT/'android/gradlew'),'--no-daemon','--console=plain','assembleDebug',f'-PframeVersionCode={a.version_code}',f'-PframeVersionName={a.version_name}']
if a.checks: command+=['testDebugUnitTest','lintDebug']
subprocess.run(command,cwd=ROOT/'android',env=env,check=True,timeout=900)
print('APK: android/app/build/outputs/apk/debug/app-debug.apk (runtime token/server imported separately over USB)')
