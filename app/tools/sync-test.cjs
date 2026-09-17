// Reproduces "PC has ~204 mastery, laptop has 11, same account" with the REAL app sync code.
// Needs the test backend: PVPT_ALLOW_UNVERIFIED=1 PORT=8796 node server/standalone.mjs
//   node app/tools/sync-test.cjs [path-to-a-real-laptop-progress.json]
const fs = require('fs');
const os = require('os');
const path = require('path');

process.env.PVPT_SOCIAL_URL = process.env.PVPT_SOCIAL_URL || 'http://127.0.0.1:8796/pvpt';
const { Social } = require('../src/social');
const { total } = require('../src/progress-merge');

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'pvpt-sync-'));
let failures = 0;
const check = (label, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? `  (${extra})` : ''}`);
  if (!ok) failures++;
};
const readTotal = (dir) => total(JSON.parse(fs.readFileSync(path.join(dir, 'progress.json'), 'utf8')).modes);

function device(name, progress) {
  const dir = path.join(work, name);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'progress.json'), JSON.stringify(progress));
  let settings = {};
  const social = new Social({ getSettings: () => settings, saveSettings: (p) => (settings = { ...settings, ...p }), getBridge: () => null });
  return {
    dir,
    social,
    signInWith: (session) => (settings.social = session),
    sync: async () => {
      process.env.PVPT_HOME = dir; // each "device" has its own ~/.pvptraining
      return social.sync();
    },
  };
}

const d = (best, medal, runs = 3) => ({ best, medal, runs });
const pcProgress = {
  schema: 1,
  history: [{ ts: 1, mode: 'mace', name: 'Launch & Smash', result: '252 dmg · Gold' }],
  modes: {
    mace: { mastery: 90, unlocked: true, drills: { mace_smash: d(252, 3), mace_chase: d(9, 3), mace_chain: d(6, 3) }, duels: { rookie: { matchWins: 3, matchLosses: 0 }, elite: { matchWins: 1, matchLosses: 4 } } },
    crystal: { mastery: 57.8, unlocked: true, drills: { crystal_speed: d(31, 2), crystal_pops: d(8, 2), crystal_survive: d(30, 1) }, duels: { veteran: { matchWins: 2, matchLosses: 2 } } },
    sword: { mastery: 58.9, unlocked: true, drills: { sword_combo: d(11, 3), sword_wtap: d(21, 2), sword_crit: d(16, 2) }, duels: { fighter: { matchWins: 1, matchLosses: 0 } } },
    spear: { mastery: 0, unlocked: true, drills: {}, duels: {} },
    elytra_mace: { mastery: 0, unlocked: true, drills: { ely_climb: d(5.2, 2) }, duels: {} },
  },
};
const laptopFile = process.argv[2];
const laptopProgress = laptopFile
  ? JSON.parse(fs.readFileSync(laptopFile, 'utf8'))
  : { schema: 1, history: [], modes: { mace: { mastery: 11.1, unlocked: true, drills: { mace_smash: d(173.4, 2, 1) }, duels: {} }, crystal: { mastery: 0, unlocked: true, drills: { crystal_speed: d(0, 0, 1) }, duels: { veteran: { matchWins: 0, matchLosses: 1 } } }, elytra_mace: { mastery: 0, drills: { ely_climb: d(8.8, 1, 1) }, duels: {} } } };

(async () => {
  const base = process.env.PVPT_SOCIAL_URL;
  const post = async (route, body) => (await fetch(base + route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })).json();
  const { nonce } = await post('/auth/begin', { name: `Sync${Math.random().toString(36).slice(2, 8)}` });
  const session = await post('/auth/finish', { nonce });
  const pc = device('pc', pcProgress);
  const laptop = device('laptop', laptopProgress);
  pc.signInWith({ token: session.token, me: session.me });
  laptop.signInWith({ token: session.token, me: session.me });

  const pcStart = readTotal(pc.dir);
  const laptopStart = readTotal(laptop.dir);
  console.log(`start: PC ${pcStart} mastery, laptop ${laptopStart} mastery`);

  // The order it really happened: the laptop signed in and uploaded first...
  const l1 = await laptop.sync();
  check('laptop syncs first (account holds only the laptop numbers so far)', l1.ok);
  // ...then the PC comes online.
  const p1 = await pc.sync();
  check('PC sync succeeds', p1.ok);
  check("PC's mastery is NOT lowered by the laptop's smaller numbers", readTotal(pc.dir) >= pcStart - 0.05, `${pcStart} -> ${readTotal(pc.dir)}`);
  // Next time the laptop syncs it inherits everything.
  const l2 = await laptop.sync();
  check('laptop receives the PC progress', l2.ok && l2.changedLocal && readTotal(laptop.dir) >= pcStart - 0.05, `${laptopStart} -> ${readTotal(laptop.dir)}`);
  const onLaptop = JSON.parse(fs.readFileSync(path.join(laptop.dir, 'progress.json'), 'utf8')).modes;
  check('PC bests, medals and duel wins are on the laptop', onLaptop.mace.drills.mace_smash.best === 252 && onLaptop.mace.drills.mace_smash.medal === 3 && onLaptop.mace.duels.elite.matchWins === 1);
  check('branches unlocked on the PC are unlocked on the laptop', onLaptop.spear.unlocked === true && onLaptop.elytra_mace.unlocked === true);
  check('lower-is-better drill keeps the faster time', onLaptop.elytra_mace.drills.ely_climb.best === 5.2);
  check("the laptop's own records survive too", onLaptop.crystal.duels.veteran.matchLosses >= 1);
  await pc.sync();
  check('both devices end up identical in total', Math.abs(readTotal(pc.dir) - readTotal(laptop.dir)) < 0.05, `PC ${readTotal(pc.dir)} / laptop ${readTotal(laptop.dir)}`);
  const me = await (await fetch(`${base}/me`, { headers: { Authorization: `Bearer ${session.token}` } })).json();
  check('the account (friends, leaderboards) shows the full total', Math.abs(me.me.totalMastery - readTotal(pc.dir)) < 0.05, `server ${me.me.totalMastery}`);
  const again = await laptop.sync();
  check('syncing again changes nothing (stable)', again.ok && !again.changedLocal);
  check('local session history is kept', JSON.parse(fs.readFileSync(path.join(pc.dir, 'progress.json'), 'utf8')).history.length === 1);

  fs.rmSync(work, { recursive: true, force: true });
  console.log(failures ? `\n${failures} FAILED` : '\nALL PASSED');
  process.exit(failures ? 1 : 0);
})();
