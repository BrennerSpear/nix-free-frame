#!/usr/bin/env python3
"""Read-only source/history audit; optional fresh public snapshot. Never pushes."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_ROOTS = {'src', 'tests', 'scripts', 'android', 'patches', 'docs'}
PUBLIC_FILES = {'README.md', 'package.json', 'bun.lock', 'tsconfig.json', '.env.example', '.gitignore', '.gitattributes', 'LICENSE', 'NOTICE'}
GENERATED = {'AGENTS.md': 'Read docs/contributing.md\n', 'CLAUDE.md': 'Read docs/contributing.md\n'}
BLOCKED_SUFFIXES = {'.apk', '.keystore', '.jks', '.pem', '.key', '.jpg', '.jpeg', '.png', '.mp4', '.zip'}
PATTERNS = {
    'private-key': re.compile(rb'-----BEGIN [A-Z ]*PRIVATE KEY-----'),
    'user-home': re.compile(rb'/' + rb'(?:Users/[^\s"\'`<>]+|home/[A-Za-z][^\s"\'`<>]*)'),
    'private-address': re.compile(rb'\b(?:10\.(?:\d{1,3}\.){2}\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
    'tailnet-address': re.compile(rb'\b[\w.-]+\.ts\.net\b'),
    'ssh-public-key': re.compile(rb'\bssh-(?:ed25519|rsa) [A-Za-z0-9+/]{30,}'),
}


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], stderr=subprocess.DEVNULL)


def public_path(name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts:
        return False
    if name in PUBLIC_FILES:
        return True
    if not path.parts or path.parts[0] not in PUBLIC_ROOTS:
        return False
    if any(p in {'build', '.tools', '.gradle', '__pycache__', 'node_modules'} for p in path.parts):
        return False
    if path.name in {'config.properties', 'local.properties'} or path.suffix.lower() in BLOCKED_SUFFIXES:
        return False
    if path.suffix == '.jar' and name != 'android/gradle/wrapper/gradle-wrapper.jar':
        return False
    return True


def private_values():
    values = set()
    config = ROOT / 'runtime/config.json'
    if config.exists():
        data = json.loads(config.read_text())
        for name in ('albumUrl', 'frameToken'):
            value = data.get(name)
            if isinstance(value, str) and len(value) >= 8:
                values.add(value.encode())
    env = ROOT / '.env'
    if env.exists():
        for line in env.read_text().splitlines():
            key, sep, value = line.partition('=')
            if sep and re.search(r'(ALBUM|TOKEN|SERIAL)', key, re.I):
                value = value.strip()
                if value.startswith('"'):
                    value = json.loads(value)
                elif value.startswith("'") and value.endswith("'"):
                    value = value[1:-1]
                if len(value) >= (4 if 'SERIAL' in key else 8):
                    values.add(value.encode())
    return values


def findings(data, secrets, identities):
    result = [name for name, pattern in PATTERNS.items() if pattern.search(data)]
    if any(value in data for value in secrets):
        result.append('private-config-value')
    if any(value in data for value in identities):
        result.append('git-author-identity')
    return result


def scan_history(secrets, identities):
    blobs = 0
    hits = {}
    objects = git('rev-list', '--objects', '--all').decode().splitlines()
    for line in objects:
        oid = line.split(' ', 1)[0]
        if git('cat-file', '-t', oid).strip() != b'blob':
            continue
        blobs += 1
        for category in findings(git('cat-file', 'blob', oid), secrets, identities):
            hits[category] = hits.get(category, 0) + 1
    commits = git('rev-list', '--all').decode().splitlines()
    metadata_hits = {}
    for commit in commits:
        for category in findings(git('cat-file', 'commit', commit), secrets, identities):
            metadata_hits[category] = metadata_hits.get(category, 0) + 1
    # Metadata identity is itself private even when source contents are clean.
    return {'reachableBlobs': blobs, 'blobFindings': hits, 'commitMetadataFindings': metadata_hits, 'originalCommitsWithAuthorMetadata': len(commits)}


def tracked_current_audit(secrets, identities):
    hits = []
    for name in git('ls-files', '-z').decode().split('\0'):
        path = ROOT / name
        if name and path.is_file() and not path.is_symlink():
            categories = findings(path.read_bytes(), secrets, identities)
            if categories:
                hits.append({'path': name, 'excludedFromPublic': not public_path(name), 'categories': categories})
    return hits


def public_snapshot(secrets, identities):
    names = git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')
    snapshot = {}
    failures = []
    for name in sorted(set(names)):
        if not name or not public_path(name):
            continue
        path = ROOT / name
        if path.is_symlink() or not path.is_file():
            failures.append({'path': name, 'categories': ['non-regular-file']})
            continue
        data = path.read_bytes()
        if name == 'android/gradle/wrapper/gradle-wrapper.jar' and hashlib.sha256(data).hexdigest() != '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17':
            failures.append({'path': name, 'categories': ['unverified-wrapper-jar']})
        # These two synthetic LAN fixtures exercise private-bind validation, not this installation.
        scanned = data
        if name == 'scripts/ssh-client.py':
            # RFC1918 CIDR definitions in the endpoint validator are product constants.
            for fixture in (b'.'.join([b'10', b'0', b'0', b'0']) + b'/8', b'.'.join([b'172', b'16', b'0', b'0']) + b'/12', b'.'.join([b'192', b'168', b'0', b'0']) + b'/16'):
                scanned = scanned.replace(fixture, b'RFC1918_NETWORK')
        if name == 'tests/config.test.ts':
            for fixture in (b'.'.join([b'192', b'168', b'1', b'2']), b'.'.join([b'192', b'168', b'1', b'3'])):
                scanned = scanned.replace(fixture, b'TEST_LAN_FIXTURE')
        categories = findings(scanned, secrets, identities)
        if categories:
            failures.append({'path': name, 'categories': categories})
        snapshot[name] = data
    snapshot.update({name: value.encode() for name, value in GENERATED.items()})
    return snapshot, failures


def export(snapshot, target):
    target = target.absolute()
    if target.exists():
        raise ValueError('Output must not exist; an existing checkout is never replaced')
    if target == ROOT or ROOT in target.parents:
        raise ValueError('Export must be outside the original checkout')
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = Path(tempfile.mkdtemp(prefix='.public-export-', dir=target.parent))
    try:
        for name, data in snapshot.items():
            path = temp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            source = ROOT / name
            if source.exists() and os.access(source, os.X_OK):
                path.chmod(0o755)
        git_env = {**os.environ, 'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_CONFIG_SYSTEM': os.devnull}
        subprocess.run(['git', '-c', 'init.templateDir=', 'init', '-q', '-b', 'main', str(temp)], check=True, env=git_env)
        subprocess.run(['git', '-C', str(temp), '-c', 'core.attributesFile=' + os.devnull, 'add', '--all'], check=True, env=git_env)
        subprocess.run(['git', '-C', str(temp), '-c', 'core.hooksPath=' + os.devnull, '-c', 'user.name=Frame Project', '-c', 'user.email=frame-project@example.invalid', 'commit', '-q', '-m', 'Initial reusable source snapshot'], check=True,
                       env={**os.environ, 'GIT_AUTHOR_NAME': 'Frame Project', 'GIT_AUTHOR_EMAIL': 'frame-project@example.invalid', 'GIT_COMMITTER_NAME': 'Frame Project', 'GIT_COMMITTER_EMAIL': 'frame-project@example.invalid', 'GIT_AUTHOR_DATE': '2000-01-01T00:00:00Z', 'GIT_COMMITTER_DATE': '2000-01-01T00:00:00Z', 'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_CONFIG_SYSTEM': os.devnull})
        for name, data in snapshot.items():
            if (temp / name).read_bytes() != data or subprocess.check_output(['git', '-C', str(temp), 'show', 'HEAD:' + name], env=git_env) != data:
                raise ValueError('Export readback failed')
        if subprocess.check_output(['git', '-C', str(temp), 'rev-list', '--count', '--all']).strip() != b'1':
            raise ValueError('Export does not have fresh history')
        if subprocess.check_output(['git', '-C', str(temp), 'remote']).strip():
            raise ValueError('Export unexpectedly has a remote')
        temp.rename(target)
    finally:
        if temp.exists():
            shutil.rmtree(temp)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='Create a sanitized fresh-history checkout at a new outside path')
    parser.add_argument('--skip-history', action='store_true', help='Only recheck current public snapshot; full history audit is default')
    args = parser.parse_args()
    secrets = private_values()
    identities = set()
    for line in git('log', '--all', '--format=%an%x00%ae%x00%cn%x00%ce').splitlines():
        identities.update(v for v in line.split(b'\0') if len(v) >= 8 and v != b'Frame Project' and not v.endswith(b'@example.invalid'))
    snapshot, failures = public_snapshot(secrets, identities)
    report = {'publicFiles': len(snapshot), 'currentPublicFindings': failures,
              'privateConfigValuesChecked': len(secrets),
              'currentTrackedFindings': tracked_current_audit(secrets, identities),
              'snapshotSha256': hashlib.sha256(b''.join(n.encode() + b'\0' + snapshot[n] for n in sorted(snapshot))).hexdigest()}
    if not args.skip_history:
        report['originalHistory'] = scan_history(secrets, identities)
    if failures:
        report['export'] = 'refused: current public files require review'
    elif args.output:
        export(snapshot, args.output)
        report['export'] = 'created: fresh anonymous local history, no remote'
    else:
        report['export'] = 'audit only; original history remains private'
    print(json.dumps(report, indent=2))
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        # Do not print command arguments or exception payloads that could contain private state.
        print(json.dumps({'error': type(error).__name__, 'result': 'audit/export did not complete'}))
        raise SystemExit(1)
