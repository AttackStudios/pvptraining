// Runs the social backend on its own: node server/standalone.mjs
//   PORT (default 8790), PVPT_DATA_DIR (default ./data), PVPT_ALLOW_UNVERIFIED=1 for local testing
//   without real Minecraft accounts.
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createSocial } from './social.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const handler = createSocial({
  dataDir: process.env.PVPT_DATA_DIR || path.join(here, 'data'),
  allowUnverified: process.env.PVPT_ALLOW_UNVERIFIED === '1',
  log: console.log,
});
const port = Number(process.env.PORT) || 8790;
http
  .createServer((req, res) => {
    if (req.url.startsWith('/pvpt')) req.url = req.url.slice(5) || '/';
    handler(req, res);
  })
  .listen(port, () => console.log(`PVPTraining social backend on http://127.0.0.1:${port}/pvpt`));
