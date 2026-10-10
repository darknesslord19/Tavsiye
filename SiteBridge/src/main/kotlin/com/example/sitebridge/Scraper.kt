package com.example.sitebridge

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// ---------- Modeller (CloudStream sınıflarıyla karışmasın diye "Site" önekli) ----------
data class SiteMovie(
    val title: String,
    val url: String,
    val poster: String,
    val direct: Boolean = false,
)

data class SiteCat(val name: String, val url: String)

data class SiteRow(val title: String, val items: List<SiteMovie>)

data class SiteEpisode(val title: String, val url: String, val season: Int, val number: Int)

data class PlayerSrc(val video: String?, val iframes: List<String>, val videos: List<String> = emptyList())

/**
 * MainActivity.java'daki tarama motorunun Kotlin karşılığı.
 * CloudStream'e bağımlı değildir: sadece HTML metni alır, veri döndürür.
 *
 * Site kuralları (rules) JSON'u MainActivity ile aynı biçimdedir:
 *  movies / sections / categories / next / player (+ yeni: search)
 *  Seçici ifadeleri: "css@oznitelik" | "@text" | "a@href||img@src" (yedekli)
 */
object Scraper {

    // ---------- Önbellekli regex'ler (MainActivity ile birebir) ----------
    private val A_TAG = Pattern.compile("(<a\\s[^>]*>)(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val IMG_TAG = Pattern.compile("<img\\s[^>]*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val CLEAN_TAGS = Pattern.compile("(?s)<[^>]+>")
    private val WS = Pattern.compile("\\s+")
    private val BG_URL = Pattern.compile(
        "background(?:-image)?\\s*:\\s*url\\s*\\(\\s*[\\\"']?([^\\\"')]+)", Pattern.CASE_INSENSITIVE
    )
    private val H_TAG = Pattern.compile("<h[1-4][^>]*>(.*?)</h[1-4]>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val FILM_PATH = Pattern.compile(".*?/film-[^/]+.*")
    private val OPT_TAG = Pattern.compile(
        "<option[^>]*value\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</option>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )
    private val NAV_BLOCK = Pattern.compile(
        "<(?:nav|ul|div)[^>]*(?:class|id)\\s*=\\s*[\"'][^\"']*" +
            "(?:menu|nav|genre|categor|kategori|tur)[^\"']*[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val CAT_PATH = Pattern.compile(
        "(?i).*/(?:kategori|kategoriler|category|categories|genre|genres|tur|turler|film-turu|film-turleri|kanal|kanallar|tv|tv-kanallari|canli-tv|canli-yayin|canli)(?:/|$).*"
    )
    private val LOOSE_EXT = Pattern.compile("(?i).*\\.(jpg|jpeg|png|gif|css|js|php|xml|txt|pdf)$")
    private val FULLHD_CAT = Pattern.compile(
        "https?://[^/]*fullhdfilmizlesene\\.now/filmizle/[^/?#]+/?(?:[?#].*)?"
    )
    private val LINK_NEXT = Pattern.compile("<link[^>]+rel=[\"']next[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
    private val NEXT_CLS = Pattern.compile(".*\\bnext\\b.*")
    private val EP_HREF = Pattern.compile(
        "href\\s*=\\s*[\\\"']([^\\\"']*(?:\\/)?(?:dublaj\\/|altyazi\\/)?(\\d+)-sezon-(\\d+)-bolum[^\\\"']*\\.html[^\\\"']*)[\\\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val EP_HREF_X = Pattern.compile(
        "href\\s*=\\s*[\"']([^\"']*/(?:bolum|episode|bolumler)/[^\"']*?-(\\d+)x(\\d+)(?:-[^\"'/]*)?/?)[\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val SERIES_LINK = Pattern.compile(
        "href\\s*=\\s*[\"']([^\"']*/(?:series|dizi|diziler)/[^\"'/?#]+/?)[\"']", Pattern.CASE_INSENSITIVE
    )
    private val EP_TITLE = Pattern.compile(
        "<(?:span|div|a|h[1-6])[^>]*>(.*?)</(?:span|div|a|h[1-6])>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )
    private val EP_SEASON_TXT = Pattern.compile(".*\\d+\\. Sezon.*")
    private val META_DESC = Pattern.compile(
        "<meta[^>]+name\\s*=\\s*[\\\"']description[\\\"'][^>]+content\\s*=\\s*[\\\"']([^\\\"']+)",
        Pattern.CASE_INSENSITIVE
    )
    private val SERIES_PATH = Pattern.compile(
        "/([^/]+)/(?:(?:dublaj|altyazi)/)?\\d+-sezon-\\d+-bolum(?:-[^/]+)?\\.html", Pattern.CASE_INSENSITIVE
    )
    private val META_TAG = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val TITLE_TAG = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val H1_TAG = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val M3U_ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"")
    private val STREAM_EXT = Pattern.compile(
        "\\.(m3u8|mp4|mkv|ts|avi|mov|webm)(\\?|\\||#|$)", Pattern.CASE_INSENSITIVE
    )
    private val URL_HINT = Pattern.compile(
        "(/|[?&=])(kategori|kategoriler|category|categories|cat|genre|genres|tur|turu|turler|turleri" +
            "|film-turleri|film-kategori|filmizle|film-izle|filmler|diziler|dizi|yerli-filmler|yabanci-filmler|kanal|kanallar|tv|tv-kanallari|canli-tv|canli-yayin|canli)([/?&=.]|$)",
        Pattern.CASE_INSENSITIVE
    )

    private val TR: Locale = Locale.forLanguageTag("tr-TR")

    private val GENRE_WORDS = arrayOf(
        "aksiyon", "macera", "komedi", "dram", "korku", "gerilim", "gizem", "romantik", "romantizm",
        "bilim kurgu", "bilimkurgu", "fantastik", "fantazi", "animasyon", "anime", "belgesel", "suç", "suc",
        "aile", "savaş", "savas", "western", "müzikal", "muzikal", "tarih", "biyografi", "spor", "polisiye",
        "psikolojik", "çocuk", "cocuk", "yerli", "yabancı", "yabanci", "hint", "asya", "kore", "türk", "turk",
        "netflix", "disney", "film", "dizi"
    )
    private val JUNK_EXACT = arrayOf(
        "ara", "rss", "sss", "home", "login", "giris", "giriş", "kayıt", "kayit", "tümü", "tumu", "more",
        "next", "prev", "ekle", "menu", "menü"
    )
    private val JUNK_HAS = arrayOf(
        "anasayfa", "ana sayfa", "iletişim", "iletisim", "contact", "hakkımızda", "hakkimizda", "dmca",
        "gizlilik", "privacy", "telegram", "twitter", "facebook", "instagram", "whatsapp", "discord",
        "sitemap", "kullanım", "register", "üye ol", "uye ol", "sonraki", "önceki", "reklam", "giriş yap",
        "kayıt ol"
    )

    private val attrPats = ConcurrentHashMap<String, Pattern>()

    // ====================================================
    // Küçük yardımcılar
    // ====================================================
    fun origin(u: String): String = try {
        val x = URL(u)
        x.protocol + "://" + x.host + (if (x.port > 0) ":" + x.port else "") + "/"
    } catch (e: Exception) {
        u
    }

    fun hostOf(u: String): String = try {
        URL(u).host.replace("www.", "")
    } catch (e: Exception) {
        ""
    }

    fun resolve(base: String, href0: String?): String? {
        return try {
            if (href0 == null) return null
            val href = href0.replace("&amp;", "&").trim()
            if (href.startsWith("//")) return URL(base).protocol + ":" + href
            URL(URL(base), href).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun attr(tag: String, name: String): String? {
        var p = attrPats[name]
        if (p == null) {
            p = Pattern.compile("(?<![\\w-])$name\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            attrPats[name] = p
        }
        val m = p!!.matcher(tag)
        return if (m.find()) m.group(1)!!.trim() else null
    }

    fun clean(s0: String?): String {
        if (s0 == null) return ""
        var s = CLEAN_TAGS.matcher(s0).replaceAll(" ")
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
            .replace("&#39;", "'").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        return WS.matcher(s).replaceAll(" ").trim()
    }

    private fun imageFromTag(img: String?, pageUrl: String): String? {
        if (img == null) return null
        val attrs = arrayOf(
            "data-src", "data-lazy-src", "data-original", "data-original-src", "data-image",
            "data-lazy", "data-fsrc", "data-url", "data-srcset", "src", "srcset"
        )
        for (a in attrs) {
            var v = attr(img, a)
            if (v == null || v.isEmpty() || v.startsWith("data:")) continue
            if (v.indexOf(',') >= 0) v = v.split(",")[0].trim()
            if (v.indexOf(' ') >= 0) v = v.trim().split(Regex("\\s+"))[0]
            val full = resolve(pageUrl, v)
            if (full != null && !full.startsWith("data:")) return full
        }
        return null
    }

    // ====================================================
    // Film kartları
    // ====================================================
    fun parseMovies(html: String, pageUrl: String, rules: JSONObject?): List<SiteMovie> {
        try {
            val mr = rules?.optJSONObject("movies")
            if (mr != null) {
                val rm = ruleMovies(doc(html, pageUrl), pageUrl, mr)
                if (rm.isNotEmpty()) return rm
            }
        } catch (ignored: Exception) {
        }

        val map = LinkedHashMap<String, SiteMovie>()
        val org0 = origin(pageUrl)
        val m = A_TAG.matcher(html)
        while (m.find()) {
            val open = m.group(1)!!
            val inner = m.group(2)!!
            val href = attr(open, "href")
            if (href == null || href.startsWith("#") || href.startsWith("javascript") ||
                href.startsWith("mailto") || href.startsWith("tel:")
            ) continue

            val im = IMG_TAG.matcher(inner)
            val img = if (im.find()) im.group() else null
            var poster = imageFromTag(img, pageUrl)
            if (poster == null) {
                val bg = BG_URL.matcher(inner)
                if (bg.find()) poster = resolve(pageUrl, bg.group(1)!!.trim())
            }
            if (poster == null) continue

            var title: String? = if (img == null) null else attr(img, "alt")
            if (title == null || title.length < 2) title = attr(open, "title")
            if (title == null || title.length < 2) title = clean(inner)
            title = clean(title)
            val full = resolve(pageUrl, href)
            if (full == null || full == pageUrl || full == org0 || title.length < 2) continue
            val lp = poster.lowercase()
            if (lp.contains("logo") || lp.contains("avatar") || lp.contains("banner") ||
                lp.endsWith(".svg") || lp.endsWith(".gif")
            ) continue
            if (!map.containsKey(full)) map[full] = SiteMovie(title, full, poster)
        }

        // Daha toleranslı ikinci geçiş: afiş ile bağlantı ayrı kardeş elemanlardaysa.
        if (map.size < 3) map.putAll(parseMoviesLoose(html, pageUrl))
        return ArrayList(map.values)
    }

    private fun parseMoviesLoose(html: String, pageUrl: String): Map<String, SiteMovie> {
        val out = LinkedHashMap<String, SiteMovie>()
        val org0 = origin(pageUrl)
        val a = A_TAG.matcher(html)
        while (a.find()) {
            val open = a.group(1)!!
            val inner = a.group(2)!!
            val href = attr(open, "href") ?: continue
            val full = resolve(pageUrl, href)
            if (full == null || full == pageUrl || full == org0) continue
            val path = full.lowercase()
            if (!(path.contains("/film/") || path.contains("/filmizle/film-") ||
                    FILM_PATH.matcher(path).matches())
            ) continue

            val from = maxOf(0, a.start() - 1200)
            val to = minOf(html.length, a.end() + 1800)
            val card = html.substring(from, to)
            val im = IMG_TAG.matcher(card)
            var poster: String? = null
            var imgTag: String? = null
            while (im.find()) {
                val candidate = imageFromTag(im.group(), pageUrl) ?: continue
                val low = candidate.lowercase()
                if (low.contains("logo") || low.contains("avatar") || low.contains("banner")) continue
                poster = candidate
                imgTag = im.group()
                break
            }
            if (poster == null) continue

            var title: String? = if (imgTag == null) null else attr(imgTag, "alt")
            if (title == null || title.length < 2) {
                val hm = H_TAG.matcher(card)
                if (hm.find()) title = clean(hm.group(1))
            }
            if (title == null || title.length < 2) title = clean(inner)
            if (title.length < 2) continue
            out[full] = SiteMovie(title, full, poster)
        }
        return out
    }

    // Ana sayfayı başlıklara (h1-h4) göre satırlara böler.
    fun parseSections(html: String, pageUrl: String, rules: JSONObject?): List<SiteRow> {
        val rows = ArrayList<SiteRow>()
        try {
            val sr = rules
            if (sr != null && sr.optJSONObject("sections") != null) {
                val rr = ruleSections(doc(html, pageUrl), pageUrl, sr)
                if (rr.isNotEmpty()) return rr
            }
        } catch (ignored: Exception) {
        }
        val all = parseMovies(html, pageUrl, rules)
        if (all.isEmpty()) return rows

        val hm = H_TAG.matcher(html)
        val marks = ArrayList<IntArray>()
        val titles = ArrayList<String>()
        while (hm.find()) {
            marks.add(intArrayOf(hm.start(), hm.end()))
            titles.add(clean(hm.group(1)))
        }
        var total = 0
        if (marks.isNotEmpty()) {
            addRow(rows, "Öne Çıkanlar", html.substring(0, marks[0][0]), pageUrl, rules)
            var i = 0
            while (i < marks.size && rows.size < 10) {
                val from = marks[i][1]
                val to = if (i + 1 < marks.size) marks[i + 1][0] else html.length
                var t = titles[i]
                if (t.isEmpty() || t.length > 40) t = "Filmler"
                addRow(rows, t, html.substring(from, to), pageUrl, rules)
                i++
            }
            for (r in rows) total += r.items.size
        }
        // Başlıklar kartların içindeyse tek satırda göster
        if (rows.isEmpty() || total < all.size / 2) {
            rows.clear()
            rows.add(SiteRow("Filmler", all.take(60)))
        }
        return rows
    }

    private fun addRow(rows: MutableList<SiteRow>, title: String, seg: String, pageUrl: String, rules: JSONObject?) {
        val items = parseMovies(seg, pageUrl, rules)
        if (items.size >= 3) rows.add(SiteRow(title, items))
    }

    // ====================================================
    // Kategoriler
    // ====================================================
    private fun isJunk(name: String): Boolean {
        val l = name.lowercase(TR)
        for (e in JUNK_EXACT) if (l == e) return true
        for (h in JUNK_HAS) if (l.contains(h)) return true
        return false
    }

    private fun isGenreText(name: String): Boolean {
        val l = name.lowercase(TR)
        for (w in GENRE_WORDS) if (w.contains(" ") && l.contains(w)) return true
        for (tok in l.split(Regex("[\\s/,&-]+"))) {
            for (w in GENRE_WORDS) {
                if (!w.contains(" ") && (tok == w || (w.length >= 5 && tok.startsWith(w)))) return true
            }
        }
        return false
    }

    private fun addCat(
        href0: String?, text: String, pageUrl: String, host: String, org0: String,
        movieUrls: Set<String>, map: MutableMap<String, SiteCat>, names: MutableSet<String>, loose: Boolean,
    ) {
        if (map.size >= 80 || href0 == null) return
        val href = href0.trim()
        if (href.startsWith("#") || href.startsWith("javascript") || href.startsWith("mailto") ||
            href.startsWith("tel:")
        ) return
        val full = resolve(pageUrl, href)
        if (full == null || hostOf(full) != host) return
        if (full == pageUrl || full == org0 || movieUrls.contains(full) || map.containsKey(full)) return
        val name = clean(text)
        if (name.length < 2 || name.length > 28 || name.matches(Regex("[\\d\\s.]+")) || isJunk(name)) return
        val key = name.lowercase(TR)
        if (names.contains(key)) return

        var ok = isGenreText(name) || URL_HINT.matcher(full).find()
        if (!ok) {
            ok = try {
                CAT_PATH.matcher(URL(full).path).matches()
            } catch (e: Exception) {
                false
            }
        }
        if (!ok && loose) {
            ok = try {
                val path = URL(full).path
                path.length > 1 && path.split("/").size <= 4 && !LOOSE_EXT.matcher(path).matches()
            } catch (e: Exception) {
                false
            }
        }
        if (!ok) return
        map[full] = SiteCat(name, full)
        names.add(key)
    }

    private fun collectCats(
        html: String, pageUrl: String, host: String, org0: String, movieUrls: Set<String>,
        map: MutableMap<String, SiteCat>, names: MutableSet<String>, loose: Boolean,
    ) {
        val m = A_TAG.matcher(html)
        while (m.find() && map.size < 80) {
            val label = IMG_TAG.matcher(m.group(2)!!).replaceAll(" ")
            addCat(attr(m.group(1)!!, "href"), label, pageUrl, host, org0, movieUrls, map, names, loose)
        }
    }

    fun parseCategories(html: String, pageUrl: String, rules: JSONObject?): List<SiteCat> {
        if (hostOf(pageUrl).contains("fullhdfilmizlesene.now")) {
            val special = LinkedHashMap<String, SiteCat>()
            val sn = HashSet<String>()
            val sm = A_TAG.matcher(html)
            while (sm.find() && special.size < 80) {
                val href = attr(sm.group(1)!!, "href")
                val full = resolve(pageUrl, href) ?: continue
                if (!FULLHD_CAT.matcher(full.lowercase()).matches()) continue
                var name = clean(IMG_TAG.matcher(sm.group(2)!!).replaceAll(" "))
                if (name.length < 2) name = clean(sm.group(2))
                if (name.length < 2 || name.length > 40) continue
                val key = name.lowercase(TR)
                if (sn.add(key)) special[full] = SiteCat(name, full)
            }
            if (special.isNotEmpty()) return ArrayList(special.values)
        }
        try {
            val cr = rules?.optJSONObject("categories")
            if (cr != null) {
                val rc = ruleCats(doc(html, pageUrl), pageUrl, cr)
                if (rc.isNotEmpty()) return rc
            }
        } catch (ignored: Exception) {
        }
        val map = LinkedHashMap<String, SiteCat>()
        val names = HashSet<String>()
        val movieUrls = HashSet<String>()
        for (mv in parseMovies(html, pageUrl, rules)) movieUrls.add(mv.url)
        val host = hostOf(pageUrl)
        val org0 = origin(pageUrl)

        // 1) Normal bağlantılar
        collectCats(html, pageUrl, host, org0, movieUrls, map, names, false)

        // 2) Açılır liste (<select><option value=adres>)
        val om = OPT_TAG.matcher(html)
        while (om.find() && map.size < 80) {
            addCat(om.group(1), om.group(2)!!, pageUrl, host, org0, movieUrls, map, names, false)
        }

        // 3) Yedek: menü / nav blokları
        if (map.size < 3) {
            val bm = NAV_BLOCK.matcher(html)
            while (bm.find() && map.size < 80) {
                val from = bm.end()
                val seg = html.substring(from, minOf(html.length, from + 5000))
                collectCats(seg, pageUrl, host, org0, movieUrls, map, names, true)
            }
        }
        return ArrayList(map.values)
    }

    fun parseNext(html: String, pageUrl: String, rules: JSONObject?): String? {
        try {
            val ne = rules?.optString("next", "")
            if (!ne.isNullOrEmpty()) {
                val nv = extract(doc(html, pageUrl), ne)
                if (nv != null) {
                    val nf = resolve(pageUrl, nv)
                    if (nf != null) return nf
                }
            }
        } catch (ignored: Exception) {
        }
        val m = LINK_NEXT.matcher(html)
        if (m.find()) {
            val h = attr(m.group(), "href")
            if (h != null) return resolve(pageUrl, h)
        }
        val a = A_TAG.matcher(html)
        while (a.find()) {
            val open = a.group(1)!!
            val cls = attr(open, "class")
            val rel = attr(open, "rel")
            val isNext = (cls != null && NEXT_CLS.matcher(cls.lowercase()).matches()) ||
                (rel != null && rel.lowercase().contains("next"))
            if (isNext) {
                val h = attr(open, "href")
                if (h != null && !h.startsWith("#")) return resolve(pageUrl, h)
            }
        }
        return null
    }

    // ====================================================
    // Dizi / bölüm
    // ====================================================
    fun seriesUrlFor(url: String?): String {
        if (url == null) return ""
        try {
            val u = java.net.URI(url)
            val path = u.path
            if (path != null) {
                val m = SERIES_PATH.matcher(path)
                if (m.find()) return u.scheme + "://" + u.authority + "/diziler/" + m.group(1) + ".html"
                if (path.contains("/diziler/")) return url
            }
        } catch (ignored: Exception) {
        }
        return url
    }

    fun looksLikeSeries(url: String): Boolean {
        val l = url.lowercase()
        return l.contains("/dizi/") || l.contains("/diziler/") || l.contains("/series/") ||
            l.contains("-sezon-") || l.contains("/bolum/") || l.contains("/episode/")
    }

    /** Sayfadaki bir dizi bağlantısı (/series/..., /dizi/...) adaylarını verir. */
    fun seriesLinks(html: String, pageUrl: String): List<String> {
        val out = ArrayList<String>()
        val sm = SERIES_LINK.matcher(html)
        while (sm.find()) {
            val su = resolve(pageUrl, sm.group(1)) ?: continue
            if (su != pageUrl && !out.contains(su)) out.add(su)
        }
        return out
    }

    fun parseEpisodes(html: String?, pageUrl: String): List<SiteEpisode> {
        val out = ArrayList<SiteEpisode>()
        if (html == null) return out
        val seen = HashSet<String>()
        for (pass in 0..1) {
            if (pass == 1 && out.isNotEmpty()) break
            val m = (if (pass == 0) EP_HREF else EP_HREF_X).matcher(html)
            while (m.find()) {
                val href = m.group(1)!!
                val season = m.group(2)!!.toIntOrNull() ?: continue
                val number = m.group(3)!!.toIntOrNull() ?: continue
                val full = resolve(pageUrl, href)
                val key = "$season-$number"
                if (full == null || seen.contains(key)) continue
                seen.add(key)
                val a = maxOf(0, m.start() - 500)
                val b = minOf(html.length, m.end() + 500)
                val around = html.substring(a, b)
                var title: String? = null
                // Önce bağlantının kendi içindeki metin (komşu bölümlerin başlığı karışmasın)
                val aStart = html.lastIndexOf("<a", m.start())
                val aEnd = html.indexOf("</a>", m.end())
                if (aStart >= 0 && aEnd > aStart && aEnd - aStart < 600) {
                    val own = clean(html.substring(aStart, aEnd))
                    if (own.length >= 2 && !EP_SEASON_TXT.matcher(own).matches()) title = own
                }
                if (title == null) {
                    val tm = EP_TITLE.matcher(around)
                    while (tm.find()) {
                        val t = clean(tm.group(1))
                        if (t.length >= 2 && !EP_SEASON_TXT.matcher(t).matches()) title = t
                    }
                }
                if (title == null || title.length < 2) title = "$season. Sezon $number. Bölüm"
                out.add(SiteEpisode(title, full, season, number))
            }
        }
        out.sortWith(compareBy<SiteEpisode>({ it.season }, { it.number }))
        return out
    }

    fun metaDescription(html: String?): String? {
        if (html == null) return null
        val m = META_DESC.matcher(html)
        return if (m.find()) clean(m.group(1)) else null
    }

    private fun metaContent(html: String, key: String): String? {
        val m = META_TAG.matcher(html)
        while (m.find()) {
            val t = m.group()
            val p = attr(t, "property") ?: attr(t, "name")
            if (p != null && p.equals(key, ignoreCase = true)) {
                val c = attr(t, "content")
                if (!c.isNullOrBlank()) return clean(c)
            }
        }
        return null
    }

    fun pageTitle(html: String?): String? {
        if (html == null) return null
        var t = metaContent(html, "og:title")
        if (t.isNullOrBlank()) {
            val h = H1_TAG.matcher(html)
            if (h.find()) t = clean(h.group(1))
        }
        if (t.isNullOrBlank()) {
            val h = TITLE_TAG.matcher(html)
            if (h.find()) t = clean(h.group(1))
        }
        if (t.isNullOrBlank()) return null
        // "Film Adı | Site" gibi site eklerini at
        for (sep in arrayOf(" | ", " » ", " – ", " — ")) {
            val i = t!!.indexOf(sep)
            if (i > 2) t = t.substring(0, i)
        }
        return t!!.trim()
    }

    fun pagePoster(html: String?, pageUrl: String): String? {
        if (html == null) return null
        val p = metaContent(html, "og:image") ?: return null
        return resolve(pageUrl, p)
    }

    // ====================================================
    // Oynatıcı: sayfadaki video / iframe adayları
    // ====================================================
    private val PACKED = Pattern.compile(
        "}\\('(.*?)',\\s*(\\d+),\\s*(\\d+),\\s*'(.*?)'\\.split\\('\\|'\\)", Pattern.DOTALL
    )
    private val B64_BLOB = Pattern.compile("[\"'](PGlmcmFt[A-Za-z0-9+/=_-]{16,}|aHR0c[A-Za-z0-9+/=_-]{12,})[\"']")
    private val ATOB = Pattern.compile("atob\\(\\s*[\"']([A-Za-z0-9+/=_-]{16,})[\"']\\s*\\)")
    private val VIDEO_ANY = Pattern.compile(
        "https?://[^\"'\\s<>\\\\]+?\\.(?:m3u8|mp4|mpd)(?:\\?[^\"'\\s<>\\\\]*)?", Pattern.CASE_INSENSITIVE
    )
    private val VIDEO_KEY = Pattern.compile(
        "[\"']?(?:file|src|source|url|video_url|videoUrl|contentUrl|hls|hlsUrl|stream|streamUrl|playlist)[\"']?\\s*[:=]\\s*" +
            "[\"']((?:https?:)?//[^\"'\\s<>]+)[\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val FRAME_TAG = Pattern.compile("<(?:iframe|embed)\\s[^>]*>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val DATA_EMBED = Pattern.compile(
        "data-(?:embed|iframe|video|player|link|href|url|src|frame)[\\w-]*\\s*=\\s*[\"']((?:https?:)?//[^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val JSON_FRAME = Pattern.compile(
        "[\"'](?:embed_url|embedUrl|iframe|iframe_url|iframeUrl|player_url|playerUrl|embed)[\"']\\s*[:=]\\s*[\"']((?:https?:)?//[^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val OPTION_URL = Pattern.compile(
        "<option[^>]+value\\s*=\\s*[\"']((?:https?:)?//[^\"']+)[\"']", Pattern.CASE_INSENSITIVE
    )
    private val ALT_SRC = Pattern.compile(
        "href\\s*=\\s*[\"']([^\"']*[?&](?:kaynak|source|player|alternatif|alt|part|server|sunucu|sid)=[^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    )
    private val FRAME_HINTS = arrayOf(
        "embed", "player", "/e/", "/v/", "iframe", "vid", "stream", "play", "watch", "rapid", "dood", "mixdrop",
        "moly", "filemoon", "uqload", "streamtape", "upstream", "sibnet", "ok.ru", "vk.com", "fembed", "voe",
        "pixel", "closeload", "hdfilm", "cdn"
    )
    private val BAD_FRAME = arrayOf(
        "facebook", "google", "twitter", "youtube", "youtu.be", "/ads", "about:", "disqus", "recaptcha",
        "gstatic", "histats", "instagram", "whatsapp", "telegram", "t.me/"
    )
    private val BAD_MEDIA = arrayOf(
        "doubleclick", "googlesyndication", "/ads/", "adserver", "preroll", "imasdk", "/banner", "analytics",
        "facebook.com", "googletagmanager"
    )
    private val ASSET_EXT = Pattern.compile("(?i)\\.(?:jpe?g|png|gif|webp|svg|css|js|ico|woff2?|ttf)(?:\\?.*)?$")

    // Dean Edwards "packer" ile sıkıştırılmış JS'in açılması
    private fun digit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'z' -> c - 'a' + 10
        in 'A'..'Z' -> c - 'A' + 36
        else -> -1
    }

    private fun unpackOne(payload: String, radix: Int, dict: List<String>): String {
        return Regex("\\b\\w+\\b").replace(payload) { m ->
            val word = m.value
            var idx = 0
            var ok = radix in 2..62
            if (ok) {
                for (ch in word) {
                    val d = digit(ch)
                    if (d < 0 || d >= radix) {
                        ok = false
                        break
                    }
                    idx = idx * radix + d
                }
            }
            if (ok && idx < dict.size && dict[idx].isNotEmpty()) dict[idx] else word
        }
    }

    private fun b64decode(s0: String): String? {
        return try {
            val tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val s = s0.replace('-', '+').replace('_', '/').trimEnd('=')
            val out = java.io.ByteArrayOutputStream()
            var buf = 0
            var bits = 0
            for (c in s) {
                val v = tbl.indexOf(c)
                if (v < 0) return null
                buf = (buf shl 6) or v
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buf shr bits) and 0xFF)
                }
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** Sayfa + açılmış packed JS + çözülmüş base64 parçaları (video/iframe aramak için tek metin). */
    fun expand(html: String): String {
        val sb = StringBuilder(html.replace("\\/", "/"))
        try {
            val m = PACKED.matcher(html)
            var n = 0
            while (m.find() && n < 6) {
                n++
                try {
                    val payload = m.group(1)!!.replace("\\'", "'").replace("\\\\", "\\")
                    val dict = m.group(4)!!.split("|")
                    sb.append('\n').append(unpackOne(payload, m.group(2)!!.toInt(), dict).replace("\\/", "/"))
                } catch (ignored: Exception) {
                }
            }
            for (p in arrayOf(B64_BLOB, ATOB)) {
                val bm = p.matcher(html)
                var n2 = 0
                while (bm.find() && n2 < 20) {
                    n2++
                    val dec = b64decode(bm.group(1)!!) ?: continue
                    if (dec.contains("<") || dec.startsWith("http")) sb.append('\n').append(dec.replace("\\/", "/"))
                }
            }
        } catch (ignored: Exception) {
        }
        return sb.toString()
    }

    private fun badMedia(u: String): Boolean {
        val l = u.lowercase()
        return BAD_MEDIA.any { l.contains(it) }
    }

    fun extractVideos(exp: String, pageUrl: String): List<String> {
        val out = LinkedHashSet<String>()
        val vm = VIDEO_ANY.matcher(exp)
        while (vm.find()) if (!badMedia(vm.group())) out.add(vm.group())
        val km = VIDEO_KEY.matcher(exp)
        while (km.find()) {
            val raw = km.group(1)!!
            val l = raw.lowercase()
            if (!(l.contains(".m3u8") || l.contains(".mp4") || l.contains(".mpd"))) continue
            val full = resolve(pageUrl, raw) ?: continue
            if (!badMedia(full) && !ASSET_EXT.matcher(full).find()) out.add(full)
        }
        return ArrayList(out)
    }

    private fun goodFrame(u: String): Boolean {
        val l = u.lowercase()
        if (BAD_FRAME.any { l.contains(it) }) return false
        return !ASSET_EXT.matcher(l).find()
    }

    fun extractIframes(exp: String, pageUrl: String): List<String> {
        val out = LinkedHashSet<String>()
        fun add(raw: String?) {
            if (raw.isNullOrBlank()) return
            val full = resolve(pageUrl, raw.trim()) ?: return
            if (full != pageUrl && goodFrame(full)) out.add(full)
        }
        val ft = FRAME_TAG.matcher(exp)
        while (ft.find()) {
            val tag = ft.group()
            add(attr(tag, "src").takeUnless { it.isNullOrBlank() || it.startsWith("about:") }
                ?: attr(tag, "data-src") ?: attr(tag, "data-lazy-src") ?: attr(tag, "data-litespeed-src")
                ?: attr(tag, "data-embed-src"))
        }
        val dm = DATA_EMBED.matcher(exp)
        while (dm.find()) {
            val v = dm.group(1)!!
            val l = v.lowercase()
            if (FRAME_HINTS.any { l.contains(it) }) add(v)
        }
        val jm = JSON_FRAME.matcher(exp)
        while (jm.find()) add(jm.group(1))
        val om = OPTION_URL.matcher(exp)
        while (om.find()) {
            val v = om.group(1)!!
            val l = v.lowercase()
            if (FRAME_HINTS.any { l.contains(it) }) add(v)
        }
        return ArrayList(out)
    }

    /** Sayfadaki alternatif kaynak sayfaları (?kaynak=2, ?player=1 ...). Aynı siteden, en çok 3. */
    fun altSourcePages(html: String, pageUrl: String): List<String> {
        val out = LinkedHashSet<String>()
        val host = hostOf(pageUrl)
        val m = ALT_SRC.matcher(html)
        while (m.find() && out.size < 3) {
            val full = resolve(pageUrl, m.group(1)) ?: continue
            if (full != pageUrl && hostOf(full) == host) out.add(full)
        }
        return ArrayList(out)
    }

    fun findPlayer(html: String, pageUrl: String, rules: JSONObject?): PlayerSrc {
        val exp = expand(html)
        val videos = ArrayList(extractVideos(exp, pageUrl))
        val iframes = ArrayList(extractIframes(exp, pageUrl))

        try {
            val pl = rules?.optJSONObject("player")
            if (pl != null) {
                val d = doc(html, pageUrl)
                var rv = extract(d, pl.optString("video", ""))
                if (rv == null && pl.optString("videoRegex", "").isNotEmpty()) {
                    val mm = Pattern.compile(pl.optString("videoRegex"), Pattern.DOTALL).matcher(exp)
                    if (mm.find()) rv = if (mm.groupCount() >= 1) mm.group(1) else mm.group()
                }
                if (rv != null) rv = resolve(pageUrl, rv)
                var rf = extract(d, pl.optString("iframe", ""))
                if (rf != null) rf = resolve(pageUrl, rf)
                if (rv != null) {
                    videos.remove(rv)
                    videos.add(0, rv)
                }
                if (rf != null) {
                    iframes.remove(rf)
                    iframes.add(0, rf)
                    if (rv == null) videos.clear()
                }
            }
        } catch (ignored: Exception) {
        }
        return PlayerSrc(videos.firstOrNull(), iframes, videos)
    }

    /** Herhangi bir sayfada (iframe içeriği vb.) video adresleri arar. */
    fun findVideos(html: String, pageUrl: String): List<String> = extractVideos(expand(html), pageUrl)

    fun findVideoIn(html: String): String? = findVideos(html, "https://x.invalid/").firstOrNull()

    // ====================================================
    // M3U / M3U8 listeleri
    // ====================================================
    fun isM3u(t: String?): Boolean {
        if (t == null) return false
        val head = t.substring(0, minOf(t.length, 4000))
        return head.trim().startsWith("#EXTM3U") || head.contains("#EXTINF")
    }

    fun isDirectUrl(u: String?): Boolean = u != null && (u.contains("#sb=") || STREAM_EXT.matcher(u).find())

    fun parseM3u(text: String): LinkedHashMap<String, MutableList<SiteMovie>> {
        val out = LinkedHashMap<String, MutableList<SiteMovie>>()
        var title: String? = null
        var logo: String? = null
        var group: String? = null
        var ua: String? = null
        var ref: String? = null
        var extGrp: String? = null
        var pending = false
        for (raw in text.split(Regex("\\r?\\n"))) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                pending = true
                logo = null
                group = null
                var name: String? = null
                val am = M3U_ATTR.matcher(line)
                while (am.find()) {
                    when (am.group(1)!!.lowercase(Locale.ROOT)) {
                        "group-title" -> group = am.group(2)!!.trim()
                        "tvg-logo", "logo" -> logo = am.group(2)!!.trim()
                        "tvg-name" -> name = am.group(2)!!.trim()
                    }
                }
                val comma = line.lastIndexOf(',')
                title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (title.isEmpty()) title = name ?: ""
            } else if (line.startsWith("#EXTGRP:")) {
                extGrp = line.substring(8).trim()
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val o = line.substring(11)
                val eq = o.indexOf('=')
                if (eq > 0) {
                    val k = o.substring(0, eq).lowercase(Locale.ROOT)
                    val v = o.substring(eq + 1).trim()
                    if (k == "http-user-agent") ua = v
                    else if (k == "http-referrer" || k == "http-referer") ref = v
                }
            } else if (line.startsWith("#")) {
                continue
            } else if (pending || title != null) {
                if (title == null || title.isEmpty()) title = line
                var url = line
                if (!url.contains("|") && (ua != null || ref != null)) {
                    val sb = StringBuilder(url).append('|')
                    if (ua != null) sb.append("User-Agent=").append(ua)
                    if (ref != null) sb.append(if (ua != null) "&" else "").append("Referer=").append(ref)
                    url = sb.toString()
                }
                val g = if (!group.isNullOrEmpty()) group
                else if (!extGrp.isNullOrEmpty()) extGrp else "Diğer"
                out.getOrPut(g!!) { ArrayList() }.add(SiteMovie(title, url, logo ?: "", true))
                title = null
                logo = null
                group = null
                ua = null
                ref = null
                pending = false
            }
        }
        return out
    }

    /** "Link|User-Agent=x&Referer=y" -> link; başlıklar [into]'ya yazılır. */
    fun splitStreamHeaders(url: String, into: MutableMap<String, String>): String {
        val bar = url.indexOf('|')
        if (bar < 0) return url
        for (kv in url.substring(bar + 1).split("&")) {
            val eq = kv.indexOf('=')
            if (eq > 0) into[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim()
        }
        return url.substring(0, bar)
    }

    // M3U öğesinin başlığı/afişi, CloudStream'e giden adresin '#sb=' parçasında taşınır
    // (parça sunucuya gönderilmez, yayın bozulmaz).
    fun encodeDirect(m: SiteMovie): String {
        val e = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return m.url + "#sb=" + e(m.title) + (if (m.poster.isNotEmpty()) "&p=" + e(m.poster) else "")
    }

    /** (yayın adresi, başlık, afiş) */
    fun decodeDirect(id: String): Triple<String, String, String?> {
        val i = id.lastIndexOf("#sb=")
        if (i < 0) return Triple(id, id.substringAfterLast('/').substringBefore('?'), null)
        val dec = { s: String -> java.net.URLDecoder.decode(s, "UTF-8") }
        val parts = id.substring(i + 4).split("&p=")
        return Triple(id.substring(0, i), dec(parts[0]), if (parts.size > 1) dec(parts[1]) else null)
    }

    // ====================================================
    // Site kuralları motoru (Jsoup üzerinde)
    // ====================================================
    @Volatile private var docKey: String? = null
    @Volatile private var docVal: Document? = null

    @Synchronized
    private fun doc(html: String, pageUrl: String): Document {
        if (html !== docKey) {
            docVal = Jsoup.parse(html, pageUrl)
            docKey = html
        }
        return docVal!!
    }

    private fun safeSelect(scope: Element, css: String): Elements = try {
        scope.select(css)
    } catch (e: Exception) {
        Elements()
    }

    /** "secici@oznitelik" | "@text" | "secici@text" | "a@href||b@href" (yedekli) */
    fun extract(scope: Element, expr: String?): String? {
        if (expr.isNullOrBlank()) return null
        for (alt0 in expr.split("||")) {
            val alt = alt0.trim()
            if (alt.isEmpty()) continue
            var css = alt
            var at = "text"
            val idx = alt.lastIndexOf('@')
            if (idx >= 0 && alt.substring(idx + 1).matches(Regex("[-\\w:]+"))) {
                css = alt.substring(0, idx).trim()
                at = alt.substring(idx + 1).lowercase(Locale.ROOT)
            }
            val target: Element = if (css.isNotEmpty()) {
                safeSelect(scope, css).firstOrNull() ?: continue
            } else scope
            var v: String? = when (at) {
                "text" -> target.text()
                "owntext" -> target.ownText()
                else -> if (target.hasAttr(at)) target.attr(at) else null
            }
            if (v == null) continue
            v = v.trim()
            if (v.isEmpty()) continue
            if ((at == "src" || at.startsWith("data-")) && v.startsWith("data:")) continue
            return v
        }
        return null
    }

    private fun ruleMovies(scope: Element, pageUrl: String, r: JSONObject): List<SiteMovie> {
        val out = ArrayList<SiteMovie>()
        val seen = HashSet<String>()
        for (it in safeSelect(scope, r.optString("item", "article"))) {
            var link = extract(it, r.optString("link", "a@href"))
            if (link == null && it.tagName() == "a") link = it.attr("href")
            if (link.isNullOrEmpty()) continue
            val img = extract(it, r.optString("image", "img@data-src||img@data-lazy-src||img@src"))
            val title = extract(it, r.optString("title", "a@title||img@alt||@text"))
            if (title == null || title.length < 2 || img == null) continue
            val full = resolve(pageUrl, link)
            val pfull = resolve(pageUrl, img)
            if (full == null || pfull == null || !seen.add(full)) continue
            out.add(SiteMovie(title, full, pfull))
        }
        return out
    }

    private fun ruleCats(d: Element, pageUrl: String, r: JSONObject): List<SiteCat> {
        val out = ArrayList<SiteCat>()
        val seenU = HashSet<String>()
        val seenN = HashSet<String>()
        val limit = r.optInt("limit", 40)
        for (it in safeSelect(d, r.optString("item", "nav a"))) {
            val name = extract(it, r.optString("name", "@text"))
            var link = extract(it, r.optString("link", "@href"))
            if (link == null && it.tagName() == "a") link = it.attr("href")
            if (name == null || link == null || name.length < 2 || name.length > 40) continue
            val full = resolve(pageUrl, link)
            val key = name.lowercase(TR)
            if (full == null || full == pageUrl || seenU.contains(full) || seenN.contains(key)) continue
            seenU.add(full)
            seenN.add(key)
            out.add(SiteCat(name, full))
            if (out.size >= limit) break
        }
        return out
    }

    private fun ruleSections(d: Element, pageUrl: String, sr: JSONObject): List<SiteRow> {
        val rows = ArrayList<SiteRow>()
        val sec = sr.optJSONObject("sections") ?: return rows
        val mr = sr.optJSONObject("movies") ?: return rows
        for (s in safeSelect(d, sec.optString("item", "section"))) {
            val items = ruleMovies(s, pageUrl, mr)
            if (items.size < 3) continue
            val title = extract(s, sec.optString("title", "h2@text||h3@text"))
            rows.add(SiteRow(if (title == null || title.length > 40) "Filmler" else title, items))
            if (rows.size >= 12) break
        }
        return rows
    }
}
