package com.darkneslord.iptv

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Darknes Lord yönetim paneli: liste kartları, aç/kapat, kategori göz ikonu, düzenle, sil.
 * Tüm arayüz kodla çizilir; kaynak (res) dosyası gerektirmez.
 */
class DarknesSettingsFragment : DialogFragment() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var listBox: LinearLayout
    private lateinit var statLists: TextView
    private lateinit var statActive: TextView
    private lateinit var statChannels: TextView

    private val expanded = mutableSetOf<String>()
    private val checking = mutableSetOf<String>()

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // ------------------------------------------------------------------ yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_NoActionBar)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(MATCH, MATCH)
            setBackgroundDrawable(ColorDrawable(Theme.BG))
            statusBarColor = Theme.BG
            navigationBarColor = Theme.BG
        }
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val statusBar = ctx.resources.getIdentifier("status_bar_height", "dimen", "android").let {
            if (it > 0) ctx.resources.getDimensionPixelSize(it) else ctx.dp(24)
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            setPadding(0, statusBar, 0, 0)
        }

        // ---- Başlık: vektör logo + isim + ekle / kapat
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(18), ctx.dp(14), ctx.dp(10), ctx.dp(10))
        }
        header.addView(LogoView(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(50), ctx.dp(50)) })
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = ctx.dp(14) }
        }
        titles.addView(label("Darknes Lord", 22f, Theme.TEXT, bold = true))
        titles.addView(label("IPTV yönetim merkezi", 12f, Theme.MUTED))
        header.addView(titles)
        header.addView(IconView(ctx, IconKind.CLOSE, Theme.MUTED).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44))
            setOnClickListener { dismiss() }
        })
        root.addView(header)

        // ---- İstatistik kartları
        val stats = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ctx.dp(14), 0, ctx.dp(14), ctx.dp(6))
        }
        statLists = statCard(stats, "Liste")
        statActive = statCard(stats, "Aktif")
        statChannels = statCard(stats, "Kanal")
        root.addView(stats)

        // ---- Yeni liste ekle butonu
        val add = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundBg(ctx, Theme.ACCENT, 16)
            isClickable = true
            setPadding(ctx.dp(16), ctx.dp(13), ctx.dp(16), ctx.dp(13))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(ctx.dp(18), ctx.dp(8), ctx.dp(18), ctx.dp(8))
            }
            setOnClickListener { showEditor(null) }
        }
        add.addView(IconView(ctx, IconKind.PLUS, Color.WHITE).apply {
            background = null
            layoutParams = LinearLayout.LayoutParams(ctx.dp(26), ctx.dp(26))
        })
        add.addView(label("Yeni IPTV listesi ekle", 15f, Color.WHITE, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ctx.dp(6) }
        })
        root.addView(add)

        // ---- Kart listesi
        listBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(28))
        }
        root.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))

        render()
        return root
    }

    // ------------------------------------------------------------------ küçük yardımcılar

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

    private fun chip(text: String, color: Int): TextView {
        val ctx = requireContext()
        return label(text, 11f, color, bold = true).apply {
            background = roundBg(ctx, withAlpha(color, 34), 10)
            setPadding(ctx.dp(9), ctx.dp(5), ctx.dp(9), ctx.dp(5))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = ctx.dp(6) }
        }
    }

    private fun statCard(parent: LinearLayout, title: String): TextView {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundBg(ctx, Theme.CARD, 16, Theme.STROKE, 1)
            setPadding(0, ctx.dp(12), 0, ctx.dp(12))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) }
        }
        val value = label("0", 20f, Theme.TEXT, bold = true)
        box.addView(value)
        box.addView(label(title, 11f, Theme.MUTED).apply { setPadding(0, ctx.dp(4), 0, 0) })
        parent.addView(box)
        return value
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun fmt(n: Int): String = NumberFormat.getInstance().format(n.toLong())

    private fun timeText(ms: Long): String =
        if (ms <= 0) "—" else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms))

    // ------------------------------------------------------------------ ana liste

    private fun render() {
        if (context == null) return
        listBox.removeAllViews()
        val items = Store.all()

        statLists.text = items.size.toString()
        statActive.text = items.count { it.enabled }.toString()
        statChannels.text = fmt(items.filter { it.enabled && it.status == STATUS_OK }.sumOf { it.channelCount })

        if (items.isEmpty()) {
            listBox.addView(emptyState())
            return
        }
        items.forEach { listBox.addView(buildCard(it)) }
    }

    private fun emptyState(): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ctx.dp(24), ctx.dp(48), ctx.dp(24), ctx.dp(24))
        }
        box.addView(LogoView(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(72), ctx.dp(72)); alpha = 0.55f })
        box.addView(label("Henüz liste yok", 17f, Theme.TEXT, bold = true).apply { setPadding(0, ctx.dp(18), 0, ctx.dp(6)) })
        box.addView(label(
            "M3U linkinizi ekleyin. Kısaltılmış linkler otomatik çözülür, liste okunur ve çalışmayan linkler kaydedilmez.",
            13f, Theme.MUTED
        ).apply { gravity = Gravity.CENTER })
        return box
    }

    private fun statusColor(e: PlaylistEntry): Int = when {
        checking.contains(e.id) -> Theme.AMBER
        !e.enabled -> Theme.OFF
        e.status == STATUS_OK -> Theme.GREEN
        e.status == STATUS_ERROR -> Theme.RED
        else -> Theme.MUTED
    }

    private fun buildCard(e: PlaylistEntry): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(ctx, Theme.CARD, 22, Theme.STROKE, 1)
            setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(10), ctx.dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = ctx.dp(12) }
        }

        // -- Üst satır: durum noktası, ad + adres, anahtar
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, ctx.dp(6), 0)
        }
        top.addView(View(ctx).apply {
            background = roundBg(ctx, statusColor(e), 6)
            layoutParams = LinearLayout.LayoutParams(ctx.dp(11), ctx.dp(11)).apply { marginEnd = ctx.dp(12) }
        })
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            alpha = if (e.enabled) 1f else 0.5f
        }
        info.addView(label(e.name, 16f, Theme.TEXT, bold = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        info.addView(label(e.url, 11f, Theme.MUTED).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, ctx.dp(4), 0, 0)
        })
        top.addView(info)
        top.addView(ToggleView(ctx).apply {
            setChecked(e.enabled)
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ctx.dp(10) }
            onChange = { on ->
                e.enabled = on
                Store.save()
                render()
            }
        })
        card.addView(top)

        // -- Chip satırı
        val chips = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ctx.dp(12), 0, 0)
            alpha = if (e.enabled) 1f else 0.5f
        }
        when {
            checking.contains(e.id) -> chips.addView(chip("Kontrol ediliyor…", Theme.AMBER))
            e.status == STATUS_OK -> {
                chips.addView(chip("${fmt(e.channelCount)} kanal", Theme.TEXT))
                chips.addView(chip("${e.categories.size} kategori", Theme.VIOLET))
                chips.addView(chip("✓ ${timeText(e.lastChecked)}", Theme.GREEN))
            }
            e.status == STATUS_ERROR -> chips.addView(chip("Çalışmıyor", Theme.RED))
            else -> chips.addView(chip("Henüz kontrol edilmedi", Theme.MUTED))
        }
        card.addView(chips)

        if (e.status == STATUS_ERROR && !checking.contains(e.id)) {
            card.addView(label(e.lastError ?: "Liste okunamadı.", 12f, Theme.RED).apply {
                setPadding(0, ctx.dp(10), ctx.dp(6), 0)
            })
        }

        // -- Ayırıcı
        card.addView(View(ctx).apply {
            setBackgroundColor(Theme.STROKE)
            layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(0, ctx.dp(12), ctx.dp(6), ctx.dp(4)) }
        })

        // -- Eylem satırı: Kategoriler ▾ | yenile, düzenle, sil
        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val isOpen = expanded.contains(e.id)
        val catBtn = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(0, ctx.dp(6), ctx.dp(10), ctx.dp(6))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setOnClickListener {
                if (isOpen) expanded.remove(e.id) else expanded.add(e.id)
                render()
            }
        }
        catBtn.addView(label("Kategoriler", 13f, Theme.TEXT, bold = true))
        catBtn.addView(label("  ${e.categories.size - e.categories.count { it.name in e.hidden }}/${e.categories.size}", 12f, Theme.MUTED))
        catBtn.addView(IconView(ctx, IconKind.CHEVRON, Theme.MUTED).apply {
            background = null
            rotation = if (isOpen) 180f else 0f
            layoutParams = LinearLayout.LayoutParams(ctx.dp(30), ctx.dp(30))
        })
        actions.addView(catBtn)

        fun action(kind: IconKind, tint: Int, onClick: () -> Unit) = IconView(ctx, kind, tint).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44))
            setOnClickListener { onClick() }
        }
        actions.addView(action(IconKind.REFRESH, Theme.AMBER) { testEntry(e) })
        actions.addView(action(IconKind.EDIT, Theme.BLUE) { showEditor(e) })
        actions.addView(action(IconKind.TRASH, Theme.RED) { confirmDelete(e) })
        card.addView(actions)

        if (isOpen) card.addView(buildCategories(e))
        return card
    }

    // ------------------------------------------------------------------ kategoriler (göz ikonu)

    private fun buildCategories(e: PlaylistEntry): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ctx.dp(4), ctx.dp(6), ctx.dp(10))
        }

        if (e.categories.isEmpty()) {
            box.addView(label("Kategori bulunamadı. Listeyi yenileyin.", 12f, Theme.MUTED))
            return box
        }

        val summary = label("", 12f, Theme.MUTED, bold = true)
        val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var query = ""

        fun updateSummary() {
            val visible = e.categories.count { it.name !in e.hidden }
            summary.text = "$visible / ${e.categories.size} kategori görünür"
        }

        fun fillRows() {
            rows.removeAllViews()
            e.categories
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .forEach { cat -> rows.addView(categoryRow(e, cat, ::updateSummary)) }
        }

        // Başlık + toplu işlemler
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(2), 0, ctx.dp(8))
        }
        summary.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        bar.addView(summary)
        fun textBtn(text: String, color: Int, onClick: () -> Unit) = label(text, 12f, color, bold = true).apply {
            background = roundBg(ctx, withAlpha(color, 30), 10)
            setPadding(ctx.dp(10), ctx.dp(6), ctx.dp(10), ctx.dp(6))
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ctx.dp(6) }
            setOnClickListener { onClick() }
        }
        bar.addView(textBtn("Tümünü göster", Theme.GREEN) {
            e.hidden.clear(); Store.save(); updateSummary(); fillRows()
        })
        bar.addView(textBtn("Tümünü gizle", Theme.RED) {
            e.hidden.addAll(e.categories.map { it.name }); Store.save(); updateSummary(); fillRows()
        })
        box.addView(bar)

        // Çok kategori varsa arama kutusu
        if (e.categories.size > 10) {
            box.addView(input(ctx, "Kategori ara…", "").apply {
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = ctx.dp(8) }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        query = s?.toString().orEmpty().trim()
                        fillRows()
                    }
                })
            })
        }

        updateSummary()
        fillRows()
        box.addView(rows)
        return box
    }

    private fun categoryRow(e: PlaylistEntry, cat: CategoryInfo, onToggled: () -> Unit): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(ctx, Theme.CARD_ALT, 14)
            setPadding(ctx.dp(14), ctx.dp(2), ctx.dp(4), ctx.dp(2))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = ctx.dp(6) }
        }
        val name = label(cat.name, 14f, Theme.TEXT).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val count = label(fmt(cat.count), 11f, Theme.MUTED, bold = true).apply {
            background = roundBg(ctx, withAlpha(Theme.MUTED, 30), 8)
            setPadding(ctx.dp(8), ctx.dp(3), ctx.dp(8), ctx.dp(3))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = ctx.dp(4) }
        }
        val eye = IconView(ctx, IconKind.EYE, Theme.GREEN).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44))
        }
        fun paint() {
            val hidden = cat.name in e.hidden
            eye.set(if (hidden) IconKind.EYE_OFF else IconKind.EYE, if (hidden) Theme.MUTED else Theme.GREEN)
            name.alpha = if (hidden) 0.4f else 1f
            count.alpha = if (hidden) 0.4f else 1f
        }
        eye.setOnClickListener {
            if (cat.name in e.hidden) e.hidden.remove(cat.name) else e.hidden.add(cat.name)
            Store.save()
            paint()
            onToggled()
        }
        paint()
        row.addView(name)
        row.addView(count)
        row.addView(eye)
        return row
    }

    // ------------------------------------------------------------------ test / yenile

    private fun testEntry(e: PlaylistEntry) {
        if (!checking.add(e.id)) return
        render()
        scope.launch {
            val result = withContext(Dispatchers.IO) { PlaylistLoader.load(e.url) }
            Repo.apply(e, result)
            checking.remove(e.id)
            if (!isAdded) return@launch
            render()
            toast(if (result.ok) "${fmt(result.channels.size)} kanal okundu" else "Çalışmıyor: ${result.error}")
        }
    }

    // ------------------------------------------------------------------ ekle / düzenle penceresi

    private fun input(ctx: Context, hint: String, value: String): EditText = EditText(ctx).apply {
        setText(value)
        this.hint = hint
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 14f
        background = roundBg(ctx, Theme.CARD_ALT, 14, Theme.STROKE, 1)
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        inputType = InputType.TYPE_CLASS_TEXT
        maxLines = 1
        isSingleLine = true
    }

    private fun makeDialog(content: View): Dialog {
        val ctx = requireContext()
        return Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(), WRAP)
            }
        }
    }

    private fun dialogBox(): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(ctx, Theme.CARD, 26, Theme.STROKE, 1)
            setPadding(ctx.dp(20), ctx.dp(20), ctx.dp(20), ctx.dp(16))
        }
    }

    private fun pillButton(text: String, color: Int, filled: Boolean, onClick: () -> Unit): TextView {
        val ctx = requireContext()
        return label(text, 14f, if (filled) Color.WHITE else color, bold = true).apply {
            gravity = Gravity.CENTER
            background = if (filled) roundBg(ctx, color, 14) else roundBg(ctx, withAlpha(color, 30), 14)
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun showEditor(existing: PlaylistEntry?) {
        val ctx = requireContext()
        val box = dialogBox()
        val dlg = makeDialog(box)

        box.addView(label(if (existing == null) "Yeni IPTV listesi" else "Listeyi düzenle", 18f, Theme.TEXT, bold = true))
        box.addView(label("Link kaydedilmeden önce çözülür ve okunur; çalışmayan link eklenemez.", 12f, Theme.MUTED).apply {
            setPadding(0, ctx.dp(6), 0, ctx.dp(14))
        })

        box.addView(label("LİSTE ADI", 10f, Theme.MUTED, bold = true).apply { setPadding(ctx.dp(2), 0, 0, ctx.dp(6)) })
        val nameIn = input(ctx, "Örn: Spor Kanalları", existing?.name.orEmpty())
        box.addView(nameIn)

        box.addView(label("M3U LİNKİ", 10f, Theme.MUTED, bold = true).apply { setPadding(ctx.dp(2), ctx.dp(14), 0, ctx.dp(6)) })
        val urlIn = input(ctx, "https://… (kısaltılmış link olabilir)", existing?.url.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(urlIn)

        val status = label("", 12f, Theme.MUTED).apply { setPadding(ctx.dp(2), ctx.dp(12), 0, 0) }
        box.addView(status)

        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ctx.dp(16), 0, 0)
        }
        var working = false

        val cancel = pillButton("İptal", Theme.MUTED, false) { if (!working) dlg.dismiss() }
        val paste = pillButton("Yapıştır", Theme.BLUE, false) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
            if (!text.isNullOrBlank()) urlIn.setText(text.trim()) else toast("Panoda link yok")
        }
        val save = pillButton("Kontrol et & Kaydet", Theme.ACCENT, true) {}

        save.setOnClickListener {
            if (working) return@setOnClickListener
            val raw = urlIn.text.toString().trim()
            if (raw.isEmpty()) {
                status.setTextColor(Theme.RED); status.text = "Lütfen bir link girin."
                return@setOnClickListener
            }
            working = true
            save.alpha = 0.55f
            status.setTextColor(Theme.AMBER)
            status.text = "Link çözülüyor ve liste okunuyor…"
            scope.launch {
                val r = withContext(Dispatchers.IO) { PlaylistLoader.load(raw) }
                working = false
                save.alpha = 1f
                if (!isAdded) return@launch
                if (!r.ok) {
                    status.setTextColor(Theme.RED)
                    status.text = "✕ ${r.error}"
                    return@launch
                }
                val name = nameIn.text.toString().trim().ifEmpty { LinkTools.hostOf(r.finalUrl) }
                val entry = existing ?: PlaylistEntry(UUID.randomUUID().toString(), name, raw)
                entry.name = name
                entry.url = raw
                Repo.apply(entry, r)
                if (existing == null) Store.add(entry) else Store.save()
                expanded.add(entry.id)
                dlg.dismiss()
                render()
                toast("${fmt(r.channels.size)} kanal bulundu")
            }
        }

        buttons.addView(cancel, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = ctx.dp(8) })
        buttons.addView(paste, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = ctx.dp(8) })
        buttons.addView(save, LinearLayout.LayoutParams(0, WRAP, 2f))
        box.addView(buttons)
        dlg.show()
    }

    private fun confirmDelete(e: PlaylistEntry) {
        val ctx = requireContext()
        val box = dialogBox()
        val dlg = makeDialog(box)
        box.addView(label("Listeyi sil?", 18f, Theme.TEXT, bold = true))
        box.addView(label("“${e.name}” ve kategori ayarları kalıcı olarak silinecek.", 13f, Theme.MUTED).apply {
            setPadding(0, ctx.dp(8), 0, ctx.dp(16))
        })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pillButton("Vazgeç", Theme.MUTED, false) { dlg.dismiss() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = ctx.dp(8) })
        row.addView(pillButton("Sil", Theme.RED, true) {
            expanded.remove(e.id)
            Store.remove(e)
            dlg.dismiss()
            render()
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(row)
        dlg.show()
    }
}
