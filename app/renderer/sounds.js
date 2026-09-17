// Every sound in the app is synthesized here with Web Audio: no files to load,
// nothing to license, and each one can be tuned in a line.

let ctx = null;
let master = null;
let muted = false;
let lastHover = 0;

function audio() {
  if (!ctx) {
    ctx = new AudioContext();
    master = ctx.createGain();
    master.gain.value = 0.5;
    const comp = ctx.createDynamicsCompressor();
    master.connect(comp).connect(ctx.destination);
  }
  if (ctx.state === 'suspended') ctx.resume();
  return ctx;
}

function tone({ freq, to, type = 'sine', dur = 0.12, gain = 0.2, delay = 0, attack = 0.004, pan = 0 }) {
  const ac = audio();
  const t = ac.currentTime + delay;
  const osc = ac.createOscillator();
  const g = ac.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  if (to) osc.frequency.exponentialRampToValueAtTime(to, t + dur);
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(gain, t + attack);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  let node = osc.connect(g);
  if (pan) {
    const p = ac.createStereoPanner();
    p.pan.value = pan;
    node = node.connect(p);
  }
  node.connect(master);
  osc.start(t);
  osc.stop(t + dur + 0.02);
}

function noise({ dur = 0.2, gain = 0.12, from = 800, to = 4000, delay = 0, q = 0.8 }) {
  const ac = audio();
  const t = ac.currentTime + delay;
  const len = Math.floor(ac.sampleRate * dur);
  const buf = ac.createBuffer(1, len, ac.sampleRate);
  const data = buf.getChannelData(0);
  for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1;
  const src = ac.createBufferSource();
  src.buffer = buf;
  const filter = ac.createBiquadFilter();
  filter.type = 'bandpass';
  filter.Q.value = q;
  filter.frequency.setValueAtTime(from, t);
  filter.frequency.exponentialRampToValueAtTime(to, t + dur);
  const g = ac.createGain();
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(gain, t + dur * 0.25);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  src.connect(filter).connect(g).connect(master);
  src.start(t);
}

