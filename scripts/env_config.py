"""Private .env reader shared by host tools. No shell evaluation/interpolation."""
import json, os, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def load_env(path=None):
    source = Path(path or os.environ.get('FRAME_ENV_FILE', ROOT / '.env'))
    result = {}
    if not source.is_file(): raise ValueError('Private .env missing; run configure/migrate first')
    if source.exists():
        if source.stat().st_mode & 0o777 != 0o600:
            raise ValueError('Private .env must have mode 600')
        for line in source.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith('#'): continue
            match = re.fullmatch(r'([A-Z][A-Z0-9_]*)\s*=\s*(.*)', line)
            if not match: raise ValueError('Invalid .env assignment')
            key, value = match.groups()
            if key in result: raise ValueError('Duplicate .env key')
            if value.startswith('"'):
                try: value = json.loads(value)
                except ValueError: raise ValueError('Invalid quoted .env value') from None
                if not isinstance(value, str): raise ValueError('Invalid .env value')
            elif value.startswith("'"):
                if not value.endswith("'") or len(value)<2: raise ValueError('Invalid quoted .env value')
                value=value[1:-1]
            elif re.search(r'\s|#',value): raise ValueError('Unquoted .env whitespace/comment is ambiguous')
            if any(c in value for c in ['\0','\r','\n']): raise ValueError('Control characters in .env value')
            result[key] = value
    return result

def resolve_path(value):
    if value.startswith('~') and value!='~' and not value.startswith('~/'): raise ValueError('Cross-account home expansion refused')
    path = Path(value).expanduser()
    return path if path.is_absolute() else ROOT / path
