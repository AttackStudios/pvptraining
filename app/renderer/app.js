import { icon, hydrateIcons } from './icons.js';
import { BRANDS, BRAND_ORDER, VIDEO_URL } from './brands.js';
import { play, setMuted, isMuted, wireGlobalSounds } from './sounds.js';

const api = window.pvpt;
const stage = document.getElementById('stage');
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const state = {
  info: null,
  settings: {},
  catalog: null,
  progress: null,
  page: 'train',
  modeId: null,
  pick: null, // { type: 'drill' | 'duel', id }
  conn: { state: 'idle', instance: null, error: null },
  game: { inWorld: false, player: null, session: null },
  feed: [],
  pollTimer: null,
};

/* ------------------------------------------------------------------ utils */

function toast(text, tone = '', silent = false) {
  if (!silent) play(tone === 'bad' ? 'error' : 'toast');
  const el = document.createElement('div');
  el.className = `toast ${tone}`;
  el.textContent = text;
  $('#toasts').append(el);
  setTimeout(() => {
    el.classList.add('out');
    setTimeout(() => el.remove(), 340);
  }, 3600);
}

function setAccent(a, b) {
  const root = document.documentElement.style;
  root.setProperty('--accent', a || 'var(--ember)');
  root.setProperty('--accent-2', b || a || 'var(--ember-2)');
}

function swapView(el) {
  const old = $('.view.in', stage);
  if (old) play('view');
  stage.append(el);
  hydrateIcons(el);
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('in')));
  if (old) {
    old.classList.remove('in');
    old.classList.add('out');
    setTimeout(() => old.remove(), 520);
  }
}

function view(cls, html) {
  const el = document.createElement('section');
  el.className = `view ${cls}`;
  el.innerHTML = html;
  return el;
}

const modeOf = (id) => state.catalog.modes.find((m) => m.id === id);
const progOf = (id) => state.progress?.modes?.[id] || { drills: {}, duels: {}, mastery: 0, unlocked: false };
const isLocked = (mode) => Boolean(mode.parent) && !progOf(mode.id).unlocked;
const masteryOf = (id) => Math.round(progOf(id).mastery || 0);

function fmtScore(value, unit) {
  if (value === undefined || value === null) return 'No run yet';
  const n = Number.isInteger(value) ? value : Number(value).toFixed(1);
  return `${n} ${unit}`;
}

function medalDots(level) {
  return `<span class="medals">${[1, 2, 3].map((m) => `<i class="medal m${m} ${level >= m ? 'on' : ''}"></i>`).join('')}</span>`;
}

/* ------------------------------------------------------------ onboarding */

function showWelcome() {
  setAccent();
  const el = view(
    'welcome',
    `<div class="crest">${icon('logo')}</div>
     <span class="eyebrow rise" style="--i:1">Minecraft combat masterclass</span>
     <h1 class="rise" style="--i:2">Train like you mean it.<br/>Become a combat <em>Ace</em>.</h1>
     <p class="lede rise" style="--i:3">PVPTraining hooks into your game, drops you into a purpose-built practice world and puts you up against bots that fight like players. Drills, duels and skill branches that unlock as you master each kit.</p>
     <button class="btn primary big rise" style="--i:4" id="go">Get started <span data-icon="arrow"></span></button>
     <div class="facts rise" style="--i:6"><span><i></i>Fabric 1.21.11</span><span><i></i>Mace and Crystal to start</span><span><i></i>Works with any launcher</span></div>`
  );
  swapView(el);
  $('#go', el).onclick = showPicker;
}

function showPicker() {
  setAccent();
  const el = view(
    'picker scroll',
    `${state.settings.onboarded ? `<button class="btn ghost small back-link" id="back"><span data-icon="back"></span>Back</button>` : ''}
     <div class="picker-head">
       <span class="eyebrow rise">Step 1 of 2</span>
       <h2 class="rise" style="--i:1">Which launcher do you play on?</h2>
       <p class="rise" style="--i:2">We will show you exactly how to add the mod for that launcher.</p>
     </div>
     <div class="launcher-grid">
       ${BRAND_ORDER.map((id, i) => {
         const b = BRANDS[id];
         return `<button class="launcher rise" style="--i:${i + 3}" data-brand="${id}" data-pick="${id}">
           <span class="logo">${b.glyph}</span>
           <span><h3>${esc(b.name)}</h3><small>${esc(b.sub)}</small></span>
           <span class="go" data-icon="arrow"></span>
         </button>`;
       }).join('')}
     </div>`
  );
  swapView(el);
  $$('[data-pick]', el).forEach((btn) => {
    btn.onclick = async () => {
      state.settings = await api.settings.set({ launcher: btn.dataset.pick });
      showGuide(btn.dataset.pick);
    };
  });
  const back = $('#back', el);
  if (back) back.onclick = showShell;
}

