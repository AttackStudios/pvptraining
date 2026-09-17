// Copies the shared catalog into the app and the mod, and bundles the newest
// built mod jar into the app's resources so the guides can hand it out.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const catalog = path.join(root, 'shared', 'catalog.json');

const copies = [
  path.join(root, 'app', 'renderer', 'catalog.json'),
  path.join(root, 'mod', 'src', 'main', 'resources', 'pvptraining', 'catalog.json'),
];
for (const dest of copies) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(catalog, dest);
  console.log('catalog ->', path.relative(root, dest));
}

const libs = path.join(root, 'mod', 'build', 'libs');
const outDir = path.join(root, 'app', 'resources', 'mod');
fs.mkdirSync(outDir, { recursive: true });
if (fs.existsSync(libs)) {
  const jars = fs
    .readdirSync(libs)
    .filter((f) => f.endsWith('.jar') && !f.includes('-sources') && !f.includes('-dev'))
    .map((f) => ({ f, t: fs.statSync(path.join(libs, f)).mtimeMs }))
    .sort((a, b) => b.t - a.t);
  if (jars.length) {
    for (const old of fs.readdirSync(outDir)) if (old.endsWith('.jar')) fs.rmSync(path.join(outDir, old));
    fs.copyFileSync(path.join(libs, jars[0].f), path.join(outDir, jars[0].f));
    console.log('mod jar ->', path.join('app', 'resources', 'mod', jars[0].f));
  } else console.warn('No built mod jar in mod/build/libs. Run ./gradlew build in mod/ first.');
} else console.warn('mod/build/libs does not exist yet. Run ./gradlew build in mod/ first.');
