package com.zeta.music

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var library: LinearLayout
    private val pickerCode = 41

    data class Track(val title: String, val creator: String, val id: String, val license: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF080808.toInt()
        window.navigationBarColor = 0xFF080808.toInt()
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF090909.toInt()) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 25, 20, 25) }
        scroll.addView(root)
        setContentView(scroll)

        addText(root, "Z", 56, -1, true, Gravity.CENTER)
        addText(root, "ZETA", 23, -1, true, Gravity.CENTER)
        addText(root, "오프라인 음악 플레이어", 13, 0xFFAAAAAA.toInt(), false, Gravity.CENTER)
        gap(root, 22)
        addText(root, "무료·공개 라이선스 음원 찾기", 18, -1, true)
        query = EditText(this).apply {
            hint = "곡명 또는 아티스트 검색"; setSingleLine(true)
            setTextColor(-1); setHintTextColor(0xFF888888.toInt())
            setPadding(14, 10, 14, 10); setBackgroundColor(0xFF202020.toInt())
        }
        root.addView(query, matchWrap())
        val search = makeButton("음원 검색")
        root.addView(search, matchWrap())
        search.setOnClickListener { searchArchive(query.text.toString().trim()) }
        val import = makeButton("기기에 있는 오디오 파일 가져오기")
        root.addView(import, matchWrap())
        import.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(i, pickerCode)
        }
        status = addText(root, "검색 결과가 여기에 표시됩니다.", 13, 0xFFBBBBBB.toInt(), false)
        gap(root, 8)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        gap(root, 22)
        addText(root, "내 오프라인 보관함", 18, -1, true)
        addText(root, "저장된 음악은 인터넷 없이 재생할 수 있습니다.", 12, 0xFF999999.toInt(), false)
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(library)
        gap(root, 18)
        addText(root, "안내: YouTube Music 음원 추출·변환은 지원하지 않습니다. 공개 라이선스 음원과 직접 보유한 파일을 재생합니다.", 11, 0xFF888888.toInt(), false)
        renderLibrary()
    }

    private fun searchArchive(q: String) {
        if (q.isBlank()) { status.text = "검색어를 입력해 주세요."; return }
        status.text = "공개 라이선스 음원 검색 중…"
        results.removeAllViews()
        Thread {
            try {
                val e = URLEncoder.encode(q, "UTF-8")
                val url = "https://archive.org/advancedsearch.php?q=" + e +
                    "%20AND%20mediatype%3Aaudio&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl&rows=30&page=1&output=json"
                val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: error("응답 없음") }
                val docs = JSONObject(body).getJSONObject("response").getJSONArray("docs")
                val found = ArrayList<Track>()
                for (i in 0 until docs.length()) {
                    val d = docs.getJSONObject(i)
                    val license = d.optString("licenseurl")
                    val ok = license.contains("creativecommons.org/publicdomain", true) ||
                        license.contains("creativecommons.org/licenses/by/", true) ||
                        license.contains("creativecommons.org/licenses/by-sa/", true) ||
                        license.contains("creativecommons.org/licenses/zero/", true)
                    if (ok) found.add(Track(d.optString("title", "제목 없음"), d.optString("creator", "아티스트 정보 없음"), d.optString("identifier"), license))
                }
                runOnUiThread {
                    status.text = if (found.isEmpty()) "공개 라이선스가 확인된 결과가 없습니다." else found.size.toString() + "개 음원을 찾았습니다."
                    found.forEach { addResult(it) }
                }
            } catch (e: Exception) { runOnUiThread { status.text = "검색 실패: " + (e.message ?: "네트워크 오류") } }
        }.start()
    }

    private fun addResult(t: Track) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(0xFF191919.toInt()) }
        addText(card, t.title, 15, -1, true)
        addText(card, t.creator, 12, 0xFFBBBBBB.toInt(), false)
        addText(card, "공개 라이선스 URL 확인됨 · 개별 이용 조건 확인 필요", 10, 0xFF8BD5A5.toInt(), false)
        val b = makeButton("MP3 다운로드")
        card.addView(b, matchWrap())
        b.setOnClickListener { downloadTrack(t, b) }
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })
    }

    private fun downloadTrack(t: Track, button: Button) {
        button.isEnabled = false; button.text = "다운로드 중…"
        Thread {
            try {
                val metaUrl = "https://archive.org/metadata/" + t.id
                val meta = http.newCall(Request.Builder().url(metaUrl).build()).execute().use {
                    JSONObject(it.body?.string() ?: error("메타데이터 없음"))
                }
                val files = meta.getJSONArray("files")
                var selected: JSONObject? = null
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val format = f.optString("format")
                    if ((format.equals("VBR MP3", true) || format.equals("MP3", true)) && f.optString("private") != "true") {
                        selected = f; break
                    }
                }
                val f = selected ?: error("MP3 파일이 없습니다.")
                val fileName = f.getString("name")
                val encodedName = fileName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val url = "https://archive.org/download/" + t.id + "/" + encodedName
                val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP " + r.code)
                    r.body?.bytes() ?: error("파일이 비어 있습니다.")
                }
                val safe = t.title.replace(Regex("[^가-힣a-zA-Z0-9._ -]"), "_").take(65).ifBlank { "audio" }
                val out = File(getExternalFilesDir("Music") ?: filesDir, safe + "_" + System.currentTimeMillis() + ".mp3")
                out.writeBytes(bytes)
                getSharedPreferences("library", MODE_PRIVATE).edit().putString(out.absolutePath, t.title).apply()
                runOnUiThread { button.text = "저장 완료"; button.isEnabled = true; status.text = "저장 완료: " + t.title; renderLibrary() }
            } catch (e: Exception) {
                runOnUiThread { button.text = "다시 시도"; button.isEnabled = true; status.text = "다운로드 실패: " + (e.message ?: "오류") }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickerCode || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        else data.data?.let { uris.add(it) }
        Thread {
            uris.forEachIndexed { index, uri ->
                try {
                    val out = File(getExternalFilesDir("Music") ?: filesDir, "import_" + System.currentTimeMillis() + "_" + index + ".audio")
                    contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                    getSharedPreferences("library", MODE_PRIVATE).edit().putString(out.absolutePath, displayName(uri)).apply()
                } catch (_: Exception) {}
            }
            runOnUiThread { renderLibrary(); status.text = uris.size.toString() + "개 파일을 추가했습니다." }
        }.start()
    }

    private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "가져온 음악"

    private fun renderLibrary() {
        if (!::library.isInitialized) return
        library.removeAllViews()
        val entries = getSharedPreferences("library", MODE_PRIVATE).all.entries
            .filter { File(it.key).exists() }.sortedBy { it.value.toString() }
        if (entries.isEmpty()) { addText(library, "저장된 음악이 없습니다.", 13, 0xFF999999.toInt(), false); return }
        entries.forEach { entry ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 7, 4, 7); setBackgroundColor(0xFF181818.toInt()) }
            val name = TextView(this).apply { text = entry.value.toString(); setTextColor(-1); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            val play = makeButton("재생")
            play.setOnClickListener {
                val i = Intent(this, PlaybackService::class.java).apply {
                    action = PlaybackService.PLAY
                    putExtra("path", entry.key); putExtra("title", entry.value.toString())
                }
                ContextCompat.startForegroundService(this, i); status.text = "재생 중: " + entry.value
            }
            val delete = makeButton("삭제")
            delete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("보관함에서 삭제")
                    .setMessage(entry.value.toString() + " 파일을 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제") { _, _ ->
                        runCatching { File(entry.key).delete() }
                        getSharedPreferences("library", MODE_PRIVATE).edit().remove(entry.key).apply()
                        renderLibrary()
                        status.text = "파일을 삭제했습니다."
                    }.show()
            }
            row.addView(name); row.addView(play); row.addView(delete)
            library.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 })
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pause = makeButton("일시정지")
        pause.setOnClickListener { startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.PAUSE)) }
        val resume = makeButton("계속 재생")
        resume.setOnClickListener { startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.RESUME)) }
        controls.addView(pause, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(resume, LinearLayout.LayoutParams(0, -2, 1f))
        val stop = makeButton("재생 종료")
        stop.setOnClickListener { startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.STOP)) }
        controls.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        library.addView(controls)
        val clear = makeButton("보관함 전체 비우기")
        clear.setOnClickListener {
            AlertDialog.Builder(this).setTitle("전체 파일 삭제")
                .setMessage("앱에 저장된 음악 파일을 모두 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("모두 삭제") { _, _ ->
                    entries.forEach { runCatching { File(it.key).delete() } }
                    getSharedPreferences("library", MODE_PRIVATE).edit().clear().apply()
                    renderLibrary()
                    status.text = "보관함을 비웠습니다."
                }.show()
        }
        library.addView(clear, matchWrap())
    }

    private fun addText(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean, gravity: Int = Gravity.START): TextView {
        val v = TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color); this.gravity = gravity
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 3, 0, 3)
        }
        parent.addView(v, matchWrap()); return v
    }
    private fun makeButton(label: String) = Button(this).apply {
        text = label; setTextColor(-1); setBackgroundColor(0xFF303030.toInt()); textSize = 13f; isAllCaps = false
    }
    private fun gap(parent: LinearLayout, h: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, h)) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
