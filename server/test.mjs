// End-to-end API test: node server/test.mjs [baseUrl]
// Needs a server started with PVPT_ALLOW_UNVERIFIED=1 (names here are not real accounts).
const base = process.argv[2] || 'http://127.0.0.1:8790/pvpt';
let failures = 0;
const check = (label, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? `  (${extra})` : ''}`);
  if (!ok) failures++;
};
async function call(method, route, { token, body } = {}) {
  const res = await fetch(base + route, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, data: await res.json() };
}
async function signIn(name) {
  const begin = await call('POST', '/auth/begin', { body: { name } });
  const fin = await call('POST', '/auth/finish', { body: { nonce: begin.data.nonce } });
  return fin.data;
}
const suffix = Math.random().toString(36).slice(2, 7);
const a = await signIn(`Alice_${suffix}`);
const b = await signIn(`Bob_${suffix}`);
const c = await signIn(`Carl_${suffix}`);
check('sign in returns token + friend code', Boolean(a.token && a.me.code && b.token), `${a.me.code} ${b.me.code}`);
check('bad name rejected', (await call('POST', '/auth/begin', { body: { name: 'no spaces!' } })).status === 400);
check('no token -> 401', (await call('GET', '/me')).status === 401);

const stats = (mastery, best) => ({ stats: { modes: { mace: { mastery, unlocked: true, drills: { mace_smash: { best, medal: 2, runs: 3 } } }, elytra_mace: { mastery: 5, drills: { ely_climb: { best: 12 - mastery / 10, medal: 1, runs: 2 } } } } }, evil: '<script>' });
check('stats upload', (await call('POST', '/stats', { token: a.token, body: stats(80, 250) })).data.ok);
await call('POST', '/stats', { token: b.token, body: stats(40, 180) });
await call('POST', '/stats', { token: c.token, body: stats(60, 400) });

check('message before friendship is refused', (await call('POST', '/messages', { token: a.token, body: { id: b.me.id, text: 'hi' } })).status === 403);
check('friend request by code', (await call('POST', '/friends/request', { token: a.token, body: { query: b.me.code } })).data.status === 'requested');
check('cannot friend yourself', (await call('POST', '/friends/request', { token: a.token, body: { query: a.me.code } })).status === 400);
const bMe = await call('GET', '/me', { token: b.token });
check('request shows up as incoming', bMe.data.incoming.length === 1 && bMe.data.incoming[0].name === a.me.name);
check('accept', (await call('POST', '/friends/accept', { token: b.token, body: { id: a.me.id } })).data.status === 'friends');
check('request by exact name + mutual request auto-accepts', (await call('POST', '/friends/request', { token: c.token, body: { query: a.me.name } })).data.status === 'requested' && (await call('POST', '/friends/request', { token: a.token, body: { query: c.me.name } })).data.status === 'friends');

check('send message', (await call('POST', '/messages', { token: a.token, body: { id: b.me.id, text: 'gg  shit  that was close' } })).data.message.text.includes('****'));
await new Promise((r) => setTimeout(r, 750));
check('rate limit lets a second message through after a moment', (await call('POST', '/messages', { token: a.token, body: { id: b.me.id, text: 'rematch?' } })).status === 200);
check('instant third message is rate limited', (await call('POST', '/messages', { token: a.token, body: { id: b.me.id, text: 'spam' } })).status === 429);
check('friend sees unread count', (await call('GET', '/me', { token: b.token })).data.friends[0].unread === 2);
const thread = await call('GET', `/messages?id=${a.me.id}`, { token: b.token });
check('thread delivers both messages, marked not mine', thread.data.messages.length === 2 && thread.data.messages.every((m) => !m.mine));
check('reading clears unread', (await call('GET', '/me', { token: b.token })).data.friends[0].unread === 0);
check('non-friend cannot read a thread', (await call('GET', `/messages?id=${a.me.id}`, { token: (await signIn(`Dan_${suffix}`)).token })).status === 403);

const cmp = await call('GET', `/friends/stats?id=${a.me.id}`, { token: b.token });
check('compare returns both stat sets, sanitized', cmp.data.stats.modes.mace.mastery === 80 && cmp.data.mine.modes.mace.mastery === 40 && !('evil' in cmp.data.stats));

const lbAll = await call('GET', '/leaderboard?metric=drill&mode=mace&drill=mace_smash', { token: a.token });
check('global drill leaderboard sorted high to low', lbAll.data.rows[0].value >= lbAll.data.rows[1].value && lbAll.data.rows.some((r) => r.me));
const lbLow = await call('GET', '/leaderboard?metric=drill&mode=elytra_mace&drill=ely_climb&lower=1&scope=friends', { token: a.token });
check('lower-is-better + friends scope', lbLow.data.rows[0].value <= lbLow.data.rows[1].value && lbLow.data.rows.length === 3);
check('total mastery board', (await call('GET', '/leaderboard?metric=total', { token: a.token })).data.rows.length >= 3);

const ch = await call('POST', '/challenges', { token: b.token, body: { id: a.me.id, mode: 'mace', drill: 'mace_smash' } });
check('challenge created, leader is the higher best', ch.data.challenge.leader === a.me.id, `${ch.data.challenge.from.score} vs ${ch.data.challenge.to.score}`);
check('duplicate challenge refused', (await call('POST', '/challenges', { token: a.token, body: { id: b.me.id, mode: 'mace', drill: 'mace_smash' } })).status === 400);
await call('POST', '/stats', { token: b.token, body: stats(45, 300) });
check('leader flips when the other player improves', (await call('GET', '/me', { token: a.token })).data.challenges[0].leader === b.me.id);

// --- progress sync across devices: merge, never overwrite ---
const pc = { stats: { modes: { mace: { mastery: 86, unlocked: true, drills: { mace_smash: { best: 252, medal: 3, runs: 9 } }, duels: { elite: { matchWins: 1, matchLosses: 4 } } }, spear: { mastery: 12, unlocked: true, drills: {} }, elytra_mace: { mastery: 20, drills: { ely_climb: { best: 5.2, medal: 2, runs: 4, lower: true } } } } } };
const laptop = { stats: { modes: { mace: { mastery: 11.1, unlocked: true, drills: { mace_smash: { best: 173.4, medal: 1, runs: 1 } } }, crystal: { mastery: 0, drills: { crystal_speed: { best: 0, medal: 0, runs: 1 } }, duels: { veteran: { matchWins: 0, matchLosses: 1 } } }, elytra_mace: { mastery: 0, drills: { ely_climb: { best: 8.8, medal: 1, runs: 1, lower: true } } } } } };
const dev = await signIn(`Sync_${suffix}`);
await call('POST', '/stats', { token: dev.token, body: pc });
const afterLaptop = await call('POST', '/stats', { token: dev.token, body: laptop });
const merged = afterLaptop.data.stats.modes;
check('a weaker device cannot lower mastery', merged.mace.mastery === 86 && afterLaptop.data.totalMastery >= 118, `total ${afterLaptop.data.totalMastery}`);
check('drill best, medal and runs keep the better side', merged.mace.drills.mace_smash.best === 252 && merged.mace.drills.mace_smash.medal === 3 && merged.mace.drills.mace_smash.runs === 9);
check('lower-is-better keeps the faster time', merged.elytra_mace.drills.ely_climb.best === 5.2);
check('each side contributes what only it has', merged.crystal.duels.veteran.matchLosses === 1 && merged.mace.duels.elite.matchWins === 1 && merged.spear.unlocked === true);
const reverse = await signIn(`SyncRev_${suffix}`);
await call('POST', '/stats', { token: reverse.token, body: laptop });
const afterPc = await call('POST', '/stats', { token: reverse.token, body: pc });
const canon = (v) => (v && typeof v === 'object' ? Object.keys(v).sort().map((k) => `${k}:${canon(v[k])}`).join(',') : String(v));
check('order does not matter (laptop first, then PC)', canon(afterPc.data.stats) === canon(afterLaptop.data.stats));
check('GET /stats returns the merged copy', (await call('GET', '/stats', { token: dev.token })).data.stats.modes.mace.mastery === 86);

// --- one account on two devices ---
const secondDevice = await signIn(a.me.name);
check('same account, second device: same identity and friend code', secondDevice.me.id === a.me.id && secondDevice.me.code === a.me.code && secondDevice.token !== a.token);
const fromFirst = await call('GET', '/me', { token: a.token });
const fromSecond = await call('GET', '/me', { token: secondDevice.token });
check('the first device stays signed in', fromFirst.status === 200);
check('both devices see the same friends and challenges', fromSecond.status === 200 && canon(fromFirst.data.friends.map((f) => f.id)) === canon(fromSecond.data.friends.map((f) => f.id)) && fromSecond.data.friends.length >= 2 && fromSecond.data.challenges.length === fromFirst.data.challenges.length);
check('messages are readable from the second device', (await call('GET', `/messages?id=${b.me.id}`, { token: secondDevice.token })).data.messages.length >= 2);

check('remove + block', (await call('POST', '/friends/remove', { token: b.token, body: { id: a.me.id, block: true } })).data.ok);
check('blocked player cannot find them again', (await call('POST', '/friends/request', { token: a.token, body: { query: b.me.code } })).status === 404);
check('messages stop after removal', (await call('POST', '/messages', { token: a.token, body: { id: b.me.id, text: 'hello?' } })).status === 403);

console.log(failures ? `\n${failures} FAILED` : '\nALL PASSED');
process.exit(failures ? 1 : 0);