const SOUNDS = {
  hover: () => tone({ freq: 1500, to: 1900, dur: 0.035, gain: 0.035, type: 'sine' }),
  click: () => {
    tone({ freq: 520, to: 300, dur: 0.07, gain: 0.16, type: 'triangle' });
    tone({ freq: 1800, dur: 0.025, gain: 0.05 });
  },
  primary: () => {
    tone({ freq: 392, dur: 0.1, gain: 0.16, type: 'triangle' });
    tone({ freq: 587, dur: 0.16, gain: 0.14, type: 'triangle', delay: 0.05 });
    noise({ dur: 0.12, gain: 0.03, from: 3000, to: 7000 });
  },
  nav: () => noise({ dur: 0.16, gain: 0.06, from: 500, to: 3200, q: 0.6 }),
  view: () => {
    noise({ dur: 0.34, gain: 0.07, from: 300, to: 5200, q: 0.5 });
    tone({ freq: 220, to: 330, dur: 0.3, gain: 0.05 });
  },
  back: () => noise({ dur: 0.22, gain: 0.055, from: 3600, to: 400, q: 0.6 }),
  select: () => tone({ freq: 880, to: 1175, dur: 0.08, gain: 0.1, type: 'triangle' }),
  locked: () => {
    tone({ freq: 196, dur: 0.09, gain: 0.16, type: 'square' });
    tone({ freq: 185, dur: 0.13, gain: 0.14, type: 'square', delay: 0.09 });
  },
  searching: () => {
    tone({ freq: 660, dur: 0.09, gain: 0.07, pan: -0.4 });
    tone({ freq: 880, dur: 0.12, gain: 0.06, delay: 0.11, pan: 0.4 });
  },
  found: () => [523, 659, 784].forEach((f, i) => tone({ freq: f, dur: 0.14, gain: 0.12, type: 'triangle', delay: i * 0.07 })),
  connected: () => {
    [523, 659, 784, 1047].forEach((f, i) => tone({ freq: f, dur: 0.32, gain: 0.13, type: 'triangle', delay: i * 0.085 }));
    tone({ freq: 131, dur: 0.6, gain: 0.12, delay: 0.25 });
    noise({ dur: 0.5, gain: 0.04, from: 2000, to: 9000, delay: 0.25 });
  },
  start: () => {
    tone({ freq: 110, to: 55, dur: 0.4, gain: 0.3 });
    noise({ dur: 0.28, gain: 0.1, from: 200, to: 2600, q: 0.4 });
    tone({ freq: 440, to: 880, dur: 0.22, gain: 0.1, type: 'sawtooth', delay: 0.04 });
  },
  hit: () => {
    tone({ freq: 240, to: 90, dur: 0.12, gain: 0.2, type: 'triangle' });
    noise({ dur: 0.07, gain: 0.08, from: 1200, to: 500 });
  },
  miss: () => tone({ freq: 300, to: 160, dur: 0.18, gain: 0.1, type: 'sawtooth' }),
  stat: () => tone({ freq: 1320, dur: 0.05, gain: 0.05 }),
  success: () => [659, 784, 988, 1319].forEach((f, i) => tone({ freq: f, dur: 0.26, gain: 0.12, type: 'triangle', delay: i * 0.07 })),
  finish: () => [392, 523, 659].forEach((f, i) => tone({ freq: f, dur: 0.3, gain: 0.11, type: 'triangle', delay: i * 0.09 })),
  error: () => {
    tone({ freq: 220, to: 140, dur: 0.22, gain: 0.18, type: 'sawtooth' });
    tone({ freq: 233, to: 148, dur: 0.22, gain: 0.12, type: 'sawtooth', delay: 0.02 });
  },
  unlock: () => {
    [392, 523, 659, 784, 1047, 1319].forEach((f, i) => tone({ freq: f, dur: 0.5, gain: 0.12, type: 'triangle', delay: i * 0.09 }));
    [523, 659, 784].forEach((f) => tone({ freq: f, dur: 1.2, gain: 0.08, delay: 0.6 }));
    noise({ dur: 1.1, gain: 0.05, from: 1500, to: 11000, delay: 0.5, q: 0.3 });
    tone({ freq: 98, dur: 1.0, gain: 0.16, delay: 0.55 });
  },
  toast: () => tone({ freq: 988, to: 1319, dur: 0.12, gain: 0.07 }),
  toggleOn: () => tone({ freq: 660, to: 990, dur: 0.1, gain: 0.12, type: 'triangle' }),
};

export function play(name) {
  if (muted || !SOUNDS[name]) return;
  if (name === 'hover') {
    const now = performance.now();
    if (now - lastHover < 45) return;
    lastHover = now;
  }
  try {
    SOUNDS[name]();
  } catch {
    /* audio device unavailable: stay silent */
  }
}

export function setMuted(value) {
  muted = Boolean(value);
}

export function isMuted() {
  return muted;
}

/**
 * One delegated listener pair gives every interactive element a hover tick and a
 * click sound. Elements can opt into a specific sound with data-sfx="name".
 */
export function wireGlobalSounds() {
  const interactive = 'button, .jar-chip, [data-sfx]';
  let lastTarget = null;
  document.addEventListener('pointerover', (e) => {
    const el = e.target.closest?.(interactive);
    if (!el || el === lastTarget || el.disabled) return;
    lastTarget = el;
    play('hover');
  });
  document.addEventListener('pointerout', (e) => {
    if (e.target.closest?.(interactive) === lastTarget && !e.relatedTarget?.closest?.(interactive)) lastTarget = null;
  });
  document.addEventListener(
    'click',
    (e) => {
      const el = e.target.closest?.(interactive);
      if (!el || el.disabled) return;
      if (el.dataset.sfx) return play(el.dataset.sfx);
      if (el.classList.contains('locked')) return play('locked');
      if (el.classList.contains('primary') || el.classList.contains('launcher')) return play('primary');
      if (el.classList.contains('nav-item')) return play('nav');
      if (el.classList.contains('back-link') || el.id === 'back') return play('back');
      if (el.classList.contains('row-item') || el.classList.contains('tier') || el.classList.contains('tile') || el.classList.contains('node')) return play('select');
      play('click');
    },
    true
  );
}