async function showGuide(id) {
  const b = BRANDS[id];
  setAccent(b.accent, b.accent2);
  const targets = b.install === 'auto' ? await api.installer.targets(id) : [];
  const jarName = state.info.jar || 'pvptraining-0.1.0+mc1.21.11.jar';

  const stepHtml = (st, i) => `
    <div class="step rise" style="--i:${i + 2}">
      <div class="num"></div>
      <div>
        <h4>${esc(st.title)}</h4>
        <p>${st.body}</p>
        ${st.targets ? targetsHtml(targets, id) : ''}
      </div>
    </div>`;

  const el = view(
    '',
    `<div class="guide" data-brand="${id}">
       <aside class="guide-side">
         <button class="btn ghost small back-link" id="back"><span data-icon="back"></span>Launchers</button>
         <div class="logo">${b.glyph}</div>
         <span class="eyebrow" style="color:var(--b-accent)">Step 2 of 2</span>
         <h2>Set up ${esc(b.name)}</h2>
         <p>${esc(b.intro)}</p>
         <div class="guide-files">
           <label>Your files</label>
           <div class="version-select" aria-disabled="true" title="More versions are coming. For now it is Fabric 1.21.11 only.">
             <span class="lk"><span data-icon="lock"></span>Fabric 1.21.11</span><em>only version for now</em>
           </div>
           <div class="jar-chip" id="jar" draggable="true" title="Drag this into your launcher or mods folder">
             <span class="ico">${icon('jar')}</span>
             <span><b>${esc(jarName)}</b><span>Drag me out, or save a copy</span></span>
           </div>
           <div class="side-actions">
             <button class="btn small" id="save"><span data-icon="download"></span>Save</button>
             <button class="btn small" id="reveal"><span data-icon="folder"></span>Show file</button>
           </div>
         </div>
         <div class="note">${esc(b.note)}</div>
       </aside>
       <div class="guide-main scroll">
         ${b.video ? `<button class="video-card rise" style="--i:1" id="video"><span class="thumb"><span class="play"></span></span><span><b>How To Download &amp; Install Fabric Mods (1.21.11)</b><span>The Breakdown · 4 min · opens YouTube</span></span></button>` : ''}
         <div class="steps">${b.steps.map(stepHtml).join('')}</div>
         <div class="guide-foot rise" style="--i:${b.steps.length + 3}">
           <span class="hint">The mod only loads on Fabric 1.21.11. Newer versions refuse to start with it.</span>
           <button class="btn primary big" id="done">I have installed it <span data-icon="arrow"></span></button>
         </div>
       </div>
     </div>`
  );
  swapView(el);

  $('#back', el).onclick = showPicker;
  $('#save', el).onclick = async () => {
    const res = await api.installer.saveAs();
    if (res.ok) toast('Saved the mod file.', 'good');
    else if (!res.canceled) toast(res.error, 'bad');
  };
  $('#reveal', el).onclick = async () => {
    if (!(await api.installer.reveal())) toast('The mod file is missing from this build.', 'bad');
  };
  $('#jar', el).addEventListener('dragstart', (e) => {
    e.preventDefault();
    api.installer.drag();
  });
  const video = $('#video', el);
  if (video) video.onclick = () => api.open(VIDEO_URL);
  $('#done', el).onclick = async () => {
    state.settings = await api.settings.set({ onboarded: true });
    showShell();
  };
  bindTargets(el);
}

function targetsHtml(targets, launcher) {
  const rows = targets
    .map(
      (t) => `<div class="target" data-dir="${esc(t.dir)}">
        <span><b>${esc(t.label)}</b><small>${esc(t.hint)}</small></span>
        <button class="btn small primary" data-install>Install</button>
      </div>`
    )
    .join('');
  const none = launcher === 'modrinth' ? 'No Modrinth instances found yet. Create one, then reopen this guide.' : launcher === 'dawn' ? 'No Dawn profiles found yet. Create one, then reopen this guide.' : 'We could not find a default Minecraft folder.';
  return `<div class="targets">${rows || `<div class="target"><span><small>${none}</small></span></div>`}
    <div class="target"><span><b>Somewhere else</b><small>Pick a mods folder yourself</small></span><button class="btn small" data-pick-folder>Choose folder</button></div>
  </div>`;
}

function bindTargets(root) {
  const done = (row, res) => {
    if (res.ok) {
      row.classList.add('done');
      row.querySelector('button').outerHTML = `<span class="chip good">${res.replaced ? 'Updated' : 'Installed'}</span>`;
      toast('Mod installed. Launch Fabric 1.21.11 and press Connect.', 'good');
    } else toast(res.error, 'bad');
  };
  $$('[data-install]', root).forEach((btn) => {
    btn.onclick = async () => done(btn.closest('.target'), await api.installer.install(btn.closest('.target').dataset.dir));
  });
  $$('[data-pick-folder]', root).forEach((btn) => {
    btn.onclick = async () => {
      const dir = await api.installer.pick();
      if (dir) done(btn.closest('.target'), await api.installer.install(dir));
    };
  });
}

/* ------------------------------------------------------------------ shell */

const PAGES = [
  { id: 'train', label: 'Train', icon: 'train' },
  { id: 'tree', label: 'Skill tree', icon: 'tree' },
  { id: 'progress', label: 'Progress', icon: 'chart' },
  { id: 'setup', label: 'Setup', icon: 'gear' },
];

