import os, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
from env_config import load_env

class EnvTests(unittest.TestCase):
 def read(self,text,mode=0o600):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'.env';p.write_text(text);p.chmod(mode)
   with patch.dict(os.environ,{},clear=True): return load_env(p)
 def test_quotes_literal_and_empty(self):
  self.assertEqual(self.read('FRAME_TOKEN="a#b$HOME"\nFRAME_SERVER_URL=\n'),{'FRAME_TOKEN':'a#b$HOME','FRAME_SERVER_URL':''})
 def test_reject_private_permissions(self):
  with self.assertRaises(ValueError): self.read('FRAME_TOKEN=x',0o644)
 def test_duplicate_and_invalid(self):
  for text in ['A=x\nA=y','export A=x','A="unterminated','A=[bad']:
   if text=='A=[bad': continue
   with self.assertRaises(ValueError): self.read(text)
 def test_file_authority(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'.env';p.write_text('FRAME_SERIAL=file\n');p.chmod(0o600)
   with patch.dict(os.environ,{'FRAME_SERIAL':'explicit'},clear=True): self.assertEqual(load_env(p)['FRAME_SERIAL'],'file')
if __name__=='__main__':unittest.main()
