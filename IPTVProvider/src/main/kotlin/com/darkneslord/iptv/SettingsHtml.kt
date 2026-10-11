package com.darkneslord.iptv

/** Ayar ekranının HTML'i. Not: Kotlin ham dizgisinde '$' kullanma (JS içinde de). */
object SettingsHtml {
    const val PAGE = """
<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root {
    --bg:#0D0F12; --panel:#15181D; --raise:#1C2027; --line:#262B33;
    --text:#E8EAED; --mute:#8A919C; --dim:#5F6672;
    --accent:#2FD0B5; --on-accent:#06201B; --amber:#E6B450; --red:#EA6A5F; --ok:#4CC38A;
  }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  html { background:var(--bg); }
  body { margin:0; padding:22px 16px 96px; background:var(--bg); color:var(--text);
         font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; font-size:15px; line-height:1.45; }
  .wrap { max-width:560px; margin:0 auto; }

  /* Başlık: tek vurgulu öğe, yayın test çubukları */
  header h1 { font-size:26px; line-height:1.15; font-weight:750; letter-spacing:-.02em; margin:0 0 6px; }
  header p { margin:0; color:var(--mute); font-size:14px; max-width:44ch; }
  .bars { display:flex; height:7px; margin:16px 0 22px; border-radius:4px; overflow:hidden; opacity:.9; }
  .bars i { flex:1; }
  .bars i:nth-child(1){background:#D2D4D8} .bars i:nth-child(2){background:#D3C25A} .bars i:nth-child(3){background:#47B7C7}
  .bars i:nth-child(4){background:#55B873} .bars i:nth-child(5){background:#B857B8} .bars i:nth-child(6){background:#CC5A47}
  .bars i:nth-child(7){background:#4560CC}

  .notice { display:none; border:1px solid var(--amber); background:rgba(230,180,80,.08); color:#F2D391;
            padding:8px 12px; border-radius:10px; font-size:13.5px; margin-bottom:18px; }
  .notice.on { display:block; }

  section { margin-bottom:22px; }
  .page { display:none; } .page.on { display:block; }
  .head { display:flex; align-items:baseline; justify-content:space-between; gap:12px; margin-bottom:10px; }
  h2 { font-size:17px; font-weight:700; letter-spacing:-.01em; margin:0; display:flex; align-items:center; gap:8px; }
  .count { color:var(--mute); font-size:13px; font-weight:500; }
  .sub { color:var(--mute); font-size:13px; margin:-4px 0 12px; }

  /* Form */
  .form { display:none; margin-bottom:16px; background:var(--panel); border:1px solid var(--line); border-radius:14px; padding:16px; }
  label { display:block; font-size:13px; color:var(--mute); margin:0 0 6px; }
  input { width:100%; min-height:46px; padding:11px 13px; border-radius:10px; border:1px solid var(--line);
          background:var(--bg); color:var(--text); font-size:15px; font-family:inherit; margin-bottom:14px; }
  input::placeholder { color:var(--dim); }
  input:focus { outline:2px solid var(--accent); outline-offset:1px; border-color:transparent; }
  .actions { display:flex; gap:10px; }
  button { font-family:inherit; font-size:14.5px; font-weight:650; min-height:44px; padding:0 18px; border-radius:10px;
           border:1px solid var(--line); background:var(--raise); color:var(--text); }
  button:active { transform:translateY(1px); }
  button:focus-visible { outline:2px solid var(--accent); outline-offset:2px; }
  button.primary { background:var(--accent); border-color:var(--accent); color:var(--on-accent); flex:1; }
  button:disabled { opacity:.55; }
  .form.on { display:block; }
  .msg { min-height:20px; font-size:13.5px; margin-top:12px; }
  .msg.err { color:var(--red); } .msg.ok { color:var(--ok); }

  /* Liste satırları */
  .rows { display:flex; flex-direction:column; gap:14px; }
  .row { background:var(--panel); border:1px solid var(--line); border-radius:16px; padding:16px; box-shadow:0 1px 0 rgba(255,255,255,.03) inset; }
  .top { display:flex; gap:11px; align-items:flex-start; }
  .dot { width:9px; height:9px; border-radius:50%; background:var(--dim); margin-top:7px; flex:none; }
  .dot.ok { background:var(--ok); } .dot.bad { background:var(--red); } .dot.run { background:var(--amber); }
  .main { min-width:0; flex:1; }
  .name { font-size:16px; font-weight:700; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
  .url { font-size:12px; color:var(--dim); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:2px; font-family:ui-monospace,Menlo,Consolas,monospace; }
  .state { font-size:13px; color:var(--mute); margin-top:5px; }
  .state.ok { color:var(--ok); } .state.bad { color:var(--red); }
  .badge { font-size:11.5px; font-weight:600; color:var(--amber); border:1px solid rgba(230,180,80,.4); border-radius:6px; padding:1px 7px; }
  .tools { display:flex; gap:6px; margin:12px 0 0 20px; flex-wrap:wrap; align-items:center; }
  .tools button { min-height:38px; padding:0 11px; font-size:13.5px; background:transparent; }
  .tools button.danger { color:var(--red); border-color:rgba(234,106,95,.35); }
  .tools button.danger.solid { background:var(--red); color:#2A0A07; border-color:var(--red); }
  .ask { font-size:13.5px; color:var(--mute); margin-right:2px; }
  .empty { background:var(--panel); border:1px dashed var(--line); border-radius:16px; padding:26px 18px; text-align:center; color:var(--mute); font-size:14px; }

  /* Aktif / pasif: tek düğme, iki durum */
  .tools button.sw { display:inline-flex; align-items:center; gap:8px; border-color:rgba(76,195,138,.45); color:var(--ok); }
  .tools button.sw i { width:8px; height:8px; border-radius:50%; background:var(--ok); }
  .tools button.sw.off { border-color:var(--line); color:var(--mute); }
  .tools button.sw.off i { background:var(--dim); }
  .row.off .name, .row.off .url { opacity:.5; }


  /* Kategoriler: göz ikonuyla göster / gizle */
  .cats { margin:14px 0 0; border-top:1px solid var(--line); padding-top:10px; }
  .cats .bar { display:block; margin-bottom:8px; color:var(--mute); font-size:13px; }
  .cats .bar span:last-child { display:flex; gap:8px; margin-top:8px; }
  .cats .bar button { min-height:34px; padding:0 12px; font-size:12.5px; background:transparent; }
  .cat { display:flex; align-items:center; gap:12px; width:100%; min-height:44px; padding:0 4px; border:0; border-radius:10px;
         background:transparent; text-align:left; font-weight:500; font-size:14.5px; color:var(--text); }
  .cat:active { background:var(--raise); }
  .cat svg { width:20px; height:20px; color:var(--accent); }
  .cat .n { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .cat .c { color:var(--dim); font-size:12.5px; font-weight:500; }
  .cat.hid { color:var(--dim); }
  .cat.hid svg { color:var(--dim); }
  .cat.hid .n { text-decoration:line-through; }
  .cats .note { color:var(--mute); font-size:13px; padding:8px 4px; }
  .tools button.cb { display:inline-flex; align-items:center; gap:6px; }
  .tools button.cb svg { width:15px; height:15px; transition:transform .15s; }
  .tools button.cb.open svg { transform:rotate(180deg); }

  /* Alt sekme çubuğu */
  nav { position:fixed; left:0; right:0; bottom:0; display:flex; background:var(--panel); border-top:1px solid var(--line);
        padding:6px 10px calc(6px + env(safe-area-inset-bottom)); z-index:5; }
  nav button { flex:1; min-height:56px; border:0; background:transparent; color:var(--mute); display:flex; flex-direction:column;
               align-items:center; justify-content:center; gap:3px; font-size:12.5px; font-weight:600; border-radius:12px; }
  nav button svg { width:23px; height:23px; }
  nav button.on { color:var(--accent); background:rgba(47,208,181,.09); }
  .addbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; }
  .addbar button { min-height:40px; font-size:14px; }
  svg { width:16px; height:16px; flex:none; }
  @media (prefers-reduced-motion:no-preference) { .dot.run { animation:pulse 1s ease-in-out infinite; } }
  @keyframes pulse { 50% { opacity:.35; } }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>IPTV listeleri</h1>
    <p id="sub">Kaydettiğin her M3U / M3U8 linki CloudStream'de ayrı bir kaynak olarak görünür.</p>
    <div class="bars" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
  </header>

  <div class="notice" id="notice">Değişikliklerin CloudStream'de görünmesi için uygulamayı kapatıp yeniden aç.</div>

  <!-- Sayfa 1: Kullanıcı -->
  <div class="page on" id="p-user">
    <div class="addbar">
      <h2 id="formTitle">Listelerim <span class="count" id="ucount"></span></h2>
      <button class="primary" id="openForm" style="flex:none">Yeni liste</button>
    </div>
    <div class="form" id="form">
      <label for="fname">Ad</label>
      <input id="fname" type="text" placeholder="Örn. Spor kanalları" autocomplete="off" maxlength="60">
      <label for="furl">M3U / M3U8 linki</label>
      <input id="furl" type="url" placeholder="https://ornek.com/liste.m3u" autocomplete="off" autocapitalize="off" spellcheck="false">
      <div class="actions">
        <button class="primary" id="save">Listeyi kaydet</button>
        <button id="cancel">Vazgeç</button>
      </div>
      <div class="msg" id="msg" role="status"></div>
    </div>
    <div class="rows" id="user"></div>
  </div>

  <!-- Sayfa 2: Yönetici -->
  <div class="page" id="p-admin">
    <div class="addbar">
      <h2>Yönetici listeleri <span class="count" id="acount"></span></h2>
      <button id="refresh" style="flex:none">Yenile</button>
    </div>
    <p class="sub">Silinemez ve düzenlenemez; istersen pasif yapabilirsin.</p>
    <div class="rows" id="admin"></div>
    <div class="msg" id="rmsg" role="status"></div>
  </div>
</div>

<nav aria-label="Bölümler">
  <button class="on" id="t-user" data-tab="user">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/></svg>
    Kullanıcı
  </button>
  <button id="t-admin" data-tab="admin">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6l8-3z"/><path d="M9 12l2 2 4-4"/></svg>
    Yönetici
  </button>
</nav>

<script>
  var state = { user: [], admin: [], adminSet: false };
  var editing = null, confirming = null, running = {};

  function byId(i) { return document.getElementById(i); }
  function esc(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
  function setMsg(el, kind, text) { var e = byId(el); e.className = 'msg ' + kind; e.textContent = text; }
  function showNotice() { byId('notice').className = 'notice on'; }

  var cats = {};   // id -> { open, loading, items }
  var EYE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
  var EYE_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.9 17.9A10.9 10.9 0 0 1 12 19C5.6 19 2 12 2 12a18.5 18.5 0 0 1 5.1-5.9M9.9 4.2A10.4 10.4 0 0 1 12 4c6.4 0 10 7 10 7a18.6 18.6 0 0 1-2.2 3.2M14.1 14.1a3 3 0 1 1-4.2-4.2"/><path d="M1 1l22 22"/></svg>';
  var CHEV = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>';

  function catsHtml(l) {
    var c = cats[l.id];
    if (!c || !c.open) return '';
    var h = '<div class="cats">';
    if (c.loading) return h + '<div class="note">Kategoriler yükleniyor...</div></div>';
    if (!c.items.length) return h + '<div class="note">Kategori bulunamadı. "Test et"e basıp listenin açılıp açılmadığına bak.</div></div>';
    var hiddenN = c.items.filter(function(x) { return x.h; }).length;
    h += '<div class="bar"><span>' + (c.items.length - hiddenN) + ' görünür, ' + hiddenN + ' gizli</span><span>' +
         '<button data-act="cat-all" data-id="' + esc(l.id) + '" data-v="0">Hepsini göster</button> ' +
         '<button data-act="cat-all" data-id="' + esc(l.id) + '" data-v="1">Hepsini gizle</button></span></div>';
    c.items.forEach(function(g, i) {
      h += '<button class="cat' + (g.h ? ' hid' : '') + '" data-act="cat" data-id="' + esc(l.id) + '" data-gi="' + i + '" aria-pressed="' + (!g.h) + '">' +
           (g.h ? EYE_OFF : EYE) + '<span class="n">' + esc(g.n) + '</span><span class="c">' + (g.c || '') + '</span></button>';
    });
    return h + '</div>';
  }

  function loadCats(id) {
    var items = JSON.parse(Android.getGroups(id));
    cats[id] = cats[id] || { open: true };
    cats[id].items = items; cats[id].loading = false;
    return items.length;
  }

  function stateOf(l) {
    if (running[l.id]) return { cls: 'run', line: 'Test ediliyor...', t: '' };
    if (!l.status) return { cls: '', line: 'Henüz test edilmedi', t: '' };
    return l.status.ok ? { cls: 'ok', line: l.status.text, t: 'ok' } : { cls: 'bad', line: l.status.text, t: 'bad' };
  }

  function rowHtml(l, admin) {
    var s = stateOf(l);
    var h = '<div class="row' + (l.enabled ? '' : ' off') + '"><div class="top"><span class="dot ' + s.cls + '"></span><div class="main">' +
            '<div class="name">' + esc(l.name) + (admin ? '<span class="badge">yönetici</span>' : '') + '</div>' +
            '<div class="url">' + esc(l.url) + '</div>' +
            '<div class="state ' + s.t + '">' + esc(s.line) + '</div></div></div><div class="tools">';
    h += '<button class="sw' + (l.enabled ? '' : ' off') + '" data-act="toggle" data-id="' + esc(l.id) + '" aria-pressed="' + l.enabled + '"><i></i>' + (l.enabled ? 'Aktif' : 'Pasif') + '</button>';
    h += '<button data-act="test" data-id="' + esc(l.id) + '"' + (running[l.id] ? ' disabled' : '') + '>Test et</button>';
    if (!admin) {
      if (confirming === l.id) {
        h += '<span class="ask">Silinsin mi?</span>' +
             '<button class="danger solid" data-act="del-yes" data-id="' + esc(l.id) + '">Sil</button>' +
             '<button data-act="del-no">Vazgeç</button>';
      } else {
        h += '<button data-act="edit" data-id="' + esc(l.id) + '">Düzenle</button>' +
             '<button class="danger" data-act="del" data-id="' + esc(l.id) + '">Sil</button>';
      }
    }
    h += '<button class="cb' + (cats[l.id] && cats[l.id].open ? ' open' : '') + '" data-act="cats" data-id="' + esc(l.id) + '">Kategoriler ' + CHEV + '</button>';
    return h + '</div>' + catsHtml(l) + '</div>';
  }

  function activeCount(a) { return a.filter(function(x) { return x.enabled; }).length; }

  function render() {
    byId('user').innerHTML = state.user.length ? state.user.map(function(l) { return rowHtml(l, false); }).join('')
      : '<div class="empty">Henüz liste yok. "Yeni liste"ye basıp bir ad ve M3U linki kaydet.</div>';
    byId('ucount').textContent = state.user.length ? activeCount(state.user) + '/' + state.user.length + ' aktif' : '';

    byId('refresh').style.display = state.adminSet ? '' : 'none';
    byId('admin').innerHTML = state.admin.length ? state.admin.map(function(l) { return rowHtml(l, true); }).join('')
      : '<div class="empty">' + (state.adminSet ? 'Yönetici listesi henüz yüklenmedi. Yenile\'ye bas.' : 'Yönetici listesi ayarlanmamış.') + '</div>';
    byId('acount').textContent = state.admin.length ? activeCount(state.admin) + '/' + state.admin.length + ' aktif' : '';
  }

  function load() { state = JSON.parse(Android.getState()); render(); }

  function openForm(on) { byId('form').className = on ? 'form on' : 'form'; byId('openForm').style.display = on ? 'none' : ''; }
  function resetForm() {
    editing = null;
    byId('fname').value = ''; byId('furl').value = '';
    byId('save').textContent = 'Listeyi kaydet';
    openForm(false);
  }
  function showTab(t) {
    byId('p-user').className = 'page' + (t === 'user' ? ' on' : '');
    byId('p-admin').className = 'page' + (t === 'admin' ? ' on' : '');
    byId('t-user').className = t === 'user' ? 'on' : '';
    byId('t-admin').className = t === 'admin' ? 'on' : '';
    byId('sub').textContent = t === 'user' ? "Kaydettiğin her M3U / M3U8 linki CloudStream'de ayrı bir kaynak olarak görünür."
                                            : "Yönetici tarafından eklenen listeler.";
    window.scrollTo(0, 0);
  }
  byId('openForm').onclick = function() { setMsg('msg', '', ''); openForm(true); byId('fname').focus(); };
  document.querySelectorAll('nav button').forEach(function(b) { b.onclick = function() { showTab(b.getAttribute('data-tab')); }; });

  byId('save').onclick = function() {
    var n = byId('fname').value, u = byId('furl').value;
    var err = editing ? Android.updateList(editing, n, u) : Android.addList(n, u);
    if (err) { setMsg('msg', 'err', err); return; }
    resetForm(); setMsg('msg', '', ''); showNotice(); load();
  };
  byId('cancel').onclick = function() { resetForm(); setMsg('msg', '', ''); };

  byId('refresh').onclick = function() {
    byId('refresh').disabled = true; setMsg('rmsg', '', 'Yönetici listesi indiriliyor...');
    Android.refreshAdmin();
  };

  document.addEventListener('click', function(ev) {
    var b = ev.target.closest('button[data-act]');
    if (!b) return;
    var act = b.getAttribute('data-act'), id = b.getAttribute('data-id');
    if (act === 'toggle') {
      var all = state.user.concat(state.admin);
      var cur = all.filter(function(x) { return x.id === id; })[0];
      if (cur) { Android.setEnabled(id, !cur.enabled); showNotice(); load(); }
    }
    else if (act === 'cats') {
      var cc = cats[id];
      if (cc && cc.open) { cc.open = false; render(); return; }
      cats[id] = { open: true, loading: false, items: [] };
      if (loadCats(id) === 0) { cats[id].loading = true; Android.fetchGroups(id); }
      render();
    }
    else if (act === 'cat') {
      var g = cats[id].items[+b.getAttribute('data-gi')];
      g.h = !g.h; Android.setGroupHidden(id, g.n, g.h); showNotice(); render();
    }
    else if (act === 'cat-all') {
      var hide = b.getAttribute('data-v') === '1';
      Android.setAllGroupsHidden(id, hide);
      cats[id].items.forEach(function(x) { x.h = hide; }); showNotice(); render();
    }
    else if (act === 'test') { running[id] = true; render(); Android.testList(id); }
    else if (act === 'edit') {
      var l = state.user.filter(function(x) { return x.id === id; })[0]; if (!l) return;
      editing = id; byId('fname').value = l.name; byId('furl').value = l.url;
      byId('save').textContent = 'Değişiklikleri kaydet';
      openForm(true); setMsg('msg', '', '');
      window.scrollTo(0, 0); byId('fname').focus();
    }
    else if (act === 'del') { confirming = id; render(); }
    else if (act === 'del-no') { confirming = null; render(); }
    else if (act === 'del-yes') {
      Android.removeList(id); confirming = null;
      if (editing === id) resetForm();
      showNotice(); load();
    }
  });

  function onTest(id) { delete running[id]; if (cats[id] && cats[id].open) loadCats(id); load(); }
  function onGroups(id) { if (cats[id]) { loadCats(id); render(); } }
  function onRefresh(ok, text) {
    byId('refresh').disabled = false;
    setMsg('rmsg', ok ? 'ok' : 'err', text);
    if (ok) showNotice();
    load();
  }

  load();
</script>
</body>
</html>
"""
}
