import {syncAlbum} from './core';
import {readConfig,runtimeDirectory} from './config';
try { console.log(JSON.stringify(await syncAlbum(await readConfig(),await runtimeDirectory()))); }
catch { console.error('Album sync failed; last good cache retained. Check connectivity, album sharing, or private sync lock.'); process.exitCode=1; }
