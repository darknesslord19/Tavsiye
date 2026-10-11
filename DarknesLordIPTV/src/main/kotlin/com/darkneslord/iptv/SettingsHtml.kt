package com.darkneslord.iptv

/** Ayarlar ekranının HTML'i. Not: JS içinde Kotlin'le çakışmasın diye '$' kullanılmadı. */
object SettingsHtml {
    const val PAGE = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<style>
  :root {
    --bg:#0a0a0f; --card:#14141c; --card2:#1b1b26; --line:#2a2a3a;
    --text:#f4f4f8; --mut:#8e8ea3; --acc:#e11d48; --acc2:#6d28d9;
    --ok:#22c55e; --bad:#f43f5e; --warn:#f59e0b; --vio:#a78bfa; --blue:#60a5fa; --off:#3a3a4c;
  }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  html, body { margin:0; background:var(--bg); color:var(--text);
    font-family:Roboto,-apple-system,"Segoe UI",sans-serif; }
  body { padding:16px 14px 48px;
    background:radial-gradient(120% 40% at 50% 0%, rgba(109,40,217,.22), rgba(10,10,15,0) 70%), var(--bg); }
  svg { display:block; }
  button { font-family:inherit; }

  /* Başlık */
  .head { display:flex; align-items:center; gap:14px; padding:4px 2px 16px; }
  .logo { width:54px; height:54px; border-radius:16px; flex:none; display:grid; place-items:center; color:#fff;
    background:linear-gradient(135deg,var(--acc),var(--acc2)); box-shadow:0 10px 28px rgba(225,29,72,.35); }
  .head h1 { margin:0; font-size:23px; letter-spacing:.2px; }
  .head p { margin:3px 0 0; font-size:12px; color:var(--mut); }
  .head .grow { flex:1; min-width:0; }
  .ibtn { width:42px; height:42px; border-radius:50%; border:0; background:transparent; color:var(--mut);
    display:grid; place-items:center; flex:none; }
  .ibtn:active { background:rgba(255,255,255,.1); }

  /* İstatistik */
  .stats { display:flex; gap:8px; margin-bottom:12px; }
  .stat { flex:1; background:var(--card); border:1px solid var(--line); border-radius:16px; padding:12px 0; text-align:center; }
  .stat b { display:block; font-size:20px; }
  .stat span { display:block; margin-top:4px; font-size:11px; color:var(--mut); }

  .primary { width:100%; display:flex; align-items:center; justify-content:center; gap:8px; border:0; color:#fff;
    background:var(--acc); border-radius:16px; padding:14px; font-size:15px; font-weight:700; margin-bottom:14px;
    box-shadow:0 8px 22px rgba(225,29,72,.3); }
  .primary:active { opacity:.85; }