function showShell() {
  setAccent();
  const el = view(
    '',
    `<div class="shell">
       <nav class="sidebar">
         <div class="sidebar-nav">
           <span class="nav-pill"></span>
           ${PAGES.map((p) => `<button class="nav-item" data-page="${p.id}"><span data-icon="${p.icon}"></span>${p.label}</button>`).join('')}
         </div>
         <div class="sidebar-foot"><div class="row"><span class="status-dot" id="dot"></span><span id="dot-text">Not connected</span></div></div>
       </nav>
       <div class="content" id="content"></div>
     </div>`
  );
  swapView(el);
  $$('.nav-item', el).forEach((btn) => (btn.onclick = () => goPage(btn.dataset.page)));
  goPage(state.page, true);
  renderDot();
}

function goPage(id, force = false) {
  if (!force && state.page === id && !state.modeId) return;
  state.page = id;
  if (id !== 'train') state.modeId = null;
  const idx = PAGES.findIndex((p) => p.id === id);
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.page === id));
  const pill = $('.nav-pill');
  if (pill) pill.style.transform = `translateY(${idx * 46}px)`;
  renderPage();
}

function renderPage() {
  const content = $('#content');
  if (!content) return;
  const el = document.createElement('div');
  el.className = 'page scroll';
  const builders = { train: state.modeId ? pageMode : pageTrain, tree: pageTree, progress: pageProgress, setup: pageSetup };
  builders[state.page](el);
  const old = $('.page.in', content);
  content.append(el);
  hydrateIcons(el);
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('in')));
  if (old) {
    old.classList.remove('in');
    setTimeout(() => old.remove(), 380);
  }
  const mode = state.modeId ? modeOf(state.modeId) : null;
  if (mode) setAccent(mode.color, mode.color);
  else setAccent();
}

function renderDot() {
  const dot = $('#dot');
  if (!dot) return;
  const s = state.conn.state;
  dot.className = `status-dot ${s === 'ready' || s === 'live' ? 'on' : s === 'idle' || s === 'error' ? '' : 'busy'}`;
  $('#dot-text').textContent =
    { idle: 'Not connected', searching: 'Looking for Minecraft', connecting: 'Connecting', entering: 'Loading practice world', ready: `In game as ${state.game.player || 'you'}`, live: 'Session running', error: 'Not connected' }[s] || '';
}

/* ------------------------------------------------------------- train page */

const HERO_COPY = {
  idle: ['Connect to Minecraft', 'Start Minecraft 1.21.11 with the PVPTraining mod, then press Connect. We find your game and take you straight into the practice world.'],
  searching: ['Looking for your game…', 'Waiting for a Minecraft client with the PVPTraining mod. Launch it now if you have not yet, and stay on the title screen.'],
  connecting: ['Found it. Connecting…', 'Shaking hands with your game.'],
  entering: ['Loading the practice world…', 'Your game is opening the PVPTraining world. First load builds the arenas, so give it a few seconds.'],
  ready: ['You are in. Pick what to train.', 'Choose a gamemode below, pick a drill or a duel, and it starts in your game right away.'],
  live: ['Session running', 'Eyes on the game. Your numbers update here live.'],
  error: ['Could not connect', ''],
};

function heroHtml() {
  const s = state.conn.state;
  let [title, body] = HERO_COPY[s];
  if ((s === 'live' && state.game.paused) || (s === 'ready' && state.pendingStart)) [title, body] = ['Ready. Switch back to Minecraft', 'The game pauses while this window is in front. Your session starts the moment you click back into Minecraft.'];
  const inst = state.conn.instance;
  const chips = [];
  if (inst) chips.push(`<span class="chip good">${esc(inst.username || 'Player')}</span>`, `<span class="chip">Minecraft ${esc(inst.mcVersion || '1.21.11')}</span>`);
  if (inst?.launcher) chips.push(`<span class="chip">${esc(inst.launcher)}</span>`);
  let action = `<button class="btn primary big" id="connect"><span data-icon="plug"></span>Connect</button>`;
  if (s === 'searching' || s === 'connecting' || s === 'entering') action = `<button class="btn" id="cancel">Cancel</button>`;
  if (s === 'ready') action = `<button class="btn ghost" id="disconnect">Disconnect</button>`;
  if (s === 'live') action = `<button class="btn danger" id="stop"><span data-icon="stop"></span>End session</button>`;
  if (s === 'error') action = `<button class="btn primary big" id="connect"><span data-icon="plug"></span>Try again</button>`;
  return `<div class="card hero rise" id="hero">
      <div class="orb" data-state="${s}"><span class="ring"></span><span class="ring r2"></span><span class="core"><span data-icon="${s === 'ready' || s === 'live' ? 'check' : 'plug'}"></span></span></div>
      <div><h3>${esc(title)}</h3><p>${esc(s === 'error' ? state.conn.error : body)}</p><div class="hero-meta">${chips.join('')}</div></div>
      <div>${action}</div>
    </div>`;
}

function bindHero(root) {
  const on = (id, fn) => {
    const b = $(`#${id}`, root);
    if (b) b.onclick = fn;
  };
  on('connect', connect);
  on('cancel', disconnect);
  on('disconnect', disconnect);
  on('stop', () => api.bridge.send({ t: 'stop' }));
}

