#!/usr/bin/env python3
"""Import config via adb stdin; do not install, set HOME, or change Nixplay."""
import argparse, hashlib, json, shlex, subprocess, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from env_config import load_env, resolve_path
env=load_env() if '--help' not in sys.argv and '-h' not in sys.argv else {}
p=argparse.ArgumentParser()
p.add_argument('--adb',default=env.get('ADB_PATH', str(resolve_path('tools/platform-tools/adb'))))
p.add_argument('--serial',default=env.get('FRAME_SERIAL'),help='Explicit authorized physical USB device serial')
p.add_argument('--config',help='Legacy private JSON; omitted reads .env')
a=p.parse_args()
if not a.serial: p.error('Set FRAME_SERIAL or --serial for an explicit USB target')
data=json.loads(Path(a.config).read_text()) if a.config else {'serverUrl':env.get('FRAME_SERVER_URL'), 'token':env.get('FRAME_TOKEN')}
if not data.get('serverUrl'): p.error('Set FRAME_SERVER_URL in .env')
a.adb=str(resolve_path(a.adb))
values={'serverUrl':data['serverUrl'],'token':data.get('frameToken',data.get('token'))}
if not a.config:
 from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
 for name, envname in [('nightStart','NIGHT_START_HOUR'),('nightEnd','NIGHT_END_HOUR')]:
  if env.get(envname):
   try: hour=int(env[envname])
   except ValueError: p.error('Night hours must be integers')
   if not 0<=hour<=23: p.error('Night hours must be 0 through 23')
   values[name]=hour
 if env.get('NIGHT_TIMEZONE'):
  try: ZoneInfo(env['NIGHT_TIMEZONE'])
  except (ZoneInfoNotFoundError,ValueError): p.error('Invalid NIGHT_TIMEZONE')
  values['nightTimezone']=env['NIGHT_TIMEZONE']
if not values['token']: p.error('Private config needs frameToken or token')
if ':' in a.serial or a.serial.startswith('emulator-'): p.error('This helper requires a physical USB serial, not network adb')
state=subprocess.run([a.adb,'-s',a.serial,'get-state'],capture_output=True,text=True,check=True,timeout=45)
if state.stdout.strip()!='device': p.error('Device is not authorized')
transport=subprocess.run([a.adb,'-s',a.serial,'get-devpath'],capture_output=True,text=True,check=True,timeout=45)
if not transport.stdout.strip().startswith('usb:'): p.error('Selected device is not a detected USB transport')
def property(name):
 result=subprocess.run([a.adb,'-s',a.serial,'shell','getprop',name],capture_output=True,text=True,check=True,timeout=45)
 return result.stdout.strip()
if property('ro.product.model').lower().replace('-','')!='w10f09' or property('ro.build.version.sdk')!='25' or property('ro.product.cpu.abi')!='armeabi-v7a': p.error('Only verified w10f09/API25/ARM32 hardware is supported')
if property('sys.boot_completed')!='1': p.error('Normal boot must complete before configuration')
payload=json.dumps(values).encode()
# A single quoted remote command keeps redirection inside the app UID.
# Noclobber refuses to replace an existing config file.
remote="run-as works.tycho.frame sh -c " + shlex.quote('mkdir -p files && set -C && cat > files/frame-config.json')
subprocess.run([a.adb,'-s',a.serial,'shell','-T',remote],input=payload,capture_output=True,check=True,timeout=45)
expected=hashlib.sha256(payload).hexdigest()
checksum=subprocess.run([a.adb,'-s',a.serial,'shell','run-as','works.tycho.frame','sha256sum','files/frame-config.json'],capture_output=True,text=True,timeout=45)
actual=checksum.stdout.split()[0] if checksum.returncode==0 and checksum.stdout.split() else ''
if actual != expected:
 # Some legacy firmware lacks sha256sum. Read privately into memory only.
 readback=subprocess.run([a.adb,'-s',a.serial,'exec-out','run-as','works.tycho.frame','cat','files/frame-config.json'],capture_output=True,timeout=45)
 if readback.returncode!=0 or len(readback.stdout)!=len(payload) or hashlib.sha256(readback.stdout).hexdigest()!=expected:
  p.error('Configuration import could not be verified; existing file was not overwritten')
print('Private configuration imported. Launch My Photo Frame to apply it.')
