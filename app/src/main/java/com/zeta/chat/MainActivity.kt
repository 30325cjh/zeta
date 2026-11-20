package com.zeta.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatLine(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String,
    val text: String,
    val userWritten: Boolean = false,
    val bookmarked: Boolean = false
)
data class ChatSlot(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "새 대화",
    val lines: MutableList<ChatLine> = mutableListOf()
)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("zeta_store", MODE_PRIVATE) }
    private val http = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZetaTheme {
                ZetaScreen(
                    initialSlots = loadSlots(),
                    apiReady = BuildConfig.GEMINI_API_KEY.isNotBlank(),
                    onSave = ::saveSlots,
                    onSend = ::askGemini
                )
            }
        }
    }

    private fun loadSlots(): MutableList<ChatSlot> {
        val raw = prefs.getString("slots", null) ?: return mutableListOf(ChatSlot())
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val source = obj.optJSONArray("lines") ?: JSONArray()
                val lines = MutableList(source.length()) { j ->
                    val l = source.getJSONObject(j)
                    ChatLine(l.optString("id", UUID.randomUUID().toString()),
                        l.optString("speaker", "나"), l.optString("text", ""),
                        l.optBoolean("userWritten"), l.optBoolean("bookmarked"))
                }
                ChatSlot(obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("title", "대화"), lines)
            }.ifEmpty { mutableListOf(ChatSlot()) }
        } catch (_: Exception) { mutableListOf(ChatSlot()) }
    }

    private fun saveSlots(slots: List<ChatSlot>) {
        val all = JSONArray()
        slots.forEach { slot ->
            val lines = JSONArray()
            slot.lines.forEach { line ->
                lines.put(JSONObject().put("id", line.id).put("speaker", line.speaker)
                    .put("text", line.text).put("userWritten", line.userWritten)
                    .put("bookmarked", line.bookmarked))
            }
            all.put(JSONObject().put("id", slot.id).put("title", slot.title).put("lines", lines))
        }
        prefs.edit().putString("slots", all.toString()).apply()
    }

    private suspend fun askGemini(history: List<ChatLine>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = BuildConfig.GEMINI_API_KEY
            check(key.isNotBlank()) { "API 키가 없습니다. GitHub Secrets에 GEMINI_API_KEY를 등록하고 다시 빌드해 주세요." }
            val transcript = history.takeLast(100).joinToString("\n") { "${it.speaker}: ${it.text}" }
            val prompt = """
                너는 ZETA의 다중 캐릭터 대화 엔진이다. 아래 기록을 읽고 자연스럽게 대화를 이어라.
                캐릭터 발언은 반드시 '@캐릭터이름: 내용' 형식으로 출력한다.
                사용자가 직접 쓴 '@이름: 내용'은 그 캐릭터가 실제로 말한 대사이므로 이를 반복하지 말고 맥락을 이어라.
                일반 사용자 발언은 '나'의 말이다. 등장인물의 말투와 관계를 유지하라.
                행동 묘사는 *행동 묘사* 형식을 쓸 수 있다.
                출력은 이어지는 캐릭터 발언만 작성하고, 대화에 없는 캐릭터를 임의로 추가하지 마라.
                대화 기록:
                $transcript
            """.trimIndent()
            val payload = JSONObject().put("contents",
                JSONArray().put(JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 2048))
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$key")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    val detail = try { JSONObject(raw).getJSONObject("error").optString("message") }
                    catch (_: Exception) { raw.take(240) }
                    "Gemini 오류 ${response.code}: $detail"
                }
                JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
        }
    }
}