function refreshHero() {
  const hero = $('#hero');
  if (!hero) return;
  const tmp = document.createElement('div');
  tmp.innerHTML = heroHtml();
  const next = tmp.firstElementChild;
  next.classList.remove('rise');
  hero.replaceWith(next);
  hydrateIcons(next);
  bindHero(next.parentElement);
}

function tileHtml(mode, i) {
  const locked = isLocked(mode);
  const parent = mode.parent ? modeOf(mode.parent) : null;
  const foot = locked
    ? `<span class="lock-note"><span data-icon="lock"></span>Master ${esc(parent.name)} to unlock</span><span>${masteryOf(parent.id)}% / ${state.catalog.mastery.unlockAt}%</span>`
    : `<span class="mastery"><span class="bar"><i data-w="${masteryOf(mode.id)}"></i></span><b>${masteryOf(mode.id)}%</b></span><span>${mode.drills.length} drills · duels</span>`;
  return `<button class="tile rise ${locked ? 'locked' : ''}" style="--i:${i + 2}; --c:${mode.color}" data-mode="${mode.id}">
      <div class="tile-top"><span class="glyph">${icon(mode.id)}</span><span><span class="kind">${mode.kind === 'branch' ? `${esc(parent.name)} branch` : 'Gamemode'}</span><h4>${esc(mode.name)}</h4></span></div>
      <p>${esc(mode.tagline)}</p>
      <div class="tile-foot">${foot}</div>
    </button>`;
}

function pageTrain(el) {
  const modes = state.catalog.modes;
  el.innerHTML = `
    <div class="page-head"><div><h2>Train</h2><p>Connect once, then everything starts from here.</p></div></div>
    ${heroHtml()}
    ${state.game.session ? liveHtml() : ''}
    <div class="section-title">Gamemodes</div>
    <div class="tiles">${modes.filter((m) => m.kind === 'gamemode').map(tileHtml).join('')}</div>
    <div class="section-title">Skill branches</div>
    <div class="tiles">${modes.filter((m) => m.kind === 'branch').map((m, i) => tileHtml(m, i + 2)).join('')}</div>`;
  bindHero(el);
  $$('[data-mode]', el).forEach((btn) => {
    btn.onclick = () => {
      const mode = modeOf(btn.dataset.mode);
      if (isLocked(mode)) {
        toast(`${mode.name} unlocks when you reach ${state.catalog.mastery.unlockAt}% mastery in ${modeOf(mode.parent).name} and beat the ${tierName(state.catalog.mastery.requiredTier)} bot.`);
        return;
      }
      state.modeId = mode.id;
      state.pick = { type: 'drill', id: mode.drills[0].id };
      renderPage();
    };
  });
  animateBars(el);
}

function animateBars(root) {
  requestAnimationFrame(() => setTimeout(() => $$('.bar > i', root).forEach((b) => (b.style.width = `${b.dataset.w}%`)), 260));
}

const tierName = (id) => state.catalog.tiers.find((t) => t.id === id)?.name || id;

const KITS = {
  mace: ['Netherite Prot IV', 'Density V + Wind Burst mace', 'Breach IV mace', 'Sword + axe', 'Shield', 'Wind charges', 'Elytra, no rockets', 'Pearls', 'Golden apples', 'Totem'],
  crystal: ['Netherite Prot IV / Blast Prot legs', 'Sword', 'Pickaxe', 'End crystals', 'Obsidian', 'Respawn anchors', 'Glowstone', 'Totems', 'Pearls', 'Golden apples', 'XP bottles'],
  spear: ['Netherite Prot IV', 'Netherite spear, Lunge III', 'Density V + Wind Burst mace', 'Breach IV mace', 'Sword + axe', 'Shield', 'Wind charges', 'Pearls', 'Golden apples', 'Totem', 'No elytra'],
  elytra_mace: ['Netherite Prot IV', 'Elytra', 'Firework rockets', 'Density V + Wind Burst mace', 'Breach IV mace', 'Sword + axe', 'Shield', 'Wind charges', 'Pearls', 'Golden apples', 'Totem'],
};

