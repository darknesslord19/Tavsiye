package com.example.sitebridge

/** Ayar ekranının HTML'i. Not: Kotlin ham dizgisinde '$' sorun çıkarır; JS içinde kullanılmadı. */
object SettingsHtml {
    const val PAGE = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  body { font-family: sans-serif; background:#0a0f1e; color:#e6ecff; margin:0; padding:20px 16px 32px; }
  h1 { font-size:20px; margin:0 0 4px; color:#fff; }
  .sub { font-size:12.5px; color:#8ea4d4; margin-bottom:16px; line-height:1.45; }
  .panel { background:linear-gradient(180deg,#111a30,#0e1627); border:1px solid #1d2c52; border-radius:20px; padding:14px; margin-bottom:14px; }
  .label { font-size:12px; letter-spacing:2px; color:#8ea4d4; font-weight:700; margin:0 0 10px; }
  input[type=text], input[type=url], textarea { width:100%; padding:12px; border-radius:12px; border:1px solid #243767; background:#0a1224; color:#e6ecff; font-size:14px; margin-bottom:8px; font-family:inherit; }
  textarea { min-height:70px; font-family:monospace; font-size:12px; }
  button { padding:11px 14px; border:0; border-radius:12px; background:#1f3a8a; color:#fff; font-size:14px; font-weight:600; }
  button:active { opacity:.8; }
  button.primary { background:linear-gradient(180deg,#2c52c9,#1f3a8a); width:100%; }
  button.ghost { background:#16224a; border:1px solid #243767; color:#bcd0ff; padding:8px 12px; }
  button.del { background:#7f1d1d; padding:8px 12px; }
  .row { display:flex; gap:8px; align-items:center; }
  .row input { margin-bottom:0; flex:1; min-width:0; }
  #msg, #smsg, #rmsg { min-height:18px; font-size:13px; margin-top:8px; line-height:1.4; }
  .ok { color:#34d399; } .err { color:#f87171; }
  .item { display:flex; align-items:center; gap:10px; padding:10px 2px; border-top:1px solid #1d2c52; }
  .item:first-of-type { border-top:0; }
  .info { flex:1; min-width:0; }
  .nm { font-size:15px; font-weight:700; color:#fff; }
  .u { font-size:11px; color:#6f84b5; word-break:break-all; margin-top:2px; }
  .tag { display:inline-block; font-size:10px; padding:2px 7px; border-radius:8px; background:#16224a; color:#7aa7ff; margin-left:6px; font-weight:600; }
  input[type=checkbox] { width:22px; height:22px; flex:none; accent-color:#3b82f6; }
  .empty { color:#6f84b5; text-align:center; padding:18px 0; font-size:13px; }
  details summary { font-size:12.5px; color:#7aa7ff; margin:2px 0 8px; }
  .hint { font-size:11.5px; color:#6f84b5; line-height:1.5; margin-bottom:8px; }
</style>
</head>
<body>
  <h1>Site Köprüsü</h1>
  <div class="sub">Film/dizi sitelerini CloudStream kaynağı olarak ekle. Siteleri elle yazabilir ya da GitHub'daki bir listeden çekebilirsin. Değişiklikten sonra CloudStream'i kapatıp açman gerekir.</div>

  <div class="panel">
    <div class="label">SİTE EKLE</div>
    <input id="sname" type="text" placeholder="Ad (isteğe bağlı)">
    <input id="surl" type="url" placeholder="https://ornek-film-sitesi.com">
    <details>
      <summary>Site kuralı (isteğe bağlı, JSON)</summary>
      <div class="hint">Site otomatik tanınmazsa seçici kuralı ver. Örn: {"movies":{"item":"article.film","link":"a@href","image":"img@data-src||img@src","title":"img@alt"},"search":"https://site.com/?s={q}"}</div>
      <textarea id="srules" placeholder='{"movies":{...}}'></textarea>
    </details>
    <button class="primary" id="addSite">Siteyi ekle</button>
    <div id="smsg"></div>
  </div>

  <div class="panel">
    <div class="label">GITHUB / JSON LİSTESİ</div>
    <div class="hint">GitHub dosya linki (github.com/.../blob/...), raw adresi, gist ya da repo adresi (içinde sites.json aranır) yapıştırabilirsin.</div>
    <div class="row">
      <input id="lurl" type="url" placeholder="https://raw.githubusercontent.com/.../sites.json">
      <button class="primary" id="addSrc" style="width:auto">Ekle</button>
    </div>
    <div id="msg"></div>
    <div id="sources"></div>
    <button class="ghost" id="refresh" style="margin-top:10px;width:100%">↻ Listeleri şimdi yenile</button>
    <div id="rmsg"></div>
  </div>

  <div class="panel">
    <div class="label">SİTELER</div>
    <div id="sites"></div>
  </div>

<script>
  function esc(s) { var d = document.createElement('div'); d.textContent = s == null ? '' : String(s); return d.innerHTML; }
  function setMsg(id, ok, t) { var e = document.getElementById(id); e.className = ok ? 'ok' : 'err'; e.textContent = t; }

  function render() {
    var st = JSON.parse(Android.getState());

    var sh = '';
    if (!st.sources.length) sh = '<div class="empty">Liste yok</div>';
    st.sources.forEach(function(u, i) {
      sh += '<div class="item"><div class="info"><div class="u" style="font-size:12px">' + esc(u) + '</div></div>' +
            '<button class="del" data-src="' + i + '">Sil</button></div>';
    });
    document.getElementById('sources').innerHTML = sh;
    var srcBtns = document.querySelectorAll('button[data-src]');
    for (var i = 0; i < srcBtns.length; i++) {
      srcBtns[i].onclick = (function(idx) { return function() { Android.removeSource(st.sources[idx]); render(); }; })(+srcBtns[i].getAttribute('data-src'));
    }

    var h = '';
    if (!st.sites.length) h = '<div class="empty">Henüz site yok. Yukarıdan site ekle ya da liste tanımla.</div>';
    st.sites.forEach(function(s, i) {
      h += '<div class="item"><input type="checkbox" data-en="' + i + '"' + (s.enabled ? ' checked' : '') + '>' +
           '<div class="info"><div class="nm">' + esc(s.name) +
           '<span class="tag">' + (s.source === 'user' ? 'sen' : 'liste') + '</span>' +
           (s.rules ? '<span class="tag">kural</span>' : '') + '</div>' +
           '<div class="u">' + esc(s.url) + '</div></div>' +
           (s.source === 'user' ? '<button class="del" data-del="' + i + '">Sil</button>' : '') + '</div>';
    });
    document.getElementById('sites').innerHTML = h;
    var cbs = document.querySelectorAll('input[data-en]');
    for (var j = 0; j < cbs.length; j++) {
      cbs[j].onchange = (function(el, idx) { return function() { Android.setEnabled(st.sites[idx].url, el.checked); }; })(cbs[j], +cbs[j].getAttribute('data-en'));
    }
    var dels = document.querySelectorAll('button[data-del]');
    for (var k = 0; k < dels.length; k++) {
      dels[k].onclick = (function(idx) { return function() { Android.removeSite(st.sites[idx].url); render(); }; })(+dels[k].getAttribute('data-del'));
    }
  }

  document.getElementById('addSite').onclick = function() {
    var err = Android.addSite(document.getElementById('sname').value, document.getElementById('surl').value, document.getElementById('srules').value);
    if (err) { setMsg('smsg', false, err); return; }
    setMsg('smsg', true, 'Eklendi. CloudStream\'i kapatıp açınca kaynak listesinde görünür.');
    document.getElementById('sname').value = ''; document.getElementById('surl').value = ''; document.getElementById('srules').value = '';
    render();
  };

  document.getElementById('addSrc').onclick = function() {
    var err = Android.addSource(document.getElementById('lurl').value);
    if (err) { setMsg('msg', false, err); return; }
    setMsg('msg', true, 'Liste eklendi. Şimdi yenile\'ye bas.');
    document.getElementById('lurl').value = '';
    render();
  };

  document.getElementById('refresh').onclick = function() {
    setMsg('rmsg', true, 'Listeler indiriliyor...');
    Android.refresh();
  };
  function onRefresh(ok, text) { setMsg('rmsg', ok, text); render(); }

  render();
</script>
</body>
</html>
"""
}
