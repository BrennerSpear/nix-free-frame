"""Run with python3 tests/public-export.test.py; does not touch the original checkout."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

module_spec = importlib.util.spec_from_file_location('public_export', Path(__file__).resolve().parents[1] / 'scripts/public-export.py')
module = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(module)

class PublicExportTest(unittest.TestCase):
    def test_private_paths_are_never_candidates(self):
        for name in ['runtime/proof.json', '.env', 'spec/recovery.md', 'android/config.properties', 'android/.tools/tool.jar', 'android/app/build/client.apk', 'docs/screenshot.png', 'scripts/id_ed25519.pem', '../src/escape.ts']:
            self.assertFalse(module.public_path(name), name)
        for name in ['.env.example', 'android/gradle/wrapper/gradle-wrapper.jar', 'src/server.ts', 'docs/how-we-did-it.md']:
            self.assertTrue(module.public_path(name), name)

    def test_secret_scan_reports_only_categories(self):
        secret = b'fixture-private-token-123'
        result = module.findings(b'prefix ' + secret, {secret}, set())
        self.assertEqual(result, ['private-config-value'])
        self.assertNotIn(secret.decode(), str(result))

    def test_export_has_fresh_safe_history_and_readback(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'public'
            previous = {name: os.environ.get(name) for name in ['GIT_AUTHOR_NAME', 'GIT_AUTHOR_EMAIL']}
            try:
                os.environ['GIT_AUTHOR_NAME'] = 'Private Author'
                os.environ['GIT_AUTHOR_EMAIL'] = 'private@example.test'
                module.export({'README.md': b'Public fixture\n', '.env.example': b'TOKEN=placeholder\n'}, target)
            finally:
                for name, value in previous.items():
                    if value is None:
                        os.environ.pop(name, None)
                    else:
                        os.environ[name] = value
            self.assertEqual((target / 'README.md').read_bytes(), b'Public fixture\n')
            self.assertEqual(subprocess.check_output(['git', '-C', str(target), 'rev-list', '--count', '--all']).strip(), b'1')
            metadata = subprocess.check_output(['git', '-C', str(target), 'log', '--format=%an %ae %cn %ce'])
            self.assertNotIn(b'Private Author', metadata)
            self.assertNotIn(b'private@example.test', metadata)
            self.assertEqual(subprocess.check_output(['git', '-C', str(target), 'remote']).strip(), b'')
            with self.assertRaises(ValueError):
                module.export({'README.md': b'replaced'}, target)
            self.assertEqual((target / 'README.md').read_bytes(), b'Public fixture\n')

if __name__ == '__main__':
    unittest.main()