function pageMode(el) {
  const mode = modeOf(state.modeId);
  const prog = progOf(mode.id);
  el.style.setProperty('--c', mode.color);
  const drillRow = (d) => {
    const p = prog.drills?.[d.id];
    const sel = state.pick?.type === 'drill' && state.pick.id === d.id;
    return `<button class="row-item ${sel ? 'selected' : ''}" data-drill="${d.id}">
        <span><h5>${esc(d.name)}</h5><small>${esc(d.goal)}</small></span>
        <span class="right">${medalDots(p?.medal || 0)}<span>${p ? `Best ${fmtScore(p.best, d.unit)}` : 'No run yet'}</span></span>
      </button>`;
  };
  const tier = (t) => {
    const p = prog.duels?.[t.id];
    const sel = state.pick?.type === 'duel' && state.pick.id === t.id;
    return `<button class="tier ${sel ? 'selected' : ''} ${p?.matchWins ? 'beaten' : ''}" data-tier="${t.id}" title="${esc(t.blurb)}"><b>${esc(t.name)}</b><span>${p ? `${p.matchWins || 0}W ${p.matchLosses || 0}L` : 'Unplayed'}</span></button>`;
  };
  el.innerHTML = `
    <button class="btn ghost small" id="back" style="margin:-6px 0 12px -12px"><span data-icon="back"></span>All gamemodes</button>
    <div class="detail-head rise"><span class="glyph">${icon(mode.id)}</span><div><h2>${esc(mode.name)}</h2><p>${esc(mode.summary)}</p></div></div>
    <div class="detail-grid">
      <div>
        <div class="section-title" style="margin-top:0">Drills</div>
        <div class="list rise" style="--i:1">${mode.drills.map(drillRow).join('')}</div>
        <div class="section-title">Duel a bot · first to 3</div>
        <div class="tier-row rise" style="--i:2">${state.catalog.tiers.map(tier).join('')}</div>
      </div>
      <div class="card launch-panel rise" style="--i:3" id="launch"></div>
    </div>`;
  $('#back', el).onclick = () => {
    state.modeId = null;
    renderPage();
  };
  $$('[data-drill]', el).forEach((b) => (b.onclick = () => choose({ type: 'drill', id: b.dataset.drill })));
  $$('[data-tier]', el).forEach((b) => (b.onclick = () => choose({ type: 'duel', id: b.dataset.tier })));
  renderLaunch(el);
}

function choose(pick) {
  state.pick = pick;
  $$('[data-drill]').forEach((b) => b.classList.toggle('selected', pick.type === 'drill' && b.dataset.drill === pick.id));
  $$('[data-tier]').forEach((b) => b.classList.toggle('selected', pick.type === 'duel' && b.dataset.tier === pick.id));
  renderLaunch(document);
}

function renderLaunch(root) {
  const panel = $('#launch', root);
  if (!panel) return;
  const mode = modeOf(state.modeId);
  const pick = state.pick;
  let title = '';
  let what = '';
  if (pick.type === 'drill') {
    const d = mode.drills.find((x) => x.id === pick.id);
    title = d.name;
    const [b, s, g] = d.medals;
    what = `${d.goal}<br/><br/>Bronze ${b} · Silver ${s} · Gold ${g} ${d.unit}${d.lowerIsBetter ? ' or faster' : ''}`;
  } else {
    const t = state.catalog.tiers.find((x) => x.id === pick.id);
    title = `Duel: ${t.name} bot`;
    what = `${t.blurb}<br/><br/>Same kit on both sides, first to 3 rounds. Winning counts toward ${mode.name} mastery.`;
  }
  const s = state.conn.state;
  const canStart = s === 'ready';
  const label = s === 'live' ? 'Session already running' : canStart ? 'Start in game' : 'Connect to start';
  panel.innerHTML = `<h4>${esc(title)}</h4><div class="what">${what}</div>
    <div class="kit">${(KITS[mode.id] || []).map((k) => `<span>${esc(k)}</span>`).join('')}</div>
    <button class="btn primary big" id="start" ${s === 'live' ? 'disabled' : ''}><span data-icon="${canStart ? 'play' : 'plug'}"></span>${label}</button>`;
  hydrateIcons(panel);
  $('#start', panel).onclick = async () => {
    if (state.conn.state !== 'ready') {
      state.modeId = null;
      renderPage();
      connect();
      return;
    }
    api.bridge.send({ t: 'start', mode: mode.id, activity: pick.type, id: pick.id });
    state.pendingStart = true;
    setTimeout(() => {
      state.pendingStart = false;
    }, 60000);
    state.modeId = null;
    state.feed = [];
    renderPage();
  };
}

/* ------------------------------------------------------------ live panel */

function liveHtml() {
  const ses = state.game.session;
  const mode = modeOf(ses.mode);
  const stats = ses.stats || [];
  return `<div id="live" style="--c:${mode?.color || 'var(--accent)'}">
      <div class="section-title">${esc(mode?.name || '')} · ${esc(ses.name || '')}</div>
      <div class="live">${stats.map((st) => `<div class="stat"><label>${esc(st.label)}</label><b data-stat="${esc(st.label)}">${esc(st.value)}</b></div>`).join('')}</div>
      <div class="card feed scroll" id="feed">${feedHtml()}</div>
    </div>`;
}

function feedHtml() {
  if (!state.feed.length) return `<div class="feed-line"><time>--:--</time>Waiting for the first hit…</div>`;
  return state.feed.slice(-40).reverse().map((f) => `<div class="feed-line ${f.tone || ''}"><time>${f.time}</time>${esc(f.text)}</div>`).join('');
}

function updateLive() {
  if (state.page !== 'train' || state.modeId) return;
  const live = $('#live');
  const ses = state.game.session;
  if (!ses) {
    if (live) renderPage();
    return;
  }
  if (!live) {
    renderPage();
    return;
  }
  const cells = $$('[data-stat]', live);
  const stats = ses.stats || [];
  if (cells.length !== stats.length) {
    renderPage();
    return;
  }
  stats.forEach((st, i) => {
    const cell = cells[i];
    if (cell.textContent !== String(st.value)) {
      cell.textContent = st.value;
      cell.classList.remove('pop');
      void cell.offsetWidth;
      cell.classList.add('pop');
    }
  });
}