@Composable
private fun ZetaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF9DBBFF), secondary = Color(0xFFB9A6FF),
        background = Color(0xFF101116), surface = Color(0xFF1A1C24)
    ), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZetaScreen(
    initialSlots: MutableList<ChatSlot>,
    apiReady: Boolean,
    onSave: (List<ChatSlot>) -> Unit,
    onSend: suspend (List<ChatLine>) -> Result<String>
) {
    val slots = remember { mutableStateListOf<ChatSlot>().apply { addAll(initialSlots) } }
    var selected by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (apiReady) "Gemini 연결 준비됨" else "API 키 미설정 · GitHub Secret 필요") }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val slot = slots.getOrNull(selected) ?: slots.first()
    refresh.hashCode()

    fun persist() = onSave(slots.toList())
    fun send() {
        val typed = input.trim()
        if (typed.isEmpty() || busy) return
        input = ""
        parseRoleLines(typed).forEach { slot.lines.add(it) }
        if (slot.title == "새 대화") slot.title = typed.take(24)
        refresh++
        persist()
        busy = true
        status = "캐릭터가 생각하는 중..."
        scope.launch {
            val result = onSend(slot.lines.toList())
            result.onSuccess { answer ->
                parseRoleLines(answer, defaultSpeaker = "아리스").forEach { slot.lines.add(it.copy(userWritten = false)) }
                status = "응답 완료"
            }.onFailure { status = it.message ?: "응답 실패" }
            busy = false
            refresh++
            persist()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ZETA", fontWeight = FontWeight.Bold)
                    Text("대화 ${selected + 1} / ${slots.size}", fontSize = 11.sp, color = Color.Gray)
                } },
                navigationIcon = {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Text("대화 ▾", color = Color(0xFFB8C7F0),
                            modifier = Modifier.clickable { menu = true }.padding(12.dp))
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            slots.forEachIndexed { index, item ->
                                DropdownMenuItem(text = { Text("${index + 1}. ${item.title}") },
                                    onClick = { selected = index; menu = false })
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (slots.size < 100) {
                            slots.add(ChatSlot()); selected = slots.lastIndex; persist()
                        } else status = "대화 슬롯은 최대 100개입니다."
                    }) { Icon(Icons.Default.Add, contentDescription = "새 대화") }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color(0xFF171920)).navigationBarsPadding()) {
                Text(status, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 5.dp))
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f).background(Color(0xFF292C36), RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("메시지 입력  (@아리스: 내용)", color = Color.Gray)
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { send() }, enabled = !busy) {
                        Icon(Icons.Default.Send, contentDescription = "전송", tint = Color(0xFF9DBBFF))
                    }
                }
            }
        },
        containerColor = Color(0xFF101116)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (slot.lines.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("ZETA", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9DBBFF))
                    Text("캐릭터와 이야기를 시작해 보세요.", color = Color.LightGray)
                    Spacer(Modifier.height(10.dp))
                    Text("일반 문장은 내 말 · @이름: 문장은 해당 캐릭터의 말", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(slot.lines.toList(), key = { it.id }) { line ->
                        ChatBubble(line) {
                            val index = slot.lines.indexOfFirst { it.id == line.id }
                            if (index >= 0) slot.lines[index] = slot.lines[index].copy(bookmarked = !slot.lines[index].bookmarked)
                            refresh++
                            persist()
                        }
                    }
                    if (busy) item { Text("캐릭터가 입력 중...", color = Color.Gray) }
                }
            }
        }
    }
}

private fun parseRoleLines(raw: String, defaultSpeaker: String = "나"): List<ChatLine> {
    val result = mutableListOf<ChatLine>()
    raw.lines().forEach { source ->
        val line = source.trim()
        if (line.isBlank()) return@forEach
        val match = Regex("^@([^:：]+)[:：]\\s*(.*)$").matchEntire(line)
        if (match != null) {
            result.add(ChatLine(speaker = match.groupValues[1].trim(),
                text = match.groupValues[2].trim(), userWritten = true))
        } else result.add(ChatLine(speaker = defaultSpeaker, text = line, userWritten = defaultSpeaker == "나"))
    }
    return result
}

@Composable
private fun ChatBubble(line: ChatLine, onBookmark: () -> Unit) {
    val mine = line.speaker == "나"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top) {
        if (!mine) {
            Box(Modifier.size(34.dp).background(Color(0xFF4A5070), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(line.speaker.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(7.dp))
        }
        Column(Modifier.widthIn(max = 300.dp)) {
            if (!mine) Text(line.speaker, color = Color(0xFFB8C7F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (mine) Color(0xFF365C91) else Color(0xFF252832),
                    shape = RoundedCornerShape(15.dp)) {
                    Text(line.text, color = Color.White, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        fontSize = 15.sp)
                }
                IconButton(onClick = onBookmark, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = "책갈피",
                        tint = if (line.bookmarked) Color(0xFFFFD36A) else Color.Gray.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
