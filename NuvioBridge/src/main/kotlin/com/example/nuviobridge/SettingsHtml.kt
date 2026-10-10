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
  body { font-family: sans-serif; background:#121212; color:#eee; margin:0; padding:16px; }
  h2 { margin:0 0 4px; font-size:20px; }
  p.sub { margin:0 0 16px; color:#999; font-size:13px; }
  .row { display:flex; gap:8px; margin-bottom:8px; }
  input[type=url] { flex:1; padding:12px; border-radius:8px; border:1px solid #333; background:#1e1e1e; color:#eee; font-size:14px; }
  button { padding:12px 16px; border:0; border-radius:8px; background:#6c5ce7; color:#fff; font-size:14px; }
  button.del { background:#c0392b; padding:8px 12px; }
  button.ghost { background:#2d2d2d; padding:8px 12px; }
  #msg { min-height:18px; font-size:13px; margin-bottom:12px; }
  .ok { color:#2ecc71; } .err { color:#e74c3c; }
  .card { background:#1e1e1e; border-radius:10px; padding:12px; margin-bottom:10px; }
  .top { display:flex; align-items:center; gap:10px; }
  .info { flex:1; min-width:0; }
  .title { font-size:16px; font-weight:bold; }
  .url { font-size:11px; color:#888; word-break:break-all; }
  .count { font-size:12px; color:#aaa; margin-top:2px; }
  .provs { margin-top:10px; border-top:1px solid #2d2d2d; padding-top:6px; }
  .prov { display:flex; align-items:center; gap:10px; padding:6px 2px; font-size:14px; }
  .empty { color:#777; text-align:center; padding:24px 0; }
  input[type=checkbox] { width:20px; height:20px; }
</style>
</head>
<body>
  <h2 id="ttl">Nuvio Bridge</h2>
  <p class="sub">Nuvio repolarının manifest.json adresini ekle. Provider'lar Cloudstream içinde çalışır.</p>
  <div class="row">
    <input id="url" type="url" placeholder="https://.../manifest.json">
    <button id="add">Ekle</button>
  </div>
  <div id="msg"></div>
  <div class="row">
    <button class="ghost" id="diagBtn">Bağlantı testi</button>
    <button class="ghost" id="provBtn">Sağlayıcı testi</button>
  </div>
  <label class="prov" style="margin-bottom:10px">
    <input type="checkbox" id="split">
    <span>Her sağlayıcıyı ayrı kaynak olarak göster (değişince Cloudstream'i kapatıp aç)</span>
  </label>
  <pre id="diag" style="display:none;white-space:pre-wrap;font-size:12px;color:#bbb;background:#1e1e1e;border-radius:8px;padding:10px;margin:0 0 12px"></pre>
  <div id="list"></div>

<script>
  var msg = document.getElementById('msg');
  var open = {};
  function say(text, ok) { msg.textContent = text; msg.className = ok ? 'ok' : 'err'; }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
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

      var toggle = el('button', 'ghost', open[r.url] ? 'Gizle' : 'Liste');
      toggle.onclick = function () { open[r.url] = !open[r.url]; render(); };

      var del = el('button', 'del', 'Sil');
      del.onclick = function () { Android.removeRepo(r.url); render(); };

      top.appendChild(cb); top.appendChild(info); top.appendChild(toggle); top.appendChild(del);
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
          line.appendChild(el('span', '', p.name));
          provs.appendChild(line);
        });
        card.appendChild(provs);
      }
      list.appendChild(card);
    });
  }

  // Android.addRepo ağ isteği yaptığı için asenkron çağrılır; sonuç onAddResult ile döner.
  document.getElementById('add').onclick = function () {
    var v = document.getElementById('url').value;
    say('Kontrol ediliyor...', true);
    Android.addRepo(v);
  };
  function onAddResult(ok, text) {
    say(text, ok);
    if (ok) document.getElementById('url').value = '';
    render();
  }

  render();
  document.getElementById('ttl').textContent = 'Nuvio Bridge  (' + Android.version() + ')';

  document.getElementById('diagBtn').onclick = function () {
    var d = document.getElementById('diag');
    d.style.display = 'block';
    d.textContent = 'Test ediliyor...';
    Android.diagnose();
  };
  function onDiag(text) {
    document.getElementById('diag').textContent = text;
  }

  var split = document.getElementById('split');
  split.checked = Android.getSplit();
  split.onchange = function () { Android.setSplit(split.checked); };

  document.getElementById('provBtn').onclick = function () {
    var d = document.getElementById('diag');
    d.style.display = 'block';
    d.textContent = 'Sağlayıcılar deneniyor, bir dakikaya kadar sürebilir...';
    Android.testProviders();
  };

  Android.refresh(); // eski sürümde eklenmiş repoların adını ve sağlayıcı listesini tamamlar
</script>
</body>
</html>
"""
}