/* -------------------------------------------------------------- tree page */

function pageTree(el) {
  const nodes = [
    { id: 'mace', x: 30, y: 26 },
    { id: 'spear', x: 14, y: 72 },
    { id: 'elytra_mace', x: 46, y: 72 },
    { id: 'crystal', x: 78, y: 26 },
    { soon: true, x: 78, y: 72, label: 'Crystal branches', sub: 'Coming later' },
  ];
  const links = [
    { from: 'mace', to: 'spear', a: nodes[0], b: nodes[1] },
    { from: 'mace', to: 'elytra_mace', a: nodes[0], b: nodes[2] },
    { soon: true, a: nodes[3], b: nodes[4] },
  ];
  const R = 2 * Math.PI * 52;
  const nodeHtml = (n) => {
    if (n.soon) return `<div class="node soon" style="left:${n.x}%;top:${n.y}%"><div class="bubble"><span class="g" style="width:34px;height:34px">${icon('soon')}</span></div><b>${n.label}</b><span>${n.sub}</span></div>`;
    const mode = modeOf(n.id);
    const locked = isLocked(mode);
    const pct = masteryOf(n.id);
    const sub = locked ? `Locked · master ${modeOf(mode.parent).name}` : pct >= state.catalog.mastery.unlockAt ? `Mastered · ${pct}%` : `${pct}% mastery`;
    return `<button class="node ${locked ? 'locked' : ''}" style="left:${n.x}%;top:${n.y}%;--c:${mode.color}" data-node="${n.id}">
        <div class="bubble">
          <svg class="ringp" viewBox="0 0 110 110"><circle class="track" cx="55" cy="55" r="52"/><circle class="val" cx="55" cy="55" r="52" stroke-dasharray="${R}" stroke-dashoffset="${R}" data-to="${R * (1 - (locked ? 0 : pct) / 100)}"/></svg>
          <span class="g" style="width:40px;height:40px">${icon(n.id)}</span>
          ${locked ? `<span class="badge">${icon('lock')}</span>` : ''}
        </div><b>${esc(mode.name)}</b><span>${esc(sub)}</span></button>`;
  };
  el.innerHTML = `
    <div class="page-head"><div><h2>Skill tree</h2><p>Master a gamemode to unlock its branches. Reach ${state.catalog.mastery.unlockAt}% mastery and beat the ${tierName(state.catalog.mastery.requiredTier)} bot.</p></div></div>
    <div class="tree-wrap rise">
      <svg class="links" viewBox="0 0 100 100" preserveAspectRatio="none">
        ${links.map((l) => {
          const open = !l.soon && !isLocked(modeOf(l.to));
          const c = l.soon ? '' : modeOf(l.to).color;
          const my = (l.a.y + 22 + l.b.y - 11) / 2;
          return `<path class="link ${open ? 'open' : ''}" style="--c:${c}" vector-effect="non-scaling-stroke" d="M ${l.a.x} ${l.a.y + 22} C ${l.a.x} ${my}, ${l.b.x} ${my}, ${l.b.x} ${l.b.y - 11}"/>`;
        }).join('')}
      </svg>
      ${nodes.map(nodeHtml).join('')}
    </div>
    <div class="tree-legend"><span>Ring = mastery</span><span>Solid line = unlocked</span><span>Dashed = still locked</span></div>`;
  $$('[data-node]', el).forEach((b) => {
    b.onclick = () => {
      const mode = modeOf(b.dataset.node);
      if (isLocked(mode)) return toast(`Keep training ${modeOf(mode.parent).name}: ${masteryOf(mode.parent)}% of ${state.catalog.mastery.unlockAt}% so far.`);
      state.page = 'train';
      state.modeId = mode.id;
      state.pick = { type: 'drill', id: mode.drills[0].id };
      goPage('train', true);
    };
  });
  requestAnimationFrame(() => setTimeout(() => $$('.ringp .val', el).forEach((c) => (c.style.strokeDashoffset = c.dataset.to)), 320));
}

/* ---------------------------------------------------------- progress page */

