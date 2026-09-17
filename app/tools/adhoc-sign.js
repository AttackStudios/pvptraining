// electron-builder afterPack hook.
// We have no Apple developer certificate, so the Mac build cannot be notarized. But it still
// has to carry a VALID signature: electron-builder rewrites the bundle (name, icon, resources)
// and leaves Electron's original ad-hoc signature broken, and macOS refuses a quarantined app
// with a broken signature as "damaged" with no way to open it. Re-signing ad hoc makes it an
// ordinary "unidentified developer" app, which the user can allow in Privacy & Security.
const { execFileSync } = require('child_process');
const path = require('path');

exports.default = async function adhocSign(context) {
  if (context.electronPlatformName !== 'darwin') return;
  const app = path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`);
  execFileSync('/usr/bin/codesign', ['--force', '--deep', '--sign', '-', app], { stdio: 'inherit' });
  execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', app], { stdio: 'inherit' });
  console.log(`  • ad-hoc signed ${path.basename(app)} (${context.arch === 3 ? 'arm64' : 'x64'})`);
};
