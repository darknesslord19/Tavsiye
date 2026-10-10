package com.example.nuviobridge

/** Ayarlar ekranının HTML'i. Not: JS içinde Kotlin'le çakışmasın diye '$' kullanılmadı. */
object SettingsHtml {
    const val PAGE = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  body { font-family: sans-serif; background:#0a0f1e; color:#e6ecff; margin:0; padding:52px 16px 28px; }
  .panel { background:linear-gradient(180deg,#111a30,#0e1627); border:1px solid #1d2c52; border-radius:24px; padding:16px; margin-bottom:14px; }

  /* Üst kart */
  .head { display:flex; align-items:center; gap:14px; }
  .logo { width:64px; height:64px; border-radius:50%; background:#04070f; border:1px solid #1d2c52;
          display:flex; align-items:center; justify-content:center; flex:none; box-shadow:0 0 18px rgba(37,99,235,.35); }
  .ht { flex:1; min-width:0; }
  .brand { font-size:22px; font-weight:800; letter-spacing:1.5px; color:#fff; text-shadow:0 0 14px rgba(59,130,246,.55); }
  .brand2 { font-size:12px; letter-spacing:2.5px; color:#3b82f6; font-weight:700; margin-top:4px; }
  .gear { width:42px; height:42px; border-radius:50%; border:1px solid #243767; background:#12203f; color:#7aa7ff;
          display:flex; align-items:center; justify-content:center; padding:0; flex:none; }
  .bar { height:3px; border-radius:3px; background:#1a2a52; margin:14px 0 10px; }
  .status { font-size:12px; letter-spacing:2px; color:#3b82f6; font-weight:700; }
  .dot { display:inline-block; width:9px; height:9px; border-radius:50%; background:#3b82f6; margin-right:8px; }

  .label { font-size:12px; letter-spacing:3px; color:#8ea4d4; font-weight:700; margin:0 0 12px; }
  .row { display:flex; gap:8px; }
  input[type=url] { flex:1; min-width:0; padding:13px; border-radius:14px; border:1px solid #243767; background:#0a1224; color:#e6ecff; font-size:14px; }
  button { padding:12px 16px; border:0; border-radius:14px; background:#1f3a8a; color:#fff; font-size:14px; font-weight:600; }
  button:active { opacity:.8; }
  button.primary { background:linear-gradient(180deg,#2c52c9,#1f3a8a); }
  button.del { background:#7f1d1d; padding:8px 12px; border-radius:12px; }
  button.ghost { background:#16224a; border:1px solid #243767; padding:8px 12px; border-radius:12px; color:#bcd0ff; }
  .three { display:grid; grid-template-columns:1fr 1fr 1fr; gap:8px; margin-top:12px; }
  .three button { padding:12px 4px; font-size:12.5px; background:#16224a; border:1px solid #243767; color:#cfe0ff; }
  .three button.go { background:linear-gradient(180deg,#2c52c9,#1f3a8a); border-color:#3b5bdb; }

  #msg { min-height:18px; font-size:13px; margin-top:10px; }
  .ok { color:#34d399; } .err { color:#f87171; }

  .opt { display:flex; align-items:center; gap:12px; font-size:13.5px; color:#c3d2f5; }
  input[type=checkbox] { width:20px; height:20px; flex:none; accent-color:#3b82f6; }

  /* Günlük kutusu */
  .term { background:#060a14; border:1px solid #1d2c52; border-radius:18px; margin-bottom:14px; overflow:hidden; display:none; }
  .term .tb { display:flex; align-items:center; gap:6px; padding:10px 14px; border-bottom:1px solid #131d38; font-family:monospace; font-size:12px; color:#8ea4d4; }
  .term .c { width:11px; height:11px; border-radius:50%; display:inline-block; }
  pre#diag { margin:0; padding:12px 14px; white-space:pre-wrap; font-size:12px; color:#6ea0ff; font-family:monospace; max-height:340px; overflow:auto; }

  /* Repo kartları */
  .card { background:linear-gradient(180deg,#111a30,#0e1627); border:1px solid #1d2c52; border-radius:20px; padding:14px; margin-bottom:12px; }
  .top { display:flex; align-items:center; gap:12px; }
  .ico { width:46px; height:46px; border-radius:14px; background:#0a1224; border:1px solid #243767; flex:none; overflow:hidden;
         display:flex; align-items:center; justify-content:center; color:#7aa7ff; font-weight:800; font-size:20px; }
  .ico img { width:100%; height:100%; object-fit:cover; }
  .ico.sm { width:26px; height:26px; border-radius:8px; font-size:12px; }
  .info { flex:1; min-width:0; }
  .title { font-size:16px; font-weight:700; color:#fff; }
  .url { font-size:11px; color:#6f84b5; word-break:break-all; margin-top:2px; }
  .count { font-size:12px; color:#3b82f6; margin-top:3px; font-weight:600; }
  .acts { display:flex; flex-direction:column; gap:6px; }
  .provs { margin-top:10px; border-top:1px solid #1d2c52; padding-top:6px; }
  .prov { display:flex; align-items:center; gap:10px; padding:7px 2px; font-size:14px; color:#d3def7; }
  .empty { color:#6f84b5; text-align:center; padding:26px 0; }

  /* Hakkında penceresi */
  #ov { position:fixed; left:0; top:0; right:0; bottom:0; background:rgba(3,6,14,.78); display:none; align-items:center; justify-content:center; padding:20px; z-index:10; }
  #ov .box { width:100%; max-width:420px; background:linear-gradient(180deg,#14203d,#0e1627); border:1px solid #2a417a; border-radius:26px; padding:22px; text-align:center; box-shadow:0 0 40px rgba(37,99,235,.25); }
  #ov .logo { margin:0 auto 12px; width:76px; height:76px; }
  #ov h3 { margin:0; font-size:22px; letter-spacing:1.5px; color:#fff; text-shadow:0 0 14px rgba(59,130,246,.55); }
  #ov .sub { font-size:12px; letter-spacing:2.5px; color:#3b82f6; font-weight:700; margin:4px 0 14px; }
  #ov p { font-size:13.5px; line-height:1.55; color:#b7c6ea; margin:0 0 16px; text-align:left; }
  #ov button { width:100%; margin-top:8px; }
  #ov .tg { background:linear-gradient(180deg,#2aa7e0,#1b7fb8); display:flex; align-items:center; justify-content:center; gap:8px; }
</style>
</head>
<body>

  <div class="panel">
    <div class="head">
      <div class="logo">
        <svg width="40" height="40" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#60a5fa"/><stop offset="1" stop-color="#1d4ed8"/></linearGradient></defs>
          <path d="M20 46h26a10 10 0 0 0 1.5-19.9A14 14 0 0 0 20.6 24 11 11 0 0 0 20 46z" fill="url(#g)"/></svg>
      </div>
      <div class="ht">
        <div class="brand">DARKNES LORD</div>
        <div class="brand2">NUVIO SCRIPT</div>
      </div>
      <button class="gear" id="gear" aria-label="Ayarlar">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>
        </svg>
      </button>
    </div>
    <div class="bar"></div>
    <div class="status"><span class="dot"></span><span id="st">HAZIR</span></div>
  </div>

  <div class="panel">
    <div class="label">REPO EKLE</div>
    <div class="row">
      <input id="url" type="url" placeholder="https://.../manifest.json">
      <button class="primary" id="add">Ekle</button>
    </div>
    <div id="msg"></div>
    <div class="three">
      <button id="diagBtn">Bağlantı testi</button>
      <button id="provBtn">Sağlayıcı testi</button>
      <button id="keepBtn" class="go">Çalışanları Aç</button>
    </div>
  </div>

  <div class="panel">
    <label class="opt">
      <input type="checkbox" id="split">
      <span>Her sağlayıcıyı ayrı kaynak olarak göster (değişince Cloudstream'i kapatıp aç)</span>
    </label>
  </div>

  <div class="term" id="term">
    <div class="tb"><span class="c" style="background:#ff5f57"></span><span class="c" style="background:#febc2e"></span><span class="c" style="background:#28c840"></span>&nbsp; sonuc.log</div>
    <pre id="diag"></pre>
  </div>

  <div class="label" style="padding-left:6px">REPOLAR</div>
  <div id="list"></div>

  <div id="ov">
    <div class="box">
      <div class="logo">
        <svg width="46" height="46" viewBox="0 0 64 64"><path d="M20 46h26a10 10 0 0 0 1.5-19.9A14 14 0 0 0 20.6 24 11 11 0 0 0 20 46z" fill="url(#g)"/></svg>
      </div>
      <h3>DARKNES LORD</h3>
      <div class="sub">NUVIO SCRIPT</div>
      <p id="about">
        Nuvio sağlayıcı (provider) repolarını Cloudstream içinde çalıştıran köprü eklentisi.
        Manifest adresini ekle; film ve dizi listesi TMDB'den gelir, linkler sağlayıcılardan bulunur.
      </p>
      <button class="tg" id="tg">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="#fff"><path d="M21.9 3.6a1.4 1.4 0 0 0-1.5-.2L2.7 10.5a1.1 1.1 0 0 0 .1 2.1l4.5 1.4 1.7 5.4a1 1 0 0 0 1.7.4l2.5-2.5 4.6 3.4a1.4 1.4 0 0 0 2.2-.8L22.5 5a1.4 1.4 0 0 0-.6-1.4zM9.5 14.2l8.4-5.2-6.5 6.3-.3 2.6z"/></svg>
        Telegram
      </button>
      <button class="ghost" id="close">Kapat</button>
    </div>
  </div>

<script>
  var msg = document.getElementById('msg');
  var open = {};
  function say(text, ok) { msg.textContent = text; msg.className = ok ? 'ok' : 'err'; }
  function setStatus(t) { document.getElementById('st').textContent = t; }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  // Repo ikonu: GitHub reposu ise sahibinin avatarı, değilse sitenin favicon'u; olmazsa baş harf.
  function iconUrl(u) {
    try {
      var a = document.createElement('a');
      a.href = u;
      if (a.hostname === 'raw.githubusercontent.com' || a.hostname === 'github.com') {
        var owner = a.pathname.split('/')[1];
        if (owner) return 'https://github.com/' + owner + '.png?size=96';
      }
      return a.protocol + '//' + a.host + '/favicon.ico';
    } catch (e) { return ''; }
  }
  function makeIcon(r, small) {
    var box = el('div', small ? 'ico sm' : 'ico');
    var letter = (r.name || '?').charAt(0).toUpperCase();
    var src = iconUrl(r.url);
    if (!src) { box.textContent = letter; return box; }
    var img = document.createElement('img');
    img.onerror = function () { box.removeChild(img); box.textContent = letter; };
    img.src = src;
    box.appendChild(img);
    return box;
  }

  function render() {
    var repos = JSON.parse(Android.getRepos());
    var list = document.getElementById('list');
    list.innerHTML = '';
    if (repos.length === 0) {
      list.appendChild(el('div', 'empty', 'Henüz repo eklenmedi'));
      return;
    }
    repos.forEach(function (r) {
      var card = el('div', 'card');
      var top = el('div', 'top');

      var cb = el('input');
      cb.type = 'checkbox';
      cb.checked = r.enabled;
      cb.onchange = function () { Android.setEnabled(r.url, cb.checked); };

      var info = el('div', 'info');
      info.appendChild(el('div', 'title', r.name));
      info.appendChild(el('div', 'url', r.url));
      var active = r.providers.filter(function (p) { return p.enabled; }).length;
      info.appendChild(el('div', 'count',
        r.providers.length === 0 ? 'Sağlayıcılar yükleniyor...' : (active + ' / ' + r.providers.length + ' sağlayıcı aktif')));

      var acts = el('div', 'acts');
      var toggle = el('button', 'ghost', open[r.url] ? 'Gizle' : 'Liste');
      toggle.onclick = function () { open[r.url] = !open[r.url]; render(); };
      var del = el('button', 'del', 'Sil');
      del.onclick = function () { Android.removeRepo(r.url); render(); };
      acts.appendChild(toggle); acts.appendChild(del);

      top.appendChild(cb); top.appendChild(makeIcon(r, false)); top.appendChild(info); top.appendChild(acts);
      card.appendChild(top);

      if (open[r.url]) {
        var provs = el('div', 'provs');
        r.providers.forEach(function (p) {
          var line = el('label', 'prov');
          var pc = el('input');
          pc.type = 'checkbox';
          pc.checked = p.enabled;
          pc.onchange = function () { Android.setProviderEnabled(r.url, p.file, pc.checked); render(); };
          line.appendChild(pc);
          line.appendChild(makeIcon(r, true));
          line.appendChild(el('span', '', p.name));
          provs.appendChild(line);
        });
        card.appendChild(provs);
      }
      list.appendChild(card);
    });
  }

  function showDiag(text) {
    document.getElementById('term').style.display = 'block';
    document.getElementById('diag').textContent = text;
  }

  // Android.addRepo ağ isteği yaptığı için asenkron çağrılır; sonuç onAddResult ile döner.
  document.getElementById('add').onclick = function () {
    var v = document.getElementById('url').value;
    say('Kontrol ediliyor...', true);
    setStatus('KONTROL EDİLİYOR');
    Android.addRepo(v);
  };
  function onAddResult(ok, text) {
    say(text, ok);
    setStatus(ok ? 'HAZIR' : 'HATA');
    if (ok) document.getElementById('url').value = '';
    render();
  }

  render();
  var version = Android.version();
  document.getElementById('about').textContent += '  (Sürüm: ' + version + ')';

  document.getElementById('diagBtn').onclick = function () {
    setStatus('BAĞLANTI TEST EDİLİYOR');
    showDiag('Test ediliyor...');
    Android.diagnose();
  };
  function onDiag(text) {
    setStatus('HAZIR');
    showDiag(text);
  }

  var split = document.getElementById('split');
  split.checked = Android.getSplit();
  split.onchange = function () { Android.setSplit(split.checked); };

  document.getElementById('provBtn').onclick = function () {
    setStatus('SAĞLAYICILAR TEST EDİLİYOR');
    showDiag('Sağlayıcılar deneniyor, çok sayıda varsa birkaç dakika sürebilir...');
    Android.testProviders();
  };

  document.getElementById('keepBtn').onclick = function () {
    showDiag(Android.keepWorking());
    render();
  };

  // Ayar dişlisi: Hakkında penceresi
  var ov = document.getElementById('ov');
  document.getElementById('gear').onclick = function () { ov.style.display = 'flex'; };
  document.getElementById('close').onclick = function () { ov.style.display = 'none'; };
  ov.onclick = function (e) { if (e.target === ov) ov.style.display = 'none'; };
  document.getElementById('tg').onclick = function () { Android.openUrl('https://t.me/darknes_lord'); };

  Android.refresh(); // eski sürümde eklenmiş repoların adını ve sağlayıcı listesini tamamlar
</script>
</body>
</html>
"""
}
