#!/usr/bin/env python3
"""Install/remove one managed key-only SSH alias, preserving unrelated configuration."""
import argparse, ipaddress, os, re, shutil, subprocess, tempfile
from pathlib import Path
from env_config import load_env, resolve_path
p=argparse.ArgumentParser()
p.add_argument('action',choices=['install','remove','verify'])
p.add_argument('--config',type=Path,default=Path.home()/'.ssh/config')
p.add_argument('--jump',help='Existing trusted SSH alias, optionally backed by Tailscale')
a=p.parse_args();env=load_env()
alias=env.get('FRAME_SSH_ALIAS','nix-frame');host=env.get('FRAME_SSH_ADDRESS','');user=env.get('FRAME_SSH_USER','');port=env.get('FRAME_SSH_PORT','8022')
for value in [alias,user]+([a.jump] if a.jump else []):
    if not value or not re.fullmatch(r'[A-Za-z0-9_.-]+',value):p.error('A specific SSH alias and Termux username are required')
try:
    ip=ipaddress.ip_address(host)
    if ip.version!=4 or not any(ip in ipaddress.ip_network(net) for net in ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']):raise ValueError()
except ValueError:p.error('FRAME_SSH_ADDRESS must be the verified private LAN IPv4 address')
if not port.isdigit() or not 1024<=int(port)<=65535:p.error('Invalid frame SSH port')
key=resolve_path(env.get('FRAME_SSH_PRIVATE_KEY_PATH','runtime/ssh/id_ed25519'));pins=resolve_path(env.get('FRAME_SSH_HOST_KEYS_FILE','runtime/ssh/known_hosts'))
start=f'# BEGIN nix-free-frame {alias}\n';end=f'# END nix-free-frame {alias}\n'
path=a.config.expanduser();original=path.read_text() if path.exists() else ''
if original.count(start)!=original.count(end) or original.count(start)>1:p.error('Ambiguous managed SSH block; original untouched')
if a.action=='verify':
    if start not in original:p.error('Managed alias absent; unrelated aliases are not verification evidence')
    result=subprocess.run(['ssh','-G','-F',str(path),alias],capture_output=True,text=True,check=True,timeout=10)
    values={}
    for line in result.stdout.splitlines():
        name,_,value=line.partition(' ');values.setdefault(name,[]).append(value)
    expected={'hostname':host,'port':port,'user':user,'userknownhostsfile':str(pins),'stricthostkeychecking':'true','passwordauthentication':'no','kbdinteractiveauthentication':'no','identitiesonly':'yes'}
    if any(values.get(name)!=[value] for name,value in expected.items()) or not values.get('identityfile') or values['identityfile'][0]!=str(key):p.error('Managed SSH resolution differs from configured pinned identity')
    subprocess.run(['ssh','-F',str(path),'-o','BatchMode=yes','-o','ConnectTimeout=10','-o','StrictHostKeyChecking=yes',alias,'id','-u'],check=True,timeout=20)
    raise SystemExit()
if a.action=='remove':
    if start not in original:p.error('Managed alias absent; nothing changed')
    begin=original.index(start);finish=original.index(end,begin)+len(end)
    updated=original[:begin]+original[finish:]
else:
    if start in original:p.error('Managed alias already exists; review before replacing')
    # Refuse an exact existing alias; the new explicit block precedes any inherited wildcard defaults.
    for line in original.splitlines():
        fields=line.strip().split()
        if fields and fields[0].lower()=='host':
            for pattern in fields[1:]:
                if pattern==alias:p.error('An existing unmanaged alias uses this name')
    if not key.is_file() or key.stat().st_mode & 0o777!=0o600:p.error('Dedicated private key missing or not mode600')
    if not pins.is_file():p.error('USB-verified known-hosts file required; no network scanning or trust-on-first-use')
    checked=subprocess.run(['ssh-keygen','-F',f'[{host}]:{port}','-f',str(pins)],capture_output=True,timeout=10)
    if checked.returncode!=0:p.error('USB-verified host pin does not match selected address/port')
    def quoted(value):return '"'+str(value).replace('\\','\\\\').replace('"','\\"')+'"'
    block=start+f'Host {alias}\n  HostName {host}\n  Port {port}\n  User {user}\n  IdentityFile {quoted(key)}\n  UserKnownHostsFile {quoted(pins)}\n  IdentitiesOnly yes\n  StrictHostKeyChecking yes\n  PasswordAuthentication no\n  KbdInteractiveAuthentication no\n  BatchMode yes\n'
    if a.jump:block+=f'  ProxyJump {a.jump}\n'
    # Start a new Host section so following preserved global lines cannot accidentally apply to the frame.
    updated=block+'Host *\n'+end+original
path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
if path.exists():
    fd,backup=tempfile.mkstemp(prefix=path.name+'.before-nix-free-frame-',dir=path.parent);os.close(fd);shutil.copyfile(path,backup);os.chmod(backup,0o600)
fd,temp=tempfile.mkstemp(dir=path.parent)
try:
    with os.fdopen(fd,'w') as out:out.write(updated)
    os.chmod(temp,0o600)
    # Parse before atomic installation. -G does not connect.
    subprocess.run(['ssh','-G','-F',temp,alias],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True,timeout=10)
    os.replace(temp,path)
finally:
    if Path(temp).exists():Path(temp).unlink()
print('Managed SSH alias updated with private backup; unrelated content retained. Verify using this command.')
