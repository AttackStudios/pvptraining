const s = (body, extra = '') =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" ${extra}>${body}</svg>`;

export const ICONS = {
  // crossed mace + crystal shard crest
  logo: `<svg viewBox="0 0 48 48" fill="none"><path d="M24 3 42 10v13c0 11-7.4 18.6-18 22C13.4 41.600 6 34 6 23V10L24 3Z" fill="currentColor" opacity="0.16"/><path d="M24 3 42 10v13c0 11-7.4 18.6-18 22C13.4 41.6 6 34 6 23V10L24 3Z" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round"/><path d="m16 33 12.500-12.500" stroke="currentColor" stroke-width="3" stroke-linecap="round"/><path d="m27 12 9 9-5.200 1.800-1.600 5.200-9-9 1.800-5.200L27 12Z" fill="currentColor"/></svg>`,
  lock: s('<rect x="5" y="11" width="14" height="9" rx="2.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>'),
  arrow: s('<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>'),
  back: s('<path d="M19 12H5"/><path d="m11 6-6 6 6 6"/>'),
  check: s('<path d="m5 12.500 4.500 4.500L19 7.500"/>'),
  plug: s('<path d="M9 3v5M15 3v5"/><path d="M6.500 8h11v4a5.500 5.500 0 0 1-11 0V8Z"/><path d="M12 17.500V21"/>'),
  train: s('<circle cx="12" cy="12" r="8.500"/><circle cx="12" cy="12" r="4.500"/><circle cx="12" cy="12" r="0.900" fill="currentColor"/>'),
  tree: s('<circle cx="12" cy="5" r="2.500"/><circle cx="5.500" cy="19" r="2.500"/><circle cx="18.500" cy="19" r="2.500"/><path d="M12 7.500v4M12 11.500c-5 0-6.500 2-6.500 5M12 11.500c5 0 6.500 2 6.500 5"/>'),
  chart: s('<path d="M4 20V10M10 20V4M16 20v-8M21 20H3"/>'),
  gear: s('<circle cx="12" cy="12" r="3"/><path d="M12 2.500v3M12 18.500v3M2.500 12h3M18.500 12h3M5.300 5.300l2.100 2.100M16.600 16.600l2.100 2.100M18.700 5.300l-2.100 2.100M7.400 16.600l-2.100 2.100"/>'),
  download: s('<path d="M12 4v11"/><path d="m7 10.500 5 5 5-5"/><path d="M5 20h14"/>'),
  folder: s('<path d="M3.500 7.500a2 2 0 0 1 2-2h4l2 2.500h7a2 2 0 0 1 2 2v7.500a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-10Z"/>'),
  jar: s('<path d="M7 3.500h10v3H7zM6 9.500c0-1.500 1-3 1-3h10s1 1.500 1 3v9a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-9Z"/><path d="M9.500 13h5"/>'),
  stop: s('<rect x="6.500" y="6.500" width="11" height="11" rx="2.500"/>'),
  play: s('<path d="M8 5.500v13l10.500-6.500L8 5.500Z" fill="currentColor"/>'),
  bolt: s('<path d="M13 3 5.500 13.500H12L11 21l7.500-10.500H12L13 3Z"/>'),
  soundOn: s('<path d="M4 9.5v5h3.5L12 18.5v-13L7.5 9.5H4Z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>'),
  soundOff: s('<path d="M4 9.5v5h3.5L12 18.5v-13L7.5 9.5H4Z"/><path d="m16 9.5 5 5M21 9.5l-5 5"/>'),
  // game modes
  mace: `<svg viewBox="0 0 32 32" fill="none"><path d="M6 26 17 15" stroke="currentColor" stroke-width="3" stroke-linecap="round"/><path d="m5 27 2.500-2.500" stroke="currentColor" stroke-width="4.500" stroke-linecap="round" opacity="0.55"/><rect x="14.500" y="4.500" width="13" height="13" rx="3.200" fill="currentColor" opacity="0.22" stroke="currentColor" stroke-width="2.200"/><path d="M21 2.500v3.500M21 16v3.500M12.500 11H16M26 11h3.500" stroke="currentColor" stroke-width="2.400" stroke-linecap="round"/></svg>`,
  crystal: `<svg viewBox="0 0 32 32" fill="none"><path d="M16 3 25 12l-9 17-9-17 9-9Z" fill="currentColor" opacity="0.2"/><path d="M16 3 25 12l-9 17-9-17 9-9Z" stroke="currentColor" stroke-width="2.200" stroke-linejoin="round"/><path d="M7 12h18M16 3l-3.500 9L16 29l3.500-17L16 3Z" stroke="currentColor" stroke-width="1.600" stroke-linejoin="round" opacity="0.8"/></svg>`,
  spear: `<svg viewBox="0 0 32 32" fill="none"><path d="M5 27 20 12" stroke="currentColor" stroke-width="2.600" stroke-linecap="round"/><path d="M28 4 25.500 13 19 6.500 28 4Z" fill="currentColor" opacity="0.25" stroke="currentColor" stroke-width="2.200" stroke-linejoin="round"/><path d="M9 17.500 14.500 23" stroke="currentColor" stroke-width="2.200" stroke-linecap="round" opacity="0.6"/></svg>`,
  elytra_mace: `<svg viewBox="0 0 32 32" fill="none"><path d="M16 7c-2.500-3-7-3.500-11-1.500C6 12 7 19 12.500 26c1-5 2-8 3.500-10.500" fill="currentColor" opacity="0.2"/><path d="M16 7c-2.500-3-7-3.500-11-1.500C6 12 7 19 12.500 26c1-5 2-8 3.500-10.500M16 7c2.500-3 7-3.500 11-1.500C26 12 25 19 19.500 26c-1-5-2-8-3.500-10.500M16 7v8.500" stroke="currentColor" stroke-width="2.100" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
  sword: `<svg viewBox="0 0 32 32" fill="none"><path d="M27 7 12.500 21.500l-2-0.500-0.500-2L24.500 4.500 28 4l-1 3Z" fill="currentColor" opacity="0.22" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="m7.500 16.500 8 8" stroke="currentColor" stroke-width="2.800" stroke-linecap="round"/><path d="M10 23 5 28" stroke="currentColor" stroke-width="3.200" stroke-linecap="round"/></svg>`,
  // a minecart carrying a block of TNT
  cart: `<svg viewBox="0 0 32 32" fill="none"><rect x="9" y="5" width="14" height="11" rx="1.500" fill="currentColor" opacity="0.22" stroke="currentColor" stroke-width="2"/><path d="M9 9h14M9 12.500h14" stroke="currentColor" stroke-width="1.600" opacity="0.85"/><path d="M16 5V2.500c0-1 1.500-1 2.500-1.500" stroke="currentColor" stroke-width="1.800" stroke-linecap="round"/><path d="M4 16h24l-2.500 8.500h-19L4 16Z" fill="currentColor" opacity="0.12" stroke="currentColor" stroke-width="2.200" stroke-linejoin="round"/><circle cx="10.500" cy="27" r="2.300" stroke="currentColor" stroke-width="2"/><circle cx="21.500" cy="27" r="2.300" stroke="currentColor" stroke-width="2"/></svg>`,
  xbow: `<svg viewBox="0 0 32 32" fill="none"><path d="M6 26 22 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/><path d="M9.500 6.500C17 5 27 15 25.500 22.500" fill="currentColor" fill-opacity="0.18" stroke="currentColor" stroke-width="2.400" stroke-linecap="round"/><path d="M9.500 6.500 25.500 22.500" stroke="currentColor" stroke-width="1.500" opacity="0.7"/><path d="M22 10l5.500-5.500M27.500 4.500h-4.500M27.500 4.500V9" stroke="currentColor" stroke-width="2.200" stroke-linecap="round" stroke-linejoin="round"/><path d="m4.500 23.500 4 4" stroke="currentColor" stroke-width="2.400" stroke-linecap="round"/></svg>`,
  soon: s('<circle cx="12" cy="12" r="8.500"/><path d="M12 7.500V12l3 2"/>'),
};

export function icon(name) {
  return ICONS[name] || '';
}

export function hydrateIcons(root = document) {
  root.querySelectorAll('[data-icon]').forEach((el) => {
    if (!el.firstChild) el.innerHTML = icon(el.dataset.icon);
  });
}
