#!/usr/bin/env python3
"""Explicit physical USB workflow; never wipe, flash, network ADB or auto-select."""
import argparse, json, os, shlex, subprocess, time
from pathlib import Path
from env_config import load_env, resolve_path, ROOT
p=argparse.ArgumentParser()
p.add_argument('action',choices=['discover','doctor','backup','install','home','owner','clear-owner','permissions','restore-home','retire-access'])
p.add_argument('--serial')
p.add_argument('--apk')
p.add_argument('--original-home',help='Explicit recorded original package/activity for restoration')
a=p.parse_args(); env=load_env(); adb=str(resolve_path(env.get('ADB_PATH','tools/platform-tools/adb')))
def run(*args):
    result=subprocess.run([adb,*args],capture_output=True,text=True,timeout=45)
    if result.returncode: raise SystemExit('ADB operation failed; private state retained. Review USB authorization.')
    return result.stdout.strip()
if a.action=='discover': print(run('devices','-l')); raise SystemExit()
serial=a.serial or env.get('FRAME_SERIAL')
if not serial or ':' in serial or serial.startswith('emulator-'): p.error('Select an explicit physical FRAME_SERIAL; network/emulated ADB refused')
def shell(*args): return run('-s',serial,'shell',*args)
if run('-s',serial,'get-state')!='device' or not run('-s',serial,'get-devpath').startswith('usb:'): p.error('Authorized physical USB transport required')
model=shell('getprop','ro.product.model'); api=shell('getprop','ro.build.version.sdk'); abi=shell('getprop','ro.product.cpu.abi')
if model.lower().replace('-','')!='w10f09' or api!='25' or abi!='armeabi-v7a': p.error('Unverified hardware/firmware; workflow supports tested W10F-09/API25/ARM32 only')
if shell('getprop','sys.boot_completed')!='1': p.error('Normal boot must complete before all targeted operations')
if a.action=='doctor':
    print(json.dumps({'model':model,'api':25,'abi':abi,'bootCompleted':shell('getprop','sys.boot_completed')=='1','home':shell('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME'),'backup':shell('bmgr','enabled'),'owner':shell('dumpsys','device_policy')})); raise SystemExit()