  /* Kart */
  .card { background:var(--card); border:1px solid var(--line); border-radius:22px; padding:16px 8px 8px 16px; margin-bottom:12px; }
  .top { display:flex; align-items:center; gap:12px; padding-right:8px; }
  .dot { width:11px; height:11px; border-radius:50%; flex:none; }
  .info { flex:1; min-width:0; }
  .name { font-size:16px; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .url { margin-top:4px; font-size:11px; color:var(--mut); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .off .info, .off .chips { opacity:.5; }
  .chips { display:flex; flex-wrap:wrap; gap:6px; margin-top:12px; padding-right:8px; }
  .chip { font-size:11px; font-weight:700; padding:5px 9px; border-radius:10px; background:rgba(255,255,255,.08); }
  .chip.ok { color:var(--ok); background:rgba(34,197,94,.14); }
  .chip.vio { color:var(--vio); background:rgba(167,139,250,.14); }
  .chip.bad { color:var(--bad); background:rgba(244,63,94,.14); }
  .chip.warn { color:var(--warn); background:rgba(245,158,11,.14); }
  .chip.mut { color:var(--mut); }
  .err { margin:10px 8px 0 0; font-size:12px; color:var(--bad); }
  .sep { height:1px; background:var(--line); margin:12px 8px 4px 0; }
  .actions { display:flex; align-items:center; }
  .catbtn { flex:1; display:flex; align-items:center; gap:6px; background:none; border:0; color:var(--text);
    font-size:13px; font-weight:700; padding:8px 0; text-align:left; }
  .catbtn small { color:var(--mut); font-weight:600; font-size:12px; }
  .catbtn svg { color:var(--mut); transition:transform .2s; }
  .catbtn.open svg { transform:rotate(180deg); }
  .spin svg { animation:sp 1s linear infinite; }
  @keyframes sp { to { transform:rotate(360deg); } }

  /* Anahtar */
  .sw { position:relative; width:46px; height:26px; flex:none; }
  .sw input { position:absolute; inset:0; opacity:0; width:100%; height:100%; margin:0; z-index:2; }
  .sw i { position:absolute; inset:0; border-radius:13px; background:var(--off); transition:background .15s; }
  .sw i::after { content:""; position:absolute; top:3px; left:3px; width:20px; height:20px; border-radius:50%;
    background:#fff; transition:transform .15s; }
  .sw input:checked + i { background:var(--ok); }
  .sw input:checked + i::after { transform:translateX(20px); }

  /* Kategoriler */
  .cats { padding:4px 8px 10px 0; }
  .bar { display:flex; align-items:center; gap:6px; margin-bottom:8px; }
  .bar .sum { flex:1; font-size:12px; font-weight:700; color:var(--mut); }
  .pill { border:0; font-size:12px; font-weight:700; padding:7px 10px; border-radius:10px; }
  .pill.g { color:var(--ok); background:rgba(34,197,94,.14); }
  .pill.r { color:var(--bad); background:rgba(244,63,94,.14); }
  .find { width:100%; margin-bottom:8px; }
  .row { display:flex; align-items:center; gap:8px; background:var(--card2); border-radius:14px; padding:2px 4px 2px 14px; margin-bottom:6px; }
  .row .n { flex:1; min-width:0; font-size:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .row .c { font-size:11px; font-weight:700; color:var(--mut); background:rgba(142,142,163,.14); padding:3px 8px; border-radius:8px; }
  .row .eye { color:var(--ok); }
  .row.hid .n, .row.hid .c { opacity:.4; }
  .row.hid .eye { color:var(--mut); }

  /* Form */
  input[type=text], input[type=url] { background:var(--card2); color:var(--text); border:1px solid var(--line);
    border-radius:14px; padding:13px 14px; font-size:14px; width:100%; outline:none; font-family:inherit; }
  input[type=text]:focus, input[type=url]:focus { border-color:var(--acc); }
  label.l { display:block; font-size:10px; font-weight:700; letter-spacing:.8px; color:var(--mut); margin:14px 2px 6px; }
  .modal { position:fixed; inset:0; background:rgba(0,0,0,.65); display:none; align-items:center; justify-content:center; padding:18px; z-index:10; }
  .modal.show { display:flex; }
  .box { width:100%; max-width:460px; background:var(--card); border:1px solid var(--line); border-radius:26px; padding:20px; max-height:92vh; overflow:auto; }
  .box h2 { margin:0; font-size:18px; }
  .box .sub { margin:6px 0 4px; font-size:12px; color:var(--mut); line-height:1.45; }
  .status { min-height:18px; margin-top:12px; font-size:12px; line-height:1.4; }
  .btns { display:flex; gap:8px; margin-top:16px; }
  .btn { flex:1; border:0; border-radius:14px; padding:13px 10px; font-size:14px; font-weight:700; color:var(--mut); background:rgba(142,142,163,.14); }
  .btn.main { flex:2; color:#fff; background:var(--acc); }
  .btn.blue { color:var(--blue); background:rgba(96,165,250,.14); }
  .btn.red { color:#fff; background:var(--bad); }
  .btn[disabled] { opacity:.5; }

  .empty { text-align:center; padding:40px 24px; color:var(--mut); font-size:13px; line-height:1.5; }
  .empty .logo { margin:0 auto 16px; opacity:.6; width:72px; height:72px; border-radius:22px; box-shadow:none; }
  .empty b { display:block; color:var(--text); font-size:17px; margin-bottom:6px; }
  .tools { margin-top:6px; }
  .ghost { width:100%; border:1px solid var(--line); background:var(--card); color:var(--mut); border-radius:14px; padding:12px; font-size:13px; font-weight:700; }
  pre { display:none; white-space:pre-wrap; word-break:break-word; font-size:12px; color:#bbb; background:var(--card);
    border:1px solid var(--line); border-radius:14px; padding:12px; margin:10px 0 0; }
  .toast { position:fixed; left:50%; bottom:22px; transform:translateX(-50%); background:#26263a; color:#fff; padding:11px 16px;
    border-radius:14px; font-size:13px; font-weight:600; display:none; z-index:20; max-width:90%; text-align:center; border:1px solid var(--line); }
</style>
</head>
<body>

<div class="head">
  <div class="logo" id="logo"></div>
  <div class="grow">
    <h1>Darknes Lord</h1>
    <p id="sub">IPTV yönetim merkezi</p>
  </div>
  <button class="ibtn" id="closeBtn"></button>
</div>

<div class="stats">
  <div class="stat"><b id="stLists">0</b><span>Liste</span></div>
  <div class="stat"><b id="stActive">0</b><span>Aktif</span></div>
  <div class="stat"><b id="stCh">0</b><span>Kanal</span></div>
</div>

<button class="primary" id="addBtn"></button>

<div id="list"></div>

<div class="tools">
  <button class="ghost" id="testAll">Tüm listeleri test et</button>
  <pre id="diag"></pre>
</div>

<div class="modal" id="modal"><div class="box" id="box"></div></div>
<div class="toast" id="toast"></div>

<script>
  var A = window.Android;
  var state = { playlists: [], version: '' };
  var openMap = {}, busy = {}, queryMap = {};

  var ICONS = {
    eye: '<path d="M2 12C5 6.5 8.5 5 12 5s7 1.5 10 7c-3 5.5-6.5 7-10 7S5 17.5 2 12z"/><circle cx="12" cy="12" r="3"/>',
    eyeoff: '<path d="M2 12C5 6.5 8.5 5 12 5s7 1.5 10 7c-3 5.5-6.5 7-10 7S5 17.5 2 12z"/><circle cx="12" cy="12" r="3"/><path d="M3.5 3.5l17 17"/>',
    edit: '<path d="M4 20l4.2-.8L19.5 7.9 16.1 4.5 4.8 15.8z"/><path d="M14 6.6l3.4 3.4"/>',
    trash: '<path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l1 13h10l1-13"/><path d="M10 11v5M14 11v5"/>',
    refresh: '<path d="M19.2 9.5A7.2 7.2 0 1 0 19.2 14.5"/><path d="M20.5 4v5.5H15"/>',
    chev: '<path d="M6 9l6 6 6-6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>'
  };
  function svg(name, size, color) {
    return '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size + '" fill="none" stroke="' + (color || 'currentColor') +
      '" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">' + ICONS[name] + '</svg>';
  }
  // Vektör IPTV / medya logosu: TV gövdesi, anten, oynat simgesi
  var LOGO = '<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#fff" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="4" y="8" width="16" height="10.5" rx="2.6"/><path d="M8.5 3.8L12 8l3.5-4.2"/>' +
    '<path d="M10.6 10.7v5.3l4.4-2.65z" fill="#fff" stroke="none"/><path d="M8 21h8"/></svg>';

  function q(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function icon(name, size, cls, color) {
    var b = el('button', 'ibtn' + (cls ? ' ' + cls : ''));
    b.innerHTML = svg(name, size || 21, color);
    return b;
  }
  function fmt(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }
  function pad(x) { return (x < 10 ? '0' : '') + x; }
  function when(ms) {
    if (!ms) return '—';
    var d = new Date(ms);
    return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  var toastTimer = null;
  function toast(text) {
    var t = q('toast');
    t.textContent = text; t.style.display = 'block';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.style.display = 'none'; }, 2600);
  }
  function load() { state = JSON.parse(A.getState()); q('sub').textContent = 'IPTV yönetim merkezi · ' + state.version; }
  function find(id) {
    for (var i = 0; i < state.playlists.length; i++) if (state.playlists[i].id === id) return state.playlists[i];
    return null;
  }
  function hidIdx(p, name) { return p.hidden.indexOf(name); }

  // ---------------------------------------------------------------- liste
  function render() {
    var ps = state.playlists, active = 0, ch = 0;
    ps.forEach(function (p) { if (p.enabled) { active++; if (p.status === 1) ch += p.count; } });
    q('stLists').textContent = ps.length;
    q('stActive').textContent = active;
    q('stCh').textContent = fmt(ch);
    var list = q('list');
    list.innerHTML = '';
    if (!ps.length) {
      var em = el('div', 'empty');
      var lg = el('div', 'logo'); lg.innerHTML = LOGO;
      em.appendChild(lg);
      em.appendChild(el('b', '', 'Henüz liste yok'));
      em.appendChild(document.createTextNode('M3U linkinizi ekleyin. Kısaltılmış linkler otomatik çözülür, liste okunur ve çalışmayan linkler kaydedilmez.'));
      list.appendChild(em);
      return;
    }
    ps.forEach(function (p) { list.appendChild(card(p)); });
  }

  function card(p) {
    var c = el('div', 'card' + (p.enabled ? '' : ' off'));
    var top = el('div', 'top');

    var dot = el('div', 'dot');
    var col = busy[p.id] ? 'var(--warn)' : !p.enabled ? 'var(--off)' : p.status === 1 ? 'var(--ok)' : p.status === 2 ? 'var(--bad)' : 'var(--mut)';
    dot.style.background = col;
    top.appendChild(dot);

    var info = el('div', 'info');
    info.appendChild(el('div', 'name', p.name));
    info.appendChild(el('div', 'url', p.url));
    top.appendChild(info);

    var sw = el('label', 'sw');
    var cb = el('input'); cb.type = 'checkbox'; cb.checked = p.enabled;
    cb.onchange = function () { p.enabled = cb.checked; A.setEnabled(p.id, cb.checked); render(); };
    sw.appendChild(cb); sw.appendChild(el('i'));
    top.appendChild(sw);
    c.appendChild(top);

    var chips = el('div', 'chips');
    if (busy[p.id]) chips.appendChild(el('span', 'chip warn', 'Kontrol ediliyor…'));
    else if (p.status === 1) {
      chips.appendChild(el('span', 'chip', fmt(p.count) + ' kanal'));
      chips.appendChild(el('span', 'chip vio', p.cats.length + ' kategori'));
      chips.appendChild(el('span', 'chip ok', '✓ ' + when(p.checked)));
    } else if (p.status === 2) chips.appendChild(el('span', 'chip bad', 'Çalışmıyor'));
    else chips.appendChild(el('span', 'chip mut', 'Henüz kontrol edilmedi'));
    c.appendChild(chips);

    if (p.status === 2 && !busy[p.id]) c.appendChild(el('div', 'err', p.error || 'Liste okunamadı.'));
    c.appendChild(el('div', 'sep'));

    var act = el('div', 'actions');
    var opened = !!openMap[p.id];
    var vis = p.cats.length - p.cats.filter(function (x) { return hidIdx(p, x.n) >= 0; }).length;
    var cbtn = el('button', 'catbtn' + (opened ? ' open' : ''));
    cbtn.appendChild(document.createTextNode('Kategoriler'));
    cbtn.appendChild(el('small', '', vis + '/' + p.cats.length));
    var ch = el('span'); ch.innerHTML = svg('chev', 18); cbtn.appendChild(ch);
    cbtn.onclick = function () { openMap[p.id] = !opened; render(); };
    act.appendChild(cbtn);

    var rf = icon('refresh', 20, busy[p.id] ? 'spin' : '', 'var(--warn)');
    rf.onclick = function () { if (busy[p.id]) return; busy[p.id] = true; render(); A.testPlaylist(p.id); };
    act.appendChild(rf);
    var ed = icon('edit', 20, '', 'var(--blue)'); ed.onclick = function () { editor(p); };
    act.appendChild(ed);
    var dl = icon('trash', 20, '', 'var(--bad)'); dl.onclick = function () { confirmDelete(p); };
    act.appendChild(dl);
    c.appendChild(act);

    if (opened) c.appendChild(cats(p));
    return c;
  }

  // ---------------------------------------------------------------- kategoriler (göz ikonu)
  function cats(p) {
    var box = el('div', 'cats');
    if (!p.cats.length) { box.appendChild(el('div', 'sum', 'Kategori bulunamadı. Listeyi yenileyin.')); return box; }

    var sum = el('div', 'sum');
    var rows = el('div');
    function updSum() {
      var v = p.cats.filter(function (x) { return hidIdx(p, x.n) < 0; }).length;
      sum.textContent = v + ' / ' + p.cats.length + ' kategori görünür';
    }
    function fill() {
      rows.innerHTML = '';
      var qs = (queryMap[p.id] || '').toLowerCase();
      p.cats.forEach(function (x) {
        if (qs && x.n.toLowerCase().indexOf(qs) < 0) return;
        var row = el('div', 'row');
        row.appendChild(el('div', 'n', x.n));
        row.appendChild(el('div', 'c', fmt(x.c)));
        var eye = icon('eye', 21, 'eye');
        function paint() {
          var h = hidIdx(p, x.n) >= 0;
          row.className = 'row' + (h ? ' hid' : '');
          eye.innerHTML = svg(h ? 'eyeoff' : 'eye', 21);
        }
        eye.onclick = function () {
          var i = hidIdx(p, x.n);
          if (i >= 0) p.hidden.splice(i, 1); else p.hidden.push(x.n);
          A.setCategoryHidden(p.id, x.n, i < 0);
          paint(); updSum(); refreshHeader(p);
        };
        row.appendChild(eye);
        paint();
        rows.appendChild(row);
      });
    }
    var bar = el('div', 'bar');
    bar.appendChild(sum);
    var g = el('button', 'pill g', 'Tümünü göster');
    g.onclick = function () { p.hidden = []; A.setAllHidden(p.id, false); updSum(); fill(); refreshHeader(p); };
    var r = el('button', 'pill r', 'Tümünü gizle');
    r.onclick = function () { p.hidden = p.cats.map(function (x) { return x.n; }); A.setAllHidden(p.id, true); updSum(); fill(); refreshHeader(p); };
    bar.appendChild(g); bar.appendChild(r);
    box.appendChild(bar);

    if (p.cats.length > 10) {
      var f = el('input', 'find'); f.type = 'text'; f.placeholder = 'Kategori ara…'; f.value = queryMap[p.id] || '';
      f.oninput = function () { queryMap[p.id] = f.value; fill(); };
      box.appendChild(f);
    }
    updSum(); fill();
    box.appendChild(rows);
    return box;
  }
  // "Kategoriler 12/38" sayacını yeniden çizmeden güncelle
  function refreshHeader(p) {
    var cards = q('list').children;
    for (var i = 0; i < state.playlists.length; i++) {
      if (state.playlists[i].id === p.id && cards[i]) {
        var s = cards[i].querySelector('.catbtn small');
        if (s) s.textContent = (p.cats.length - p.cats.filter(function (x) { return hidIdx(p, x.n) >= 0; }).length) + '/' + p.cats.length;
      }
    }
  }

  // ---------------------------------------------------------------- pencereler
  function showModal(node) { var b = q('box'); b.innerHTML = ''; b.appendChild(node); q('modal').classList.add('show'); }
  function hideModal() { q('modal').classList.remove('show'); }
  q('modal').onclick = function (e) { if (e.target === q('modal') && !saving) hideModal(); };

  var saving = false;
  function editor(p) {
    var wrap = el('div');
    wrap.appendChild(el('h2', '', p ? 'Listeyi düzenle' : 'Yeni IPTV listesi'));
    wrap.appendChild(el('div', 'sub', 'Link kaydedilmeden önce çözülür ve okunur; çalışmayan link eklenemez.'));
    wrap.appendChild(el('label', 'l', 'LİSTE ADI'));
    var nm = el('input'); nm.type = 'text'; nm.placeholder = 'Örn: Spor Kanalları'; nm.value = p ? p.name : '';
    wrap.appendChild(nm);
    wrap.appendChild(el('label', 'l', 'M3U LİNKİ'));
    var ur = el('input'); ur.type = 'url'; ur.placeholder = 'https://… (kısaltılmış link olabilir)'; ur.value = p ? p.url : '';
    wrap.appendChild(ur);
    var st = el('div', 'status'); wrap.appendChild(st);

    var btns = el('div', 'btns');
    var cancel = el('button', 'btn', 'İptal'); cancel.onclick = function () { if (!saving) hideModal(); };
    var paste = el('button', 'btn blue', 'Yapıştır');
    paste.onclick = function () {
      var t = A.paste();
      if (t) { ur.value = t.trim(); st.textContent = ''; } else toast('Panoda link yok');
    };
    var save = el('button', 'btn main', 'Kontrol et & Kaydet');
    save.onclick = function () {
      if (saving) return;
      var u = ur.value.trim();
      if (!u) { st.style.color = 'var(--bad)'; st.textContent = 'Lütfen bir link girin.'; return; }
      saving = true; save.disabled = true;
      st.style.color = 'var(--warn)'; st.textContent = 'Link çözülüyor ve liste okunuyor…';
      window.__editor = { st: st, save: save };
      A.savePlaylist(p ? p.id : '', nm.value, u);
    };
    btns.appendChild(cancel); btns.appendChild(paste); btns.appendChild(save);
    wrap.appendChild(btns);
    showModal(wrap);
  }
  function confirmDelete(p) {
    var wrap = el('div');
    wrap.appendChild(el('h2', '', 'Listeyi sil?'));
    wrap.appendChild(el('div', 'sub', '“' + p.name + '” ve kategori ayarları kalıcı olarak silinecek.'));
    var btns = el('div', 'btns');
    var no = el('button', 'btn', 'Vazgeç'); no.onclick = hideModal;
    var yes = el('button', 'btn red', 'Sil');
    yes.onclick = function () { A.removePlaylist(p.id); hideModal(); load(); render(); };
    btns.appendChild(no); btns.appendChild(yes);
    wrap.appendChild(btns);
    showModal(wrap);
  }

  // ---------------------------------------------------------------- Android -> JS geri çağrıları
  function onSaveResult(ok, text) {
    saving = false;
    var e = window.__editor;
    if (!ok) {
      if (e) { e.st.style.color = 'var(--bad)'; e.st.textContent = '✕ ' + text; e.save.disabled = false; }
      return;
    }
    hideModal(); load(); render(); toast(text);
  }
  function onTestResult(id, ok, text) {
    busy[id] = false; load(); render(); toast(ok ? text : 'Çalışmıyor: ' + text);
  }
  function onDiag(text) {
    var d = q('diag'); d.style.display = 'block'; d.textContent = text;
    q('testAll').disabled = false; load(); render();
  }

  q('logo').innerHTML = LOGO;
  q('closeBtn').innerHTML = svg('close', 22);
  q('closeBtn').onclick = function () { A.close(); };
  q('addBtn').innerHTML = svg('plus', 22) + '<span>Yeni IPTV listesi ekle</span>';
  q('addBtn').onclick = function () { editor(null); };
  q('testAll').onclick = function () {
    var d = q('diag'); d.style.display = 'block'; d.textContent = 'Listeler deneniyor, bir dakikaya kadar sürebilir…';
    q('testAll').disabled = true; A.testAll();
  };

  load();
  render();
</script>
</body>
</html>
"""
}
