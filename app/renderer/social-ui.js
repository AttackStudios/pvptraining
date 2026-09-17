// Friends, messages, stat comparison, challenges and leaderboards.
// Talks to the friends service through window.pvpt.social (main process keeps the token).

export function createSocialUi(ctx) {
  const { api, state, $, $$, esc, toast, icon, hydrateIcons, play, modeOf, fmtScore, medalDots, goPage } = ctx;
  const social = { signedIn: false, me: null, data: null, friendId: null, tab: 'compare', thread: [], threadTimer: null, pageTimer: null, badgeTimer: null, board: { metric: 'total', mode: '', drill: '', scope: 'global' }, busy: false };

  const call = (method, route, payload) => api.social.api(method, route, payload);
  const initial = (name) => esc(String(name || '?').slice(0, 1).toUpperCase());
  const hue = (id) => [...String(id)].reduce((n, c) => (n * 31 + c.charCodeAt(0)) % 360, 7);
  const avatar = (u, size = 38) => `<span class="avatar" style="--h:${hue(u.id)};width:${size}px;height:${size}px;font-size:${Math.round(size * 0.42)}px">${initial(u.name)}${u.online ? '<i></i>' : ''}</span>`;

  function ago(ts) {
    if (!ts) return 'never seen';
    const s = Math.max(0, (Date.now() - ts) / 1000);
    if (s < 150) return 'online';
    if (s < 3600) return `${Math.round(s / 60)} min ago`;
    if (s < 86400) return `${Math.round(s / 3600)} h ago`;
    return `${Math.round(s / 86400)} d ago`;
  }

  function left(ms) {
    const s = Math.max(0, ms / 1000);
    if (s < 3600) return `${Math.max(1, Math.round(s / 60))} min left`;
    return `${Math.round(s / 3600)} h left`;
  }

  const allDrills = () => state.catalog.modes.flatMap((m) => m.drills.map((d) => ({ ...d, mode: m })));
  const drillOf = (modeId, drillId) => allDrills().find((d) => d.mode.id === modeId && d.id === drillId);

  async function refresh() {
    const st = await api.social.state();
    social.signedIn = st.signedIn;
    social.me = st.me;
    if (!st.signedIn) {
      social.data = null;
      paintBadge();
      return false;
    }
    const res = await call('GET', '/me');
    if (res.ok) {
      const before = unreadTotal();
      social.data = res.data;
      social.me = res.data.me;
      if (unreadTotal() > before && before >= 0) play('toast');
    } else if (res.status === 401) {
      social.signedIn = false;
      social.data = null;
    }
    paintBadge();
    return res.ok;
  }

  const unreadTotal = () => (social.data ? social.data.friends.reduce((n, f) => n + (f.unread || 0), 0) + social.data.incoming.length : 0);

  function paintBadge() {
    const btn = $('.nav-item[data-page="friends"]');
    if (!btn) return;
    let badge = $('.nav-badge', btn);
    const n = unreadTotal();
    if (!n) return badge?.remove();
    if (!badge) {
      badge = document.createElement('span');
      badge.className = 'nav-badge';
      btn.append(badge);
    }
    badge.textContent = n > 9 ? '9+' : n;
  }

  function startBadgePolling() {
    clearInterval(social.badgeTimer);
    refresh();
    social.badgeTimer = setInterval(() => {
      if (state.page !== 'friends') refresh();
    }, 45000);
  }

  function stopTimers() {
    clearInterval(social.threadTimer);
    clearInterval(social.pageTimer);
    social.threadTimer = social.pageTimer = null;
  }

  /* ------------------------------------------------------------ friends page */

  function pageFriends(el) {
    stopTimers();
    el.innerHTML = `<div class="page-head"><div><h2>Friends</h2><p>Add friends, message them, compare stats and challenge them.</p></div></div><div id="social-body"><div class="card pad empty">Loading…</div></div>`;
    paintFriends(el);
    social.pageTimer = setInterval(async () => {
      if (state.page !== 'friends') return stopTimers();
      if (social.friendId || document.activeElement?.tagName === 'INPUT') return;
      if (await refresh()) paintFriends(document);
    }, 15000);
  }

  async function paintFriends(root) {
    await refresh();
    const body = $('#social-body', root);
    if (!body) return;
    if (!social.signedIn) return paintSignIn(body);
    if (!social.data) {
      body.innerHTML = `<div class="card pad empty">The friends service is waking up or unreachable. <button class="btn small" id="retry" style="margin-left:10px">Try again</button></div>`;
      $('#retry', body).onclick = () => paintFriends(document);
      return;
    }
    if (social.friendId && social.data.friends.some((f) => f.id === social.friendId)) return paintFriend(body);
    social.friendId = null;
    const d = social.data;
    const reqRow = (u) => `<div class="s-row"><span class="who">${avatar(u)}<span><b>${esc(u.name)}</b><small>wants to be friends</small></span></span>
        <span class="acts"><button class="btn small primary" data-accept="${u.id}">Accept</button><button class="btn small ghost" data-decline="${u.id}">Decline</button></span></div>`;
    const friendRow = (f, i) => `<button class="s-row friend rise" style="--i:${i}" data-friend="${f.id}">
        <span class="who">${avatar(f)}<span><b>${esc(f.name)}</b><small>${ago(f.lastSeen)}</small></span></span>
        <span class="acts"><span class="chip">${Math.round(f.totalMastery)} mastery</span>${f.unread ? `<span class="unread">${f.unread}</span>` : ''}<span class="go" data-icon="arrow"></span></span></button>`;
    body.innerHTML = `
      <div class="social-top">
        <div class="card pad me-card rise">${avatar({ ...d.me, online: true }, 52)}
          <div><h3>${esc(d.me.name)}</h3><p>${Math.round(d.me.totalMastery)} total mastery${d.me.verified ? '' : ' · unverified test account'}</p></div>
          <button class="code" id="copy" title="Copy your friend code"><small>Your friend code</small><b>${esc(d.me.code)}</b></button>
        </div>
        <form class="card pad add-card rise" style="--i:1" id="add">
          <label for="q">Add a friend</label>
          <div class="add-row"><input id="q" placeholder="Their friend code or exact Minecraft name" maxlength="24" autocomplete="off" spellcheck="false" /><button class="btn primary" type="submit">Send request</button></div>
        </form>
      </div>
      ${d.incoming.length ? `<div class="section-title">Requests</div><div class="s-list">${d.incoming.map(reqRow).join('')}</div>` : ''}
      <div class="section-title">Friends${d.friends.length ? ` · ${d.friends.filter((f) => f.online).length} online` : ''}</div>
      <div class="s-list">${d.friends.length ? d.friends.map(friendRow).join('') : `<div class="card pad empty">No friends yet. Share your code <b>${esc(d.me.code)}</b>, or add someone with theirs.</div>`}</div>
      ${d.outgoing.length ? `<div class="section-title">Waiting for an answer</div><div class="s-list">${d.outgoing.map((u) => `<div class="s-row"><span class="who">${avatar(u)}<span><b>${esc(u.name)}</b><small>request sent</small></span></span><span class="acts"><button class="btn small ghost" data-cancel="${u.id}">Cancel</button></span></div>`).join('')}</div>` : ''}
      ${challengesHtml(d.challenges.filter((c) => !c.settled), 'Running challenges')}
      <div class="social-foot"><button class="btn small ghost" id="signout">Sign out</button></div>`;
    hydrateIcons(body);
    $('#copy', body).onclick = () => {
      navigator.clipboard.writeText(d.me.code);
      toast('Friend code copied.', 'good');
    };
    $('#add', body).onsubmit = async (e) => {
      e.preventDefault();
      const q = $('#q', body).value.trim();
      if (!q) return;
      const res = await call('POST', '/friends/request', { query: q });
      if (!res.ok) return toast(res.error, 'bad');
      toast(res.data.status === 'friends' ? `You and ${res.data.name} are now friends.` : `Request sent to ${res.data.name}.`, 'good');
      paintFriends(document);
    };
    const act = (sel, route, key, msg) => $$(sel, body).forEach((b) => (b.onclick = async () => {
      const res = await call('POST', route, { id: b.dataset[key] });
      if (!res.ok) return toast(res.error, 'bad');
      if (msg) toast(msg(res.data), 'good');
      paintFriends(document);
    }));
    act('[data-accept]', '/friends/accept', 'accept', (r) => `You and ${r.name} are now friends.`);
    act('[data-decline]', '/friends/decline', 'decline');
    act('[data-cancel]', '/friends/remove', 'cancel');
    $$('[data-friend]', body).forEach((b) => (b.onclick = () => {
      social.friendId = b.dataset.friend;
      social.tab = Number($('.unread', b)?.textContent) ? 'messages' : 'compare';
      paintFriends(document);
    }));
    $('#signout', body).onclick = async () => {
      await api.social.signOut();
      social.friendId = null;
      paintFriends(document);
    };
  }

  function paintSignIn(body) {
    const connected = state.conn.state === 'ready' || state.conn.state === 'live';
    body.innerHTML = `<div class="card signin rise">
        <div class="signin-art">${icon('friends')}</div>
        <h3>Sign in with your Minecraft account</h3>
        <p>Friends, messages, stat comparison, challenges and leaderboards. There is no password: your game proves the account is yours, the same way it does when you join a server. Only your name, your training stats and messages you send to friends are stored.</p>
        <button class="btn primary big" id="signin">${connected ? 'Sign in' : 'Connect to Minecraft first'}</button>
        <small>${connected ? `Signing in as ${esc(state.game.player || state.conn.instance?.username || 'your account')}` : 'Signing in needs your game running with the mod, so it can prove who you are.'}</small>
      </div>`;
    $('#signin', body).onclick = async () => {
      if (!connected) return goPage('train', true);
      if (social.busy) return;
      social.busy = true;
      const btn = $('#signin', body);
      btn.textContent = 'Asking Minecraft… (the server may take up to a minute to wake)';
      btn.disabled = true;
      const res = await api.social.signIn(state.game.player || state.conn.instance?.username);
      social.busy = false;
      if (!res.ok) {
        toast(res.error, 'bad');
        return paintSignIn(body);
      }
      play('connected');
      toast(`Signed in as ${res.me.name}. Your friend code is ${res.me.code}.`, 'good');
      paintFriends(document);
    };
  }

  /* ------------------------------------------------------------- one friend */

  function paintFriend(body) {
    clearInterval(social.threadTimer);
    const f = social.data.friends.find((x) => x.id === social.friendId);
    const tabs = [['compare', 'Compare'], ['messages', `Messages${f.unread ? ` (${f.unread})` : ''}`], ['challenges', 'Challenges']];
    body.innerHTML = `
      <button class="btn ghost small" id="fback" style="margin:-6px 0 12px -12px"><span data-icon="back"></span>All friends</button>
      <div class="friend-head rise">${avatar(f, 56)}<div><h3>${esc(f.name)}</h3><p>${ago(f.lastSeen)} · ${Math.round(f.totalMastery)} total mastery</p></div>
        <span class="acts"><button class="btn small ghost" id="unfriend">Remove</button><button class="btn small ghost" id="block">Block</button></span></div>
      <div class="s-tabs">${tabs.map(([id, label]) => `<button class="s-tab ${social.tab === id ? 'on' : ''}" data-tab="${id}" data-sfx="select">${label}</button>`).join('')}</div>
      <div id="friend-body"></div>`;
    hydrateIcons(body);
    $('#fback', body).onclick = () => {
      social.friendId = null;
      paintFriends(document);
    };
    $$('[data-tab]', body).forEach((b) => (b.onclick = () => {
      social.tab = b.dataset.tab;
      paintFriend(body);
    }));
    const drop = (block) => async () => {
      if (!confirm(block ? `Block ${f.name}? They will not be able to find or message you.` : `Remove ${f.name} from your friends?`)) return;
      await call('POST', '/friends/remove', { id: f.id, block });
      social.friendId = null;
      paintFriends(document);
    };
    $('#unfriend', body).onclick = drop(false);
    $('#block', body).onclick = drop(true);
    const inner = $('#friend-body', body);
    if (social.tab === 'messages') paintMessages(inner, f);
    else if (social.tab === 'challenges') paintChallenges(inner, f);
    else paintCompare(inner, f);
  }

  async function paintCompare(el, f) {
    el.innerHTML = `<div class="card pad empty">Loading ${esc(f.name)}'s stats…</div>`;
    const res = await call('GET', '/friends/stats', { id: f.id });
    if (!res.ok) return (el.innerHTML = `<div class="card pad empty">${esc(res.error)}</div>`);
    const mine = res.data.mine?.modes || {};
    const theirs = res.data.stats?.modes || {};
    let myWins = 0;
    let theirWins = 0;
    const cards = state.catalog.modes.map((mode, i) => {
      const a = mine[mode.id] || {};
      const b = theirs[mode.id] || {};
      const rows = mode.drills.map((d) => {
        const x = a.drills?.[d.id];
        const y = b.drills?.[d.id];
        const has = (v) => v && typeof v.best === 'number' && (!d.lowerIsBetter || v.best > 0);
        let win = 0;
        if (has(x) && has(y) && x.best !== y.best) win = (d.lowerIsBetter ? x.best < y.best : x.best > y.best) ? 1 : 2;
        else if (has(x) && !has(y)) win = 1;
        else if (!has(x) && has(y)) win = 2;
        if (win === 1) myWins++;
        if (win === 2) theirWins++;
        return `<div class="cmp-row"><span class="v ${win === 1 ? 'win' : ''}">${has(x) ? fmtScore(x.best, d.unit) : 'no run'}${medalDots(x?.medal || 0)}</span><span class="n">${esc(d.name)}</span><span class="v r ${win === 2 ? 'win' : ''}">${medalDots(y?.medal || 0)}${has(y) ? fmtScore(y.best, d.unit) : 'no run'}</span></div>`;
      }).join('');
      const ma = Math.round(a.mastery || 0);
      const mb = Math.round(b.mastery || 0);
      return `<div class="card cmp-card rise" style="--i:${i};--c:${mode.color}">
          <div class="cmp-head"><span class="glyph">${icon(mode.id)}</span><b>${esc(mode.name)}</b></div>
          <div class="cmp-mastery"><span class="pct ${ma > mb ? 'win' : ''}">${ma}%</span><span class="duo"><i style="width:${ma}%"></i><i class="them" style="width:${mb}%"></i></span><span class="pct r ${mb > ma ? 'win' : ''}">${mb}%</span></div>
          ${rows}</div>`;
    }).join('');
    el.innerHTML = `<div class="cmp-score rise"><span><b>You</b>${myWins} drills ahead</span><span class="vs">vs</span><span class="r"><b>${esc(f.name)}</b>${theirWins} drills ahead</span></div><div class="cmp-grid">${cards}</div>`;
  }

  function bubble(m) {
    const t = new Date(m.ts);
    return `<div class="msg ${m.mine ? 'mine' : ''}"><p>${esc(m.text)}</p><time>${t.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</time></div>`;
  }

  async function paintMessages(el, f) {
    el.innerHTML = `<div class="card chat"><div class="thread scroll" id="thread"><div class="empty">Loading…</div></div>
        <form class="composer" id="composer"><input id="text" maxlength="300" placeholder="Message ${esc(f.name)}" autocomplete="off" /><button class="btn primary" type="submit">Send</button></form></div>`;
    const thread = $('#thread', el);
    let lastTs = 0;
    social.thread = [];
    const load = async () => {
      const res = await call('GET', '/messages', { id: f.id, since: lastTs });
      if (!res.ok || !document.body.contains(thread)) return;
      if (!lastTs) thread.innerHTML = res.data.messages.length ? '' : `<div class="empty">No messages yet. Say hi, or send a challenge.</div>`;
      if (res.data.messages.length) {
        $('.empty', thread)?.remove();
        const incoming = res.data.messages.filter((m) => !m.mine).length;
        thread.insertAdjacentHTML('beforeend', res.data.messages.map(bubble).join(''));
        thread.scrollTop = thread.scrollHeight;
        if (lastTs && incoming) play('toast');
        lastTs = res.data.messages[res.data.messages.length - 1].ts;
      } else if (!lastTs) lastTs = 1;
      f.unread = 0;
    };
    await load();
    social.threadTimer = setInterval(() => {
      if (!document.body.contains(thread)) return clearInterval(social.threadTimer);
      load();
    }, 4000);
    $('#text', el).focus();
    $('#composer', el).onsubmit = async (e) => {
      e.preventDefault();
      const input = $('#text', el);
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      const res = await call('POST', '/messages', { id: f.id, text });
      if (!res.ok) {
        input.value = text;
        return toast(res.error, 'bad');
      }
      play('select');
      await load();
    };
  }

  function challengesHtml(list, title) {
    if (!list.length) return '';
    const row = (c) => {
      const d = drillOf(c.mode, c.drill);
      const mine = c.from.id === social.me.id ? c.from : c.to;
      const theirs = c.from.id === social.me.id ? c.to : c.from;
      const unit = d?.unit || '';
      const lead = c.leader === social.me.id ? 'you' : c.leader ? 'them' : 'tie';
      const status = c.settled ? (lead === 'you' ? 'You won' : lead === 'them' ? `${esc(theirs.name)} won` : 'Draw') : left(c.expiresAt - Date.now());
      return `<div class="ch-row" style="--c:${d?.mode.color || 'var(--accent)'}">
          <span class="ch-name"><b>${esc(d?.name || c.drill)}</b><small>${esc(d?.mode.name || c.mode)} · ${status}</small></span>
          <span class="ch-score ${lead === 'you' ? 'win' : ''}"><small>You</small>${mine.score === null ? 'no run' : fmtScore(mine.score, unit)}</span>
          <span class="vs">vs</span>
          <span class="ch-score ${lead === 'them' ? 'win' : ''}"><small>${esc(theirs.name)}</small>${theirs.score === null ? 'no run' : fmtScore(theirs.score, unit)}</span>
        </div>`;
    };
    return `<div class="section-title">${title}</div><div class="card ch-list">${list.map(row).join('')}</div>`;
  }

  function paintChallenges(el, f) {
    const withFriend = social.data.challenges.filter((c) => c.from.id === f.id || c.to.id === f.id);
    const open = state.catalog.modes.filter((m) => !m.parent || state.progress?.modes?.[m.id]?.unlocked);
    el.innerHTML = `
      <form class="card pad new-ch rise" id="newch">
        <label>Challenge ${esc(f.name)} on a drill. You both have 48 hours: best score wins.</label>
        <div class="add-row"><select id="chdrill">${open.map((m) => `<optgroup label="${esc(m.name)}">${m.drills.map((d) => `<option value="${m.id}|${d.id}">${esc(d.name)}</option>`).join('')}</optgroup>`).join('')}</select>
        <button class="btn primary" type="submit">Send challenge</button></div>
      </form>
      ${challengesHtml(withFriend.filter((c) => !c.settled), 'Running')}
      ${challengesHtml(withFriend.filter((c) => c.settled), 'Finished')}
      ${withFriend.length ? '' : `<div class="card pad empty" style="margin-top:14px">No challenges with ${esc(f.name)} yet.</div>`}`;
    $('#newch', el).onsubmit = async (e) => {
      e.preventDefault();
      const [mode, drill] = $('#chdrill', el).value.split('|');
      const d = drillOf(mode, drill);
      const res = await call('POST', '/challenges', { id: f.id, mode, drill, lower: Boolean(d?.lowerIsBetter) });
      if (!res.ok) return toast(res.error, 'bad');
      play('start');
      toast(`Challenge sent: ${d.name}. Go set a score.`, 'good');
      await refresh();
      paintChallenges(el, f);
    };
  }

  /* ------------------------------------------------------------ leaderboards */

  function pageRanks(el) {
    stopTimers();
    const b = social.board;
    const modeOptions = state.catalog.modes.map((m) => `<option value="${m.id}" ${b.mode === m.id ? 'selected' : ''}>${esc(m.name)}</option>`).join('');
    el.innerHTML = `
      <div class="page-head"><div><h2>Leaderboards</h2><p>Where you stand against everyone, or just your friends.</p></div>
        <div class="seg" id="scope"><button data-scope="global" class="${b.scope === 'global' ? 'on' : ''}" data-sfx="select">Everyone</button><button data-scope="friends" class="${b.scope === 'friends' ? 'on' : ''}" data-sfx="select">Friends</button></div></div>
      <div class="board-filters rise">
        <select id="bmode"><option value="">Total mastery, all gamemodes</option>${modeOptions}</select>
        <select id="bdrill" ${b.mode ? '' : 'disabled'}><option value="">Mastery</option>${b.mode ? modeOf(b.mode).drills.map((d) => `<option value="${d.id}" ${b.drill === d.id ? 'selected' : ''}>${esc(d.name)}</option>`).join('') : ''}</select>
      </div>
      <div class="card board rise" style="--i:1" id="board"><div class="empty">Loading…</div></div>`;
    $$('[data-scope]', el).forEach((btn) => (btn.onclick = () => {
      b.scope = btn.dataset.scope;
      pageRanksRepaint();
    }));
    $('#bmode', el).onchange = (e) => {
      b.mode = e.target.value;
      b.drill = '';
      pageRanksRepaint();
    };
    $('#bdrill', el).onchange = (e) => {
      b.drill = e.target.value;
      pageRanksRepaint();
    };
    loadBoard(el);
  }

  function pageRanksRepaint() {
    const page = $('.page.in');
    if (page && state.page === 'ranks') pageRanks(page);
  }

  async function loadBoard(el) {
    const box = $('#board', el);
    const st = await api.social.state();
    if (!st.signedIn) {
      box.innerHTML = `<div class="empty">Sign in on the Friends page to see the leaderboards and put your scores on them.<br/><button class="btn small primary" id="tofriends" style="margin-top:14px">Go to Friends</button></div>`;
      $('#tofriends', box).onclick = () => goPage('friends', true);
      return;
    }
    const b = social.board;
    const d = b.mode && b.drill ? drillOf(b.mode, b.drill) : null;
    const query = { scope: b.scope, metric: d ? 'drill' : b.mode ? 'mastery' : 'total', mode: b.mode, drill: b.drill, lower: d?.lowerIsBetter ? 1 : '' };
    await api.social.upload();
    const res = await call('GET', '/leaderboard', query);
    if (!document.body.contains(box)) return;
    if (!res.ok) return (box.innerHTML = `<div class="empty">${esc(res.error)}</div>`);
    const unit = d ? d.unit : b.mode ? '% mastery' : 'mastery';
    const fmt = (v) => (d ? fmtScore(v, unit) : b.mode ? `${Math.round(v)}%` : `${Math.round(v)}`);
    const rows = res.data.rows;
    box.innerHTML = rows.length
      ? `<div class="board-head"><span>#</span><span>Player</span><span>${esc(d ? d.name : b.mode ? `${modeOf(b.mode).name} mastery` : 'Total mastery')}</span></div>
         ${rows.map((r, i) => `<div class="board-row ${r.me ? 'me' : ''} rise" style="--i:${Math.min(i, 12)}"><span class="rank r${r.rank}">${r.rank}</span><span class="who">${avatar(r, 30)}<b>${esc(r.name)}</b>${r.me ? '<em>you</em>' : r.friend ? '<em>friend</em>' : ''}</span><span class="val">${fmt(r.value)}</span></div>`).join('')}
         <div class="board-foot">${res.data.myRank ? `You are #${res.data.myRank} of ${res.data.players}` : `${res.data.players} ranked. Set a score here to join them.`}</div>`
      : `<div class="empty">Nobody has a score here yet. Be the first.</div>`;
  }

  return { pageFriends, pageRanks, startBadgePolling, stopTimers, refresh };
}
