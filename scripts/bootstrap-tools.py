#!/usr/bin/env python3
"""Pinned official downloads, verified before extraction; macOS Apple Silicon host."""
import argparse, hashlib, json, os, platform, subprocess, tempfile, urllib.request, zipfile, time
from pathlib import Path
from env_config import ROOT, load_env, resolve_path
p=argparse.ArgumentParser()
p.add_argument('--tool-dir',help='Ignored archive/tool root (default android/.tools)')
p.add_argument('--download',action='store_true',help='Download and verify missing archives')
p.add_argument('--install',action='store_true',help='Extract verified tools (implies download)')
a=p.parse_args()
if platform.system()!='Darwin' or platform.machine()!='arm64': p.error('Supported build host: Apple Silicon macOS')
env=load_env()
base=resolve_path(a.tool_dir or env.get('FRAME_TOOL_DIR','android/.tools')); base.mkdir(parents=True,exist_ok=True)
# Isolated --tool-dir is also used for clean-bootstrap verification.
sdk=base/'sdk' if a.tool_dir else resolve_path(env.get('ANDROID_SDK_ROOT','android/.tools/sdk'))
jdk_home=base/'jdk-17.0.20.1+1/Contents/Home' if a.tool_dir else resolve_path(env.get('JAVA_HOME','android/.tools/jdk-17.0.20.1+1/Contents/Home'))
adb_dir=base/'platform-tools' if a.tool_dir else resolve_path(env.get('ADB_PATH','tools/platform-tools/adb')).parent
# SHA-1 for Google packages is the checksum published in HTTPS SDK repository metadata.
artifacts=[
 ('jdk.tar.gz','https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.20.1_1.tar.gz','sha256','196d13ba5f10414bef7f6a05a9b3f00edacb18ebacef2b99485db9e2ee18f0e8','jdk'),
 ('platform-tools.zip','https://dl.google.com/android/repository/platform-tools_r37.0.1-darwin.zip','sha1','6ae73f4de6452dc57e62ec02b68eed92a4c21661','adb'),
 ('sdk.zip','https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip','sha1','37fb7dd41005b3b4ca6ea48ac27074b6fc4e3236','cli'),
 ('build-tools.zip','https://dl.google.com/android/repository/build-tools_r34-macosx.zip','sha1','00e1301387028f33753e38274401694e1a27c62f','build'),
 ('platform35.zip','https://dl.google.com/android/repository/platform-35_r02.zip','sha1','0bb560a90a7a2cbd0dd8348224d518b638fe7949','platform'),
 ('termux.apk','https://f-droid.org/repo/com.termux_1002.apk','sha256','e6265a57eb5ca363808488e3b01955958bed93bc0c8a0d281849b363b11027ec','apk'),
 ('termux-boot.apk','https://f-droid.org/repo/com.termux.boot_1000.apk','sha256','6f7cf9b94f539d3efd4af3544ff819947b49395275d8cfa7e5f80de14f3d9cf8','apk')]
for name,url,algorithm,expected,kind in artifacts:
    target=base/name
    if not target.exists():
        if not (a.download or a.install): print(name+': missing (use --download or --install)'); continue
        fd,tmp=tempfile.mkstemp(dir=base); os.close(fd)
        try:
            with urllib.request.urlopen(url,timeout=60) as response,open(tmp,'wb') as out:
                total=0; deadline=time.monotonic()+600
                while chunk:=response.read(1024*1024):
                    total+=len(chunk)
                    if total>300*1024*1024 or time.monotonic()>deadline: raise ValueError("Download exceeded size/time bound")
                    out.write(chunk)
            if hashlib.new(algorithm,Path(tmp).read_bytes()).hexdigest()!=expected: raise ValueError('Official archive checksum mismatch: '+name)
            os.replace(tmp,target)
        finally:
            if Path(tmp).exists(): Path(tmp).unlink()
    if hashlib.new(algorithm,target.read_bytes()).hexdigest()!=expected: raise ValueError('Archive checksum mismatch: '+name)
    print(name+': verified')
    if a.install and kind!='apk':
        # Extract into scratch, then move only the dedicated pinned package.
        with tempfile.TemporaryDirectory(dir=base) as scratch:
            if kind=='jdk': subprocess.run(['tar','-xzf',str(target),'-C',scratch],check=True,timeout=120)
            else:
                subprocess.run(['unzip','-q',str(target),'-d',scratch],check=True,timeout=120)
            folders={'jdk':('jdk-17.0.20.1+1',jdk_home.parents[1]),'adb':('platform-tools',adb_dir),'cli':('cmdline-tools',sdk/'cmdline-tools/12.0'),'build':('android-14',sdk/'build-tools/34.0.0'),'platform':('android-35',sdk/'platforms/android-35')}
            folder,destination=folders[kind]
            extracted=Path(scratch)/folder
            if destination.exists():
                for source in extracted.rglob('*'):
                    relative=source.relative_to(extracted);installed=destination/relative
                    if source.is_symlink():
                        if not installed.is_symlink() or os.readlink(source)!=os.readlink(installed): raise ValueError('Installed pinned tool differs: '+str(relative))
                    elif source.is_file():
                        if not installed.is_file() or hashlib.sha256(source.read_bytes()).digest()!=hashlib.sha256(installed.read_bytes()).digest(): raise ValueError('Installed pinned tool differs: '+str(relative))
                print(kind+': existing extracted tool files match verified archive')
            if not destination.exists():
                destination.parent.mkdir(parents=True,exist_ok=True)
                os.rename(Path(scratch)/folder,destination)
# Verify both APK signatures with exact pinned trust, if the build verifier is installed.
verifier=sdk/'build-tools/34.0.0/apksigner'
jdk=jdk_home
if verifier.exists() and jdk.exists():
    for name in ['termux.apk','termux-boot.apk']:
        if (base/name).exists():
            result=subprocess.run([str(verifier),'verify','--min-sdk-version','25','--print-certs',str(base/name)],env={**os.environ,'JAVA_HOME':str(jdk)},capture_output=True,text=True,check=True,timeout=60)
            signer='228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42'
            if 'Signer #1 certificate SHA-256 digest: '+signer not in result.stdout: raise ValueError('Unexpected Termux signer')
            print(name+': API25 signature and pinned F-Droid signer verified')
print('Gradle: tracked wrapper verifies 8.9 distribution SHA256 on first build. SDK licensing remains an explicit manual sdkmanager --licenses step.')
