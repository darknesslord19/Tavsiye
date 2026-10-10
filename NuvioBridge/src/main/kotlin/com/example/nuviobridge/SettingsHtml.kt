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
  #msg { min-height:18px; font-size:13px; margin-bottom:12px; }
  .ok { color:#2ecc71; } .err { color:#e74c3c; }
  .card { background:#1e1e1e; border-radius:10px; padding:12px; margin-bottom:8px; display:flex; align-items:center; gap:10px; }
  .url { flex:1; word-break:break-all; font-size:13px; }
  .empty { color:#777; text-align:center; padding:24px 0; }
</style>
</head>
<body>
  <h2>Nuvio Bridge</h2>
  <p class="sub">Nuvio repolarının manifest.json adresini ekle. Provider'lar Cloudstream içinde çalışır.</p>
  <div class="row">
    <input id="url" type="url" placeholder="https://.../manifest.json">
    <button id="add">Ekle</button>
  </div>
  <div id="msg"></div>
  <div id="list"></div>

<script>
  var msg = document.getElementById('msg');
  function say(text, ok) { msg.textContent = text; msg.className = ok ? 'ok' : 'err'; }

  function render() {
    var repos = JSON.parse(Android.getRepos());
    var list = document.getElementById('list');
    list.innerHTML = '';
    if (repos.length === 0) {
      var e = document.createElement('div');
      e.className = 'empty';
      e.textContent = 'Henüz repo eklenmedi';
      list.appendChild(e);
      return;
    }
    repos.forEach(function (r) {
      var card = document.createElement('div');
      card.className = 'card';

      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = r.enabled;
      cb.onchange = function () { Android.setEnabled(r.url, cb.checked); };

      var u = document.createElement('div');
      u.className = 'url';
      u.textContent = r.url;

      var del = document.createElement('button');
      del.className = 'del';
      del.textContent = 'Sil';
      del.onclick = function () { Android.removeRepo(r.url); render(); };

      card.appendChild(cb); card.appendChild(u); card.appendChild(del);
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
</script>
</body>
</html>
"""
}