private=resolve_path(env.get('RUNTIME_DIR','runtime'))/'device-recovery'
state=private/'before-management.json'
complete=private/'backup-complete.json'
if a.action in ['install','home','owner','restore-home'] and not complete.is_file(): p.error('Complete original-state/APK backup required; interrupted evidence is retained privately for review')
if a.action=='backup':
    private.mkdir(parents=True,exist_ok=True); os.chmod(private,0o700)
    if state.exists(): p.error('Original recovery state exists; preserve it')
    backup=shell('bmgr','enabled')
    home=shell('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
    if backup not in ['Backup Manager currently enabled','Backup Manager currently disabled']: p.error('Unrecognized Backup Manager state; refusing guessed recovery')
    packages=['com.kitesystems.nix.frame','com.kitesystems.nix.prod','com.nixplay.webviewtest']
    package_states={name:shell('dumpsys','package',name) for name in packages}
    data={'packageStates':package_states,'backupEnabled':backup=='Backup Manager currently enabled','backupOutput':backup,'home':home,'model':model,'api':api}
    fd=os.open(state,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f: json.dump(data,f)
    for package in ['com.kitesystems.nix.frame','com.kitesystems.nix.prod','com.nixplay.webviewtest']:
        paths=shell('pm','path',package).splitlines()
        for i,line in enumerate(paths):
            if line.startswith('package:'):
                destination=private/f'{package}-{i}.apk'
                if destination.exists(): p.error('Private backup APK exists; refusing overwrite')
                run('-s',serial,'pull',line[8:],str(destination))
                os.chmod(destination,0o600)
    fd=os.open(complete,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f: json.dump({'model':model,'api':api,'complete':True},f)
    print('Original HOME and Backup Manager state recorded privately; available original APKs copied.'); raise SystemExit()
if a.action=='install':
    if not state.exists(): p.error('Run backup first; original state required')
    if not a.apk: p.error('--apk required')
    apk=resolve_path(a.apk)
    if not apk.is_file(): p.error('APK missing')
    sdk=resolve_path(env.get('ANDROID_SDK_ROOT','android/.tools/sdk'))/'build-tools/34.0.0'
    buildenv={**os.environ,'JAVA_HOME':str(resolve_path(env.get('JAVA_HOME','android/.tools/jdk-17.0.20.1+1/Contents/Home')))}
    def local(*args):
        result=subprocess.run(args,capture_output=True,text=True,env=buildenv,timeout=60)
        if result.returncode: p.error('Official APK verification failed')
        return result.stdout
    metadata=local(str(sdk/'aapt'),'dump','badging',str(apk))
    if "package: name='works.tycho.frame'" not in metadata: p.error('Only the own frame package may be installed')
    import re
    match=re.search(r"sdkVersion:'([0-9]+)'",metadata)
    if not match or int(match[1])>25: p.error('APK incompatible with API25')
    native=re.search(r'^native-code:(.*)$',metadata,re.M)
    if native and "'armeabi-v7a'" not in native[1] and "'armeabi'" not in native[1]: p.error('APK native code incompatible with ARM32')
    certificate=local(str(sdk/'apksigner'),'verify','--min-sdk-version','25','--print-certs',str(apk))
    signer=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',certificate)
    if not signer: p.error('APK signer not verified')
    baseline=resolve_path(env.get('FRAME_BASELINE_APK','runtime/app-installed.apk'))
    if baseline.exists():
        old=local(str(sdk/'apksigner'),'verify','--min-sdk-version','25','--print-certs',str(baseline))
        if signer.group(0) not in old: p.error('APK signer differs from private installed baseline')
    elif 'package:works.tycho.frame' in shell('pm','list','packages','works.tycho.frame'):
        p.error('Existing frame app requires its private APK baseline for signature comparison')
    result=run('-s',serial,'install','-r',str(apk))
    if 'Success' not in result: p.error('Installation not confirmed')
elif a.action=='home':
    if not state.exists(): p.error('Run backup first')
    shell('cmd','package','set-home-activity','works.tycho.frame/.FrameActivity')
    shell('am','start','-n','works.tycho.frame/.FrameActivity')
elif a.action=='owner':
    if not state.exists(): p.error('Run backup first')
    shell('dpm','set-device-owner','works.tycho.frame/.FrameAdminReceiver')
    import re
    policy=shell('dumpsys','device_policy')
    if not re.search(r'Device Owner:[^\n]*(?:\n[^\n]*){0,5}?admin=ComponentInfo\{works\.tycho\.frame/works\.tycho\.frame\.FrameAdminReceiver\}',policy): p.error('Exact Device Owner assignment not verified; do not retry with data deletion')
elif a.action=='clear-owner':
    if not state.exists(): p.error('Original Backup Manager state required; no assumption made')
    shell('am','broadcast','-a','works.tycho.frame.CLEAR_OWNER','-n','works.tycho.frame/.OwnerRecoveryReceiver')
    policy=shell('dumpsys','device_policy')
    if 'Device Owner' in policy and 'works.tycho.frame' in policy: p.error('Owner absence unverified; backup not changed')
    original=json.loads(state.read_text())['backupEnabled']
    shell('bmgr','enable','true' if original else 'false')
    actual=shell('bmgr','enabled')
    if actual not in ['Backup Manager currently enabled','Backup Manager currently disabled'] or (actual=='Backup Manager currently enabled')!=original: p.error('Backup Manager restoration unverified')
elif a.action=='permissions':
    shell('pm','grant','works.tycho.frame','com.termux.permission.RUN_COMMAND')
    shell('dumpsys','deviceidle','whitelist','+com.termux')
    print('Waiting 45 seconds for permission/exemption persistence.'); time.sleep(45)
    if 'com.termux' not in shell('dumpsys','deviceidle','whitelist'): p.error('Termux exemption unverified')
    permissions=shell('dumpsys','package','works.tycho.frame')
    if 'com.termux.permission.RUN_COMMAND: granted=true' not in permissions: p.error('Scoped frame RUN_COMMAND grant unverified')
elif a.action=='restore-home':
    if not a.original_home or not state.exists(): p.error('Recorded recovery and explicit --original-home required')
    if a.original_home not in json.loads(state.read_text())['home']: p.error('HOME does not match recorded original')
    shell('cmd','package','set-home-activity',a.original_home)
    shell('am','start','-n',a.original_home)
elif a.action=='retire-access':
    shell('pm','revoke','works.tycho.frame','com.termux.permission.RUN_COMMAND')
    print('Bridge grant revoked. Stop dedicated Termux SSH task and restore private Termux properties manually; unrelated keys/settings retained.')
if a.action in ['home','owner','clear-owner','restore-home']:
    print('Waiting 45 seconds for persistent state before any reboot.'); time.sleep(45)
print('Completed targeted operation. Run doctor to read back state. No reboot, data deletion or firmware changes performed.')