function pageProgress(el) {
  const R = 2 * Math.PI * 26;
  const cards = state.catalog.modes
    .map((mode, i) => {
      const prog = progOf(mode.id);
      const locked = isLocked(mode);
      const pct = locked ? 0 : masteryOf(mode.id);
      const beaten = state.catalog.tiers.filter((t) => prog.duels?.[t.id]?.matchWins).pop();
      return `<div class="card prog-card rise" style="--i:${i};--c:${mode.color}">
          <div class="top">
            <div class="ring"><svg viewBox="0 0 62 62"><circle class="track" cx="31" cy="31" r="26"/><circle class="val" cx="31" cy="31" r="26" stroke-dasharray="${R}" stroke-dashoffset="${R}" data-to="${R * (1 - pct / 100)}"/></svg><b>${pct}%</b></div>
            <div><h4>${esc(mode.name)}</h4><div class="sub">${locked ? 'Locked' : beaten ? `Best bot beaten: ${beaten.name}` : 'No duel wins yet'}</div></div>
          </div>
          <div class="prog-lines">${mode.drills
            .map((d) => `<div class="prog-line"><span>${esc(d.name)}</span><span class="v">${fmtScore(prog.drills?.[d.id]?.best, d.unit)}${medalDots(prog.drills?.[d.id]?.medal || 0)}</span></div>`)
            .join('')}</div>
        </div>`;
    })
    .join('');
  const hist = (state.progress?.history || []).slice(-12).reverse();
  el.innerHTML = `
    <div class="page-head"><div><h2>Progress</h2><p>Mastery is half drill medals, half the toughest bot you have beaten.</p></div></div>
    <div class="prog-grid">${cards}</div>
    <div class="section-title">Recent sessions</div>
    <div class="card history">${
      hist.length
        ? hist.map((h) => `<div class="h-row"><span>${new Date(h.ts).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</span><span><b>${esc(modeOf(h.mode)?.name || h.mode)}</b> · ${esc(h.name)}</span><span>${esc(h.result)}</span></div>`).join('')
        : `<div class="empty">Nothing here yet. Your runs show up as soon as you finish one.</div>`
    }</div>`;
  requestAnimationFrame(() => setTimeout(() => $$('.ring .val', el).forEach((c) => (c.style.strokeDashoffset = c.dataset.to)), 320));
}

/* ------------------------------------------------------------- setup page */

function pageSetup(el) {
  const b = BRANDS[state.settings.launcher] || BRANDS.other;
  el.innerHTML = `
    <div class="page-head"><div><h2>Setup</h2><p>Launcher, mod file and version.</p></div></div>
    <div class="setup-grid">
      <div class="card setup-card rise"><h4>Launcher: ${esc(b.name)}</h4><p>Reopen the install guide, or switch to a different launcher.</p>
        <div class="actions"><button class="btn small" id="guide">Open install guide</button><button class="btn small ghost" id="switch">Change launcher</button></div></div>
      <div class="card setup-card rise" style="--i:1"><h4>Mod file</h4><p><code class="path">${esc(state.info.jar || 'not bundled in this build')}</code></p>
        <div class="actions"><button class="btn small" id="save"><span data-icon="download"></span>Save a copy</button><button class="btn small ghost" id="reveal">Show file</button></div></div>
      <div class="card setup-card rise" style="--i:2"><h4>Game version</h4><p>PVPTraining is built for one version right now. More will follow, and this becomes a picker when they do.</p>
        <div class="version-select" aria-disabled="true" style="max-width:320px"><span class="lk"><span data-icon="lock"></span>Fabric 1.21.11</span><em>only version for now</em></div></div>
      <div class="card setup-card rise" style="--i:3"><h4>About</h4><p>PVPTraining ${esc(state.info.version)}. The app talks to your game over a local connection that never leaves this computer.</p>
        <div class="actions"><button class="btn small ghost" id="site">Website</button></div></div>
    </div>`;
  $('#guide', el).onclick = () => showGuide(b.id);
  $('#switch', el).onclick = showPicker;
  $('#save', el).onclick = async () => {
    const res = await api.installer.saveAs();
    if (res.ok) toast('Saved the mod file.', 'good');
    else if (!res.canceled) toast(res.error, 'bad');
  };
  $('#reveal', el).onclick = () => api.installer.reveal();
  $('#site', el).onclick = () => api.open('https://attackstudios.github.io/pvptraining/');
}

/* ------------------------------------------------------------- connection */

const CONN_SFX = { searching: 'searching', connecting: 'found', ready: 'connected', live: 'start', error: 'error' };

function setConn(next, extra = {}) {
  if (next !== state.conn.state && CONN_SFX[next]) play(CONN_SFX[next]);
  state.conn = { ...state.conn, ...extra, state: next };
  renderDot();
  if (state.page === 'train' && !state.modeId) refreshHero();
  else renderLaunch(document);
}

async function connect() {
  clearInterval(state.pollTimer);
  setConn('searching', { error: null, instance: null });
  const attempt = async () => {
    if (state.conn.state !== 'searching') return;
    const found = await api.discover();
    if (!found.length || state.conn.state !== 'searching') return;
    clearInterval(state.pollTimer);
    const instance = found[0];
    setConn('connecting', { instance });
    const res = await api.bridge.connect(instance);
    if (!res.ok) setConn('error', { error: res.error, instance: null });
  };
  state.pollTimer = setInterval(attempt, 1200);
  attempt();
}

async function disconnect() {
  clearInterval(state.pollTimer);
  await api.bridge.close();
  state.game = { inWorld: false, player: null, session: null };
  setConn('idle', { instance: null, error: null });
  if (state.page === 'train' && !state.modeId) renderPage();
}

function applyGameState(msg) {
  const hadSession = Boolean(state.game.session);
  state.game.inWorld = Boolean(msg.practice);
  state.game.player = msg.player || state.game.player;
  state.game.session = msg.session || null;
  if (state.game.session) state.pendingStart = false;
  const wasPaused = state.game.paused;
  state.game.paused = Boolean(msg.paused);
  const busy = ['connecting', 'entering', 'ready', 'live'].includes(state.conn.state);
  if (!busy) return;
  if (state.game.session) {
    if (state.conn.state !== 'live') setConn('live');
  } else if (state.game.inWorld) {
    if (state.conn.state !== 'ready') setConn('ready');
  } else if (state.conn.state === 'ready' || state.conn.state === 'live') {
    setConn('entering');
    api.bridge.send({ t: 'enterWorld' });
  }
  if (wasPaused !== state.game.paused && state.conn.state === 'live') refreshHero();
  if (hadSession !== Boolean(state.game.session) && state.page === 'train' && !state.modeId) renderPage();
  else updateLive();
}

function onMessage(msg) {
  if (msg.t === 'welcome') {
    state.game.player = msg.player;
    setConn('entering');
    api.bridge.send({ t: 'enterWorld' });
    applyGameState(msg);
  } else if (msg.t === 'state') {
    applyGameState(msg);
  } else if (msg.t === 'event') {
    const d = new Date();
    play(msg.tone === 'good' ? 'hit' : msg.tone === 'bad' ? 'miss' : 'stat');
    state.feed.push({ text: msg.text, tone: msg.tone, time: `${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}` });
    const feed = $('#feed');
    if (feed) feed.innerHTML = feedHtml();
  } else if (msg.t === 'progress') {
    state.progress = msg.progress;
  } else if (msg.t === 'result') {
    if (msg.progress) state.progress = msg.progress;
    play(msg.medal > 0 || msg.won ? 'success' : 'finish');
    toast(msg.summary || 'Session finished.', msg.medal > 0 || msg.won ? 'good' : '', true);
    (msg.unlocked || []).forEach((id, i) => setTimeout(() => {
      if (i === 0) play('unlock');
      toast(`Skill branch unlocked: ${modeOf(id)?.name || id}`, 'good', true);
    }, 900 + i * 700));
    if (msg.unlocked?.length) setTimeout(() => goPage('tree', true), 1200);
    else if (state.page !== 'train' || !state.modeId) renderPage();
  } else if (msg.t === 'error') {
    toast(msg.message, 'bad');
  }
}

function onStatus(status) {
  if (status.state === 'disconnected' && state.conn.state !== 'idle' && state.conn.state !== 'error') {
    state.game = { inWorld: false, player: null, session: null };
    if (status.byUser) setConn('idle', { instance: null });
    else setConn('error', { error: 'Minecraft closed or the connection dropped.', instance: null });
    if (state.page === 'train' && !state.modeId) renderPage();
  }
}

/* ------------------------------------------------------------------- boot */

function wireSoundToggle() {
  const btn = $('#sound');
  const paint = () => {
    btn.dataset.icon = isMuted() ? 'soundOff' : 'soundOn';
    btn.innerHTML = icon(btn.dataset.icon);
    btn.title = isMuted() ? 'Sound is off' : 'Sound is on';
    btn.classList.toggle('off', isMuted());
  };
  paint();
  btn.onclick = async () => {
    setMuted(!isMuted());
    state.settings = await api.settings.set({ muted: isMuted() });
    paint();
    play('toggleOn');
  };
}

function wireUpdates() {
  const pill = $('#update');
  const paint = (st) => {
    if (!st) return;
    pill.classList.toggle('ready', st.state === 'ready' || st.state === 'manual');
    pill.onclick = null;
    if (st.state === 'downloading') {
      pill.hidden = false;
      pill.textContent = `${st.what === 'mod' ? 'Updating mod' : `Downloading ${st.version}`} ${st.percent}%`;
    } else if (st.state === 'ready') {
      pill.hidden = false;
      pill.textContent = `Restart to update to ${st.version}`;
      pill.title = 'The update also installs by itself the next time you close the app.';
      pill.onclick = () => api.update.apply();
    } else if (st.state === 'manual') {
      pill.hidden = false;
      pill.textContent = `${st.version} is out`;
      pill.title = st.reason || '';
      pill.onclick = () => api.open(st.url);
    } else if (st.state === 'mod-updated') {
      pill.hidden = true;
      toast(st.refreshed ? `Mod updated to ${st.version} in ${st.refreshed} folder${st.refreshed > 1 ? 's' : ''}. Restart Minecraft to use it.` : `Newer mod ${st.version} downloaded. Reinstall it from Setup.`, 'good');
      api.info().then((info) => (state.info = info));
    } else if (st.state === 'error') {
      pill.hidden = true;
      toast(`Update failed: ${st.message}`, 'bad');
    } else pill.hidden = true;
  };
  api.update.onStatus(paint);
  api.update.get().then(paint);
}

async function boot() {
  [state.info, state.settings, state.catalog, state.progress] = await Promise.all([api.info(), api.settings.get(), api.catalog(), api.progress()]);
  if (state.info.platform === 'win32') document.body.classList.add('win');
  hydrateIcons();
  setMuted(state.settings.muted);
  wireGlobalSounds();
  wireSoundToggle();
  wireUpdates();
  api.bridge.onMessage(onMessage);
  api.bridge.onStatus(onStatus);
  window.__pvpt = { state, showWelcome, showPicker, showGuide, showShell, goPage, renderPage, setConn, onMessage };
  if (state.info.shots) return;
  if (state.settings.onboarded) showShell();
  else showWelcome();
}

boot();
