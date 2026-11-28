package com.zeta.apkrestorer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var details: TextView
    private var restoredDir: File? = null
    private var sourceName = "input.apk"
    private val maxExpandedBytes = 700L * 1024L * 1024L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.rgb(10, 10, 10))
        }
        fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
        }
        root.addView(label("ZETA", 30f))
        root.addView(label("APK → 프로젝트 복원", 20f).apply { setPadding(0, 4, 0, 22) })
        val pick = Button(this).apply { text = "APK 파일 선택" }
        root.addView(pick)
        status = label("APK를 선택하면 내부 파일을 분석합니다.", 15f, Color.LTGRAY)
        status.setPadding(0, 18, 0, 12)
        root.addView(status)
        details = label("지원 항목\n• APK 내부 파일 및 리소스 추출\n• DEX 바이트코드 보존\n• 앱 정보 및 복원용 프로젝트 구조 생성\n\n주의: 원본 Kotlin/Java 소스코드와 주석을 완벽하게 복구하는 도구는 아닙니다.", 14f, Color.LTGRAY)
        root.addView(ScrollView(this).apply { addView(details) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val export = Button(this).apply { text = "복원 프로젝트 ZIP 내보내기"; isEnabled = false }
        root.addView(export)
        pick.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/vnd.android.package-archive"
            }
            startActivityForResult(i, REQ_OPEN)
        }
        export.setOnClickListener {
            if (restoredDir == null) return@setOnClickListener
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, sourceName.substringBeforeLast('.', "restored") + "-restored-project.zip")
            }
            startActivityForResult(i, REQ_SAVE)
        }
        setContentView(root)
        exportButton = export
    }

    private lateinit var exportButton: Button

    @Deprecated("Deprecated in Android API but retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == REQ_OPEN) {
            status.text = "APK 분석 및 파일 추출 중…"
            exportButton.isEnabled = false
            worker.execute { restore(uri) }
        } else if (requestCode == REQ_SAVE) {
            val dir = restoredDir ?: return
            status.text = "ZIP 파일 생성 중…"
            worker.execute {
                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                                val name = file.relativeTo(dir).invariantSeparatorsPath
                                zip.putNextEntry(ZipEntry(name))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    } ?: error("저장 위치를 열 수 없습니다.")
                    runOnUiThread { status.text = "완료! 복원 프로젝트 ZIP을 저장했습니다." }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "내보내기 실패: ${e.message}" }
                }
            }
        }
    }

    private fun restore(uri: Uri) {
        try {
            val input = File(cacheDir, "selected.apk")
            contentResolver.openInputStream(uri)?.use { src -> input.outputStream().use(src::copyTo) }
                ?: error("APK 파일을 읽을 수 없습니다.")
            sourceName = queryName(uri) ?: "restored.apk"
            val info = packageManager.getPackageArchiveInfo(input.absolutePath, 0)
            val root = File(cacheDir, "restored-project").apply { deleteRecursively(); mkdirs() }
            val raw = File(root, "apk-original").apply { mkdirs() }
            input.copyTo(File(raw, sourceName), overwrite = true)
            val extracted = File(root, "extracted").apply { mkdirs() }
            var total = 0L
            var count = 0
            ZipInputStream(FileInputStream(input)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val clean = entry.name.replace('\\', '/')
                    if (clean.startsWith("/") || clean.split('/').any { it == ".." }) {
                        zis.closeEntry(); continue
                    }
                    if (!entry.isDirectory) {
                        val out = File(extracted, clean)
                        if (!out.canonicalPath.startsWith(extracted.canonicalPath + File.separator)) {
                            zis.closeEntry(); continue
                        }
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zis.read(buffer)
                                if (n <= 0) break
                                total += n
                                if (total > maxExpandedBytes) error("압축 해제 용량이 700MB를 초과했습니다.")
                                stream.write(buffer, 0, n)
                            }
                        }
                        count++
                    }
                    zis.closeEntry()
                }
            }
            val dexDir = File(root, "recovered-project/app/src/main/dex").apply { mkdirs() }
            extracted.listFiles()?.filter { it.isFile && it.name.matches(Regex("classes(\\d*)\\.dex")) }?.forEach {
                it.copyTo(File(dexDir, it.name), overwrite = true)
            }
            val meta = buildString {
                appendLine("# APK 복원 결과")
                appendLine()
                appendLine("- 원본 파일: $sourceName")
                appendLine("- 패키지: ${info?.packageName ?: "확인 불가"}")
                appendLine("- 버전명: ${info?.versionName ?: "확인 불가"}")
                appendLine("- 추출 파일 수: $count")
                appendLine("- 추출 크기: $total bytes")
                appendLine()
                appendLine("## 복원 범위")
                appendLine("APK 내부 리소스와 DEX 바이트코드를 보존했습니다. DEX는 원본 Kotlin/Java 소스가 아니며, 이 프로젝트는 자동으로 완전 빌드 가능한 원본 프로젝트가 아닙니다.")
                appendLine("AndroidManifest.xml은 APK에 저장된 바이너리 XML일 수 있습니다. 리소스는 extracted/ 아래 원래 경로로 보존됩니다.")
            }
            File(root, "RESTORE_REPORT.md").writeText(meta)
            val gradle = File(root, "recovered-project")
            File(gradle, "settings.gradle.kts").writeText("pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositories { google(); mavenCentral() } }\nrootProject.name = \"RecoveredApk\"\ninclude(\":app\")\n")
            File(gradle, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") version \"8.7.3\" apply false }\n")
            File(gradle, "app").mkdirs()
            File(gradle, "app/build.gradle.kts").writeText("plugins { id(\"com.android.application\") }\nandroid { namespace = \"recovered.placeholder\"; compileSdk = 35; defaultConfig { applicationId = \"recovered.placeholder\"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = \"1.0\" } }\n")
            restoredDir = root
            runOnUiThread {
                status.text = "분석 완료: 파일 $count개 추출"
                details.text = meta + "\n\nZIP 내보내기를 누르면 원본 APK, 추출 리소스, DEX, 프로젝트 뼈대를 저장합니다."
                exportButton.isEnabled = true
            }
        } catch (e: Exception) {
            runOnUiThread { status.text = "복원 실패: ${e.message}" }
        }
    }

    private fun queryName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) c.getString(index) else null
        }
    }

    companion object { private const val REQ_OPEN = 10; private const val REQ_SAVE = 11 }
}
