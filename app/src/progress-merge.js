/**
 * Progress sync rules, shared by everything that touches progress on this side.
 *
 * Two copies of a player's progress (this device and the account) are combined by taking the
 * better side of every single thing: best score, medal, runs, duel record, unlock. Nothing is
 * ever overwritten, so signing in on a fresh device can only add to what the account has.
 * Mastery is then recomputed from the merged medals and duel wins with the same formula the
 * mod uses (Progress.recompute), because the merged total can be higher than either side's.
 */

function lowerSet(catalog) {
  const set = new Set();
  for (const m of catalog.modes) for (const d of m.drills) if (d.lowerIsBetter) set.add(d.id);
  return set;
}

function mergeModes(a = {}, b = {}, catalog) {
  const lower = lowerSet(catalog);
  const out = {};
  for (const id of new Set([...Object.keys(a), ...Object.keys(b)])) {
    const x = a[id] || {};
    const y = b[id] || {};
    const mode = { mastery: Math.max(Number(x.mastery) || 0, Number(y.mastery) || 0), unlocked: Boolean(x.unlocked || y.unlocked), drills: {}, duels: {} };
    for (const d of new Set([...Object.keys(x.drills || {}), ...Object.keys(y.drills || {})])) {
      const p = x.drills?.[d];
      const q = y.drills?.[d];
      const isLower = lower.has(d);
      const valid = (v) => v && typeof v.best === 'number' && (v.runs === undefined || v.runs > 0) && (!isLower || v.best > 0);
      let best = 0;
      if (valid(p) && valid(q)) best = isLower ? Math.min(p.best, q.best) : Math.max(p.best, q.best);
      else if (valid(p)) best = p.best;
      else if (valid(q)) best = q.best;
      mode.drills[d] = { best, medal: Math.max(Number(p?.medal) || 0, Number(q?.medal) || 0), runs: Math.max(Number(p?.runs) || 0, Number(q?.runs) || 0) };
    }
    for (const t of new Set([...Object.keys(x.duels || {}), ...Object.keys(y.duels || {})])) {
      const p = x.duels?.[t] || {};
      const q = y.duels?.[t] || {};
      mode.duels[t] = { matchWins: Math.max(Number(p.matchWins) || 0, Number(q.matchWins) || 0), matchLosses: Math.max(Number(p.matchLosses) || 0, Number(q.matchLosses) || 0) };
    }
    out[id] = mode;
  }
  return out;
}

function bestTierIndex(mode, catalog) {
  let best = -1;
  catalog.tiers.forEach((t, i) => {
    if ((mode.duels?.[t.id]?.matchWins || 0) > 0) best = i;
  });
  return best;
}

/** Same arithmetic as the mod's Progress.recompute(): half drill medals, half toughest bot beaten. */
function recompute(modes, catalog) {
  const rules = catalog.mastery;
  for (const def of catalog.modes) {
    const mode = (modes[def.id] ||= { mastery: 0, unlocked: false, drills: {}, duels: {} });
    let medals = 0;
    for (const d of def.drills) medals += (mode.drills?.[d.id]?.medal || 0) / 3;
    const drillPart = def.drills.length ? (medals / def.drills.length) * rules.drillWeight : 0;
    const duelPart = ((bestTierIndex(mode, catalog) + 1) / catalog.tiers.length) * rules.duelWeight;
    mode.mastery = Math.round((drillPart + duelPart) * 10) / 10;
    if (!def.parent) mode.unlocked = true;
  }
  const required = catalog.tiers.findIndex((t) => t.id === rules.requiredTier);
  for (const def of catalog.modes) {
    if (!def.parent || modes[def.id].unlocked) continue;
    const parent = modes[def.parent];
    if (parent && parent.mastery >= rules.unlockAt && bestTierIndex(parent, catalog) >= required) modes[def.id].unlocked = true;
  }
  return modes;
}

/** Drills flagged so the server (which has no catalog) knows which direction is better. */
function forUpload(modes, catalog) {
  const lower = lowerSet(catalog);
  const out = JSON.parse(JSON.stringify(modes || {}));
  for (const mode of Object.values(out)) for (const [id, d] of Object.entries(mode.drills || {})) if (lower.has(id)) d.lower = true;
  return out;
}

const total = (modes) => Math.round(Object.values(modes || {}).reduce((n, m) => n + (Number(m.mastery) || 0), 0) * 10) / 10;

module.exports = { mergeModes, recompute, forUpload, total };
