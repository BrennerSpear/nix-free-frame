#!/usr/bin/env python3
"""Install/update private macOS jobs atomically, retaining previous plists for rollback."""
import argparse
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import sys
from env_config import load_env, resolve_path

project = Path(__file__).resolve().parent.parent

def run(args, check=True):
    return subprocess.run(args, check=check, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)

def write_plist(path, value):
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('xb') as stream:
        os.chmod(temporary, 0o600)
        plistlib.dump(value, stream)
    temporary.replace(path)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group()
    choice.add_argument('--update', action='store_true')
    choice.add_argument('--status', action='store_true')
    choice.add_argument('--remove', action='store_true')
    choice.add_argument('--rollback', action='store_true')
    args = parser.parse_args()
    if sys.platform != 'darwin':
        raise ValueError('LaunchAgents require macOS.')
    env = load_env()
    prefix = env.get('LAUNCH_AGENT_PREFIX', 'org.nixfreeframe')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9.-]+', prefix):
        raise ValueError('Invalid LAUNCH_AGENT_PREFIX.')
    bun = shutil.which('bun')
    if not bun:
        raise ValueError('Bun is required.')
    # Use the same validator as the server, without printing private settings.
    run([bun, 'run', str(project/'scripts/configure.ts'), 'doctor'])
    agents = Path.home()/'Library/LaunchAgents'
    logs = resolve_path(env.get('RUNTIME_DIR', 'runtime'))/'logs'
    backups = resolve_path(env.get('RUNTIME_DIR', 'runtime'))/'launchagent-backup'
    domain = f'gui/{os.getuid()}'
    run(['launchctl', 'print', domain])
    definitions = []
    for role, entrypoint in [('server', 'server.ts'), ('sync', 'sync-cli.ts')]:
        label = f'{prefix}.{role}'
        target = agents/f'{label}.plist'
        definition = {
            'Label': label,
            'ProgramArguments': [bun, 'run', str(project/'src'/entrypoint)],
            'WorkingDirectory': str(project),
            'EnvironmentVariables': {'PATH': f'{Path(bun).parent}:/usr/bin:/bin:/usr/sbin:/sbin',
                                     'FRAME_ENV_FILE': str(Path(os.environ.get('FRAME_ENV_FILE', str(project/'.env'))).resolve())},
            'StandardOutPath': str(logs/f'{role}.log'),
            'StandardErrorPath': str(logs/f'{role}.error.log'),
        }
        if role == 'server':
            definition.update(RunAtLoad=True, KeepAlive=True, ThrottleInterval=30)
        else:
            hour, minute = int(env.get('SYNC_HOUR', '9')), int(env.get('SYNC_MINUTE', '0'))
            if not 0 <= hour <= 23 or not 0 <= minute <= 59:
                raise ValueError('Invalid sync schedule.')
            definition['StartCalendarInterval'] = {'Hour': hour, 'Minute': minute}
        definitions.append((label, target, definition))
    if args.status:
        for label, target, _ in definitions:
            loaded = run(['launchctl', 'print', f'{domain}/{label}'], check=False).returncode == 0
            print(f'{label}: plist={target.exists()} loaded={loaded}')
        return
    # Every mutation verifies exact ownership first, including retirement and rollback.
    def owned(path, label, role):
        value = plistlib.loads(path.read_bytes())
        if value.get('Label') != label or value.get('WorkingDirectory') != str(project) or value.get('ProgramArguments', [])[-1:] != [str(project/'src'/f'{role}.ts')]:
            raise ValueError('Job belongs to another project; refusing mutation.')
    for label, target, _ in definitions:
        role = 'server' if label.endswith('.server') else 'sync-cli'
        if target.exists():
            owned(target, label, role)
        elif run(['launchctl', 'print', f'{domain}/{label}'], check=False).returncode == 0:
            raise ValueError('Loaded job has no owned plist; refusing mutation.')
        if args.rollback and (backups/target.name).exists():
            owned(backups/target.name, label, role)
    agents.mkdir(parents=True, exist_ok=True)
    logs.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.remove:
        backups.mkdir(mode=0o700, parents=True, exist_ok=True)
        if any(target.exists() and (backups/(target.name+'.removed')).exists() for _,target,_ in definitions):
            raise ValueError('Removal backup exists; inspect before retry.')
        for label, target, _ in definitions:
            run(['launchctl', 'bootout', f'{domain}/{label}'], check=False)
            if target.exists():
                saved = backups/(target.name+'.removed')
                if saved.exists():
                    raise ValueError('Removal backup exists; inspect before retry.')
                target.replace(saved)
        print('Removed only configured jobs; private plists retained for recovery.')
        return
    if args.rollback:
        for label, target, _ in definitions:
            if not (backups/target.name).exists():
                raise ValueError('No previous plist backup for both jobs.')
        for label, target, _ in definitions:
            run(['launchctl', 'bootout', f'{domain}/{label}'], check=False)
            shutil.copy2(backups/target.name, target)
            run(['launchctl', 'bootstrap', domain, str(target)])
        print('Previous job definitions restored; configuration/source rollback is separate.')
        return
    for label, target, _ in definitions:
        if target.exists() and not args.update:
            raise ValueError('Jobs exist; use --update to back up and replace.')
        if not target.exists() and run(['launchctl', 'print', f'{domain}/{label}'], check=False).returncode == 0:
            raise ValueError('Job loaded without managed plist; inspect before replacing.')
        if target.exists():
            old = plistlib.loads(target.read_bytes())
            if old.get('Label') != label or old.get('WorkingDirectory') != str(project):
                raise ValueError('Existing job belongs to another project; refusing replacement.')
    previous = {}
    backups.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        for label, target, definition in definitions:
            previous[label] = target.read_bytes() if target.exists() else None
            if target.exists() and not (backups/target.name).exists():
                shutil.copy2(target, backups/target.name)
                os.chmod(backups/target.name, 0o600)
            write_plist(target, definition)
            run(['plutil', '-lint', str(target)])
        for label, target, _ in definitions:
            run(['launchctl', 'bootout', f'{domain}/{label}'], check=False)
            run(['launchctl', 'bootstrap', domain, str(target)])
        print('Both jobs installed; configured daily schedule uses host local timezone.')
    except BaseException:
        for label, target, _ in reversed(definitions):
            if label not in previous:
                continue
            run(['launchctl', 'bootout', f'{domain}/{label}'], check=False)
            if previous[label] is None:
                target.unlink(missing_ok=True)
            else:
                with target.open('wb') as stream:
                    stream.write(previous[label])
                os.chmod(target, 0o600)
                run(['launchctl', 'bootstrap', domain, str(target)], check=False)
        raise

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError):
        print('Job operation failed; previous definitions retained/restored. Run configure doctor and inspect local job state.', file=sys.stderr)
        sys.exit(1)
