"""Safety gates are tested without any USB/device access."""
import runpy, subprocess, sys, unittest, contextlib, io
from unittest.mock import patch
from env_config import ROOT

class DeviceGateTests(unittest.TestCase):
 def invoke(self, overrides=None, serial='USB_TEST'):
  calls=[]
  values={'get-state':'device','get-devpath':'usb:1-1','ro.product.model':'w10f09','ro.build.version.sdk':'25','ro.product.cpu.abi':'armeabi-v7a','sys.boot_completed':'1'}
  values.update(overrides or {})
  def run(command,**kwargs):
   calls.append(command)
   return subprocess.CompletedProcess(command,0,values.get(command[-1],''),'')
  with patch('env_config.load_env',return_value={'FRAME_SERIAL':serial}),patch('subprocess.run',side_effect=run),patch.object(sys,'argv',['device.py','home']):
   with self.assertRaises(SystemExit),contextlib.redirect_stderr(io.StringIO()):runpy.run_path(str(ROOT/'scripts/device.py'),run_name='__main__')
  return calls
 def assertNoMutation(self,calls):
  for c in calls:
   self.assertNotIn('set-home-activity',c);self.assertNotIn('start',c);self.assertNotIn('install',c)
 def test_network_serial_refused_without_adb(self):self.assertEqual(self.invoke(serial='192.0.2.1:5555'),[])
 def test_wrong_hardware_refused(self):self.assertNoMutation(self.invoke({'ro.product.model':'OTHER'}))
 def test_incompatible_api_refused(self):self.assertNoMutation(self.invoke({'ro.build.version.sdk':'34'}))
 def test_wrong_abi_refused(self):self.assertNoMutation(self.invoke({'ro.product.cpu.abi':'arm64-v8a'}))
 def test_not_booted_refused(self):self.assertNoMutation(self.invoke({'sys.boot_completed':'0'}))
 def test_non_usb_transport_refused(self):self.assertNoMutation(self.invoke({'get-devpath':'local'}))
if __name__=='__main__':unittest.main()
