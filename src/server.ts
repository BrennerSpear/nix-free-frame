import {readConfig,runtimeDirectory} from './config';
import {handleRequest} from './core';
const config=await readConfig();
const directory=await runtimeDirectory();
const server=Bun.serve({hostname:config.bindAddress,port:config.port,fetch:request=>handleRequest(request,directory,config.frameToken)});
console.log(`Local photo service listening on configured interface, port ${server.port}`);
