// Dev only (`npm run shots`): renders each screen offscreen with sample data
// and writes PNGs, so UI changes can be checked without clicking through.
const fs = require('fs');
const path = require('path');

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const SAMPLE = {
  modes: {
    mace: { mastery: 86, unlocked: true, drills: { mace_smash: { best: 252, medal: 3 }, mace_chase: { best: 7, medal: 2 }, mace_chain: { best: 4, medal: 2 } }, duels: { rookie: { matchWins: 3, matchLosses: 0 }, fighter: { matchWins: 2, matchLosses: 1 }, veteran: { matchWins: 1, matchLosses: 2 }, elite: { matchWins: 1, matchLosses: 4 } } },
    crystal: { mastery: 34, unlocked: true, drills: { crystal_speed: { best: 22, medal: 1 }, crystal_pops: { best: 5, medal: 1 } }, duels: { rookie: { matchWins: 1, matchLosses: 1 } } },
    spear: { mastery: 12, unlocked: true, drills: { spear_swap: { best: 9, medal: 1 } }, duels: {} },
    elytra_mace: { mastery: 0, unlocked: true, drills: {}, duels: {} },
  },
  history: [
    { ts: Date.now() - 86400000, mode: 'mace', name: 'Duel: Elite bot', result: 'Won 3-2' },
    { ts: Date.now() - 3600000, mode: 'mace', name: 'Launch & Smash', result: '252 dmg · Gold' },
    { ts: Date.now() - 600000, mode: 'spear', name: 'Attribute Swap', result: '9 lunges · Bronze' },
  ],
};

async function run(win) {
  const out = process.env.PVPT_SHOTS_DIR || path.join(process.cwd(), 'shots');
  fs.mkdirSync(out, { recursive: true });
  const js = (code) => win.webContents.executeJavaScript(code);
  await new Promise((r) => win.webContents.once('did-finish-load', r));
  await wait(600);
  win.showInactive();
  const snap = async (name, code, delay = 1500) => {
    await js(code);
    await wait(delay);
    const img = await win.webContents.capturePage();
    fs.writeFileSync(path.join(out, `${name}.png`), img.toPNG());
    console.log('shot', name);
  };
  const p = 'window.__pvpt';
  await snap('01-welcome', `${p}.showWelcome()`);
  await snap('02-picker', `${p}.showPicker()`);
  for (const b of ['dawn', 'lunar', 'modrinth', 'other']) await snap(`03-guide-${b}`, `${p}.showGuide('${b}')`, 1700);
  await js(`${p}.state.progress = ${JSON.stringify(SAMPLE)}; ${p}.state.settings.launcher='lunar'; ${p}.showShell()`);
  await snap('04-train', `void 0`);
  await snap('05-train-ready', `${p}.state.conn.instance={username:'Trainee',mcVersion:'1.21.11',launcher:'Lunar Client'}; ${p}.state.game.player='Trainee'; ${p}.state.game.inWorld=true; ${p}.setConn('ready')`);
  await snap('06-mode', `${p}.state.modeId='mace'; ${p}.state.pick={type:'drill',id:'mace_smash'}; ${p}.renderPage()`);
  await snap('07-live', `${p}.state.modeId=null; ${p}.state.feed=[{time:'00:12',text:'Smash for 38.5 damage from 14 blocks',tone:'good'},{time:'00:20',text:'Missed the dummy',tone:'bad'}]; ${p}.state.game.session={mode:'mace',name:'Launch & Smash',stats:[{label:'Score',value:'112'},{label:'Attempts left',value:'6'},{label:'Best smash',value:'38.5'},{label:'Accuracy',value:'75%'}]}; ${p}.setConn('live'); ${p}.renderPage()`);
  await snap('08-tree', `${p}.state.game.session=null; ${p}.goPage('tree', true)`, 2200);
  await snap('09-progress', `${p}.goPage('progress', true)`, 2200);
  await snap('10-setup', `${p}.goPage('setup', true)`);
  if (process.env.PVPT_SHOTS_SOCIAL) {
    // needs a signed-in profile and a reachable friends service (see server/standalone.mjs)
    const click = (sel) => `document.querySelector(${JSON.stringify(sel)})?.click()`;
    await snap('11-friends', `${p}.state.game.session=null; ${p}.setConn('ready'); ${p}.goPage('friends', true)`, 3000);
    await snap('12-friend-compare', `(document.querySelector('[data-friend]:has(.unread)') || document.querySelector('[data-friend]'))?.click(); setTimeout(() => ${click('[data-tab="compare"]')}, 600)`, 3600);
    await snap('13-friend-messages', click('[data-tab="messages"]'), 3000);
    await snap('14-friend-challenges', click('[data-tab="challenges"]'), 2500);
    await snap('15-leaderboard', `${p}.goPage('ranks', true)`, 3500);
  }
  require('electron').app.quit();
}

module.exports = { run };
