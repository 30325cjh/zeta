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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Panel2 = Color(0xFF25252E)
private val Accent = Color(0xFFB8A1FF)
private val MainText = Color(0xFFF4F1FA)
private val Muted = Color(0xFF9A96A5)

data class Persona(val id: String, val name: String, val tagline: String, val personality: String, val greeting: String)
data class ChatLine(val speaker: String, val body: String, val fromCharacter: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) { ZetaApp() } }
    }
}

@Composable
private fun ZetaApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zeta_local", 0) }
    val scope = rememberCoroutineScope()
    val personas = remember {
        mutableStateListOf<Persona>().apply {
            val saved = prefs.getString("personas", null)
            if (saved.isNullOrBlank()) {
                add(Persona("default", "하나", "다정하고 장난기 있는 대화 상대", "친근하고 자연스럽게 말한다. 상대의 말에 적극적으로 반응하고 대화를 이어간다.", "안녕! 오늘은 어떤 이야기를 해볼까?"))
            } else {
                runCatching {
                    val arr = JSONArray(saved)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(Persona(o.optString("id"), o.optString("name"), o.optString("tagline"), o.optString("personality"), o.optString("greeting")))
                    }
                }
            }
        }
    }
    val messages = remember {
        mutableStateListOf<ChatLine>().apply {
            val saved = prefs.getString("messages", null)
            if (!saved.isNullOrBlank()) runCatching {
                val arr = JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ChatLine(o.optString("speaker"), o.optString("body"), o.optBoolean("character")))
                }
            }
        }
    }
    var currentPersona by remember { mutableStateOf(personas.firstOrNull()?.id ?: "") }
    var page by remember { mutableStateOf("home") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) chatListState.animateScrollToItem(messages.lastIndex) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var selectedForChat by remember { mutableStateOf(personas.map { it.id }.toSet()) }

    fun persist() {
        val p = JSONArray()
        personas.forEach { x -> p.put(JSONObject().put("id", x.id).put("name", x.name).put("tagline", x.tagline).put("personality", x.personality).put("greeting", x.greeting)) }
        val m = JSONArray()
        messages.forEach { x -> m.put(JSONObject().put("speaker", x.speaker).put("body", x.body).put("character", x.fromCharacter)) }
        prefs.edit().putString("personas", p.toString()).putString("messages", m.toString()).apply()
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        val directed = Regex("^@([^:：]+)[:：]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
        val targetName = directed?.groupValues?.get(1)?.trim()
        val target = personas.firstOrNull { it.name.equals(targetName, true) }
        val actualText = directed?.groupValues?.get(2)?.trim().orEmpty().ifBlank { text }
        val speaker = if (target != null) target.name else "나"
        messages.add(ChatLine(speaker, actualText, target != null))
        draft = ""
        persist()
        busy = true
        error = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                askZeta(apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }, personas.filter { selectedForChat.contains(it.id) || it.name.equals(target?.name, true) }, messages.toList(), target?.name)
            }
            if (result.isSuccess) {
                messages.add(ChatLine(result.getOrNull()?.first ?: (target?.name ?: personas.firstOrNull()?.name ?: "ZETA"), result.getOrNull()?.second ?: "", true))
                persist()
            } else {
                error = result.exceptionOrNull()?.message ?: "응답을 가져오지 못했어요."
            }
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page == "chat") {
                    IconButton(onClick = { page = "home" }) { Icon(Icons.Default.ArrowBack, null, tint = MainText) }
                } else {
                    Text("ZETA", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                if (page == "home") {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "설정", tint = Muted) }
                } else {
                    Text("개인 채팅", color = Muted, fontSize = 12.sp)
                }
            }

            if (page == "home") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp)) {
                    Text("나만의 캐릭터", color = MainText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("원하는 캐릭터를 만들고 자유롭게 대화해 보세요.", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("내 캐릭터", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editingPersona = null; showPersonaEditor = true }) {
                            Icon(Icons.Default.Add, null, tint = Accent)
                            Spacer(Modifier.width(4.dp))
                            Text("캐릭터 만들기", color = Accent)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(personas, key = { it.id }) { persona ->
                            Row(
                                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable {
                                    currentPersona = persona.id
                                    selectedForChat = setOf(persona.id)
                                    if (messages.isEmpty()) messages.add(ChatLine(persona.name, persona.greeting, true))
                                    page = "chat"
                                    persist()
                                }.padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(54.dp).background(Accent.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(persona.name.take(1), color = Accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(persona.name, color = MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(persona.tagline.ifBlank { "나만의 AI 캐릭터" }, color = Muted, fontSize = 12.sp, maxLines = 2)
                                }
                                IconButton(onClick = { editingPersona = persona; showPersonaEditor = true }) { Icon(Icons.Default.Edit, "편집", tint = Muted) }
                                Icon(Icons.Default.ChatBubbleOutline, null, tint = Accent)
                            }
                        }
                        if (personas.isEmpty()) item { Text("아직 캐릭터가 없어요. 위 버튼으로 첫 캐릭터를 만들어 보세요.", color = Muted) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("대화 기록", color = MainText, fontWeight = FontWeight.SemiBold)
                            Text(if (messages.isEmpty()) "저장된 대화가 없습니다" else "이 기기에 자동 저장된 대화 " + messages.size + "개", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { page = "chat" }) { Text("열기", color = Accent) }
                        TextButton(onClick = { messages.clear(); persist() }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                    }
                }
            } else {
                val active = personas.firstOrNull { it.id == currentPersona } ?: personas.firstOrNull()
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Accent.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(active?.name?.take(1) ?: "Z", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(active?.name ?: "ZETA", color = MainText, fontWeight = FontWeight.Bold)
                        Text("AI 캐릭터와 대화 중", color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showParticipants = true }) { Text("참여 설정", color = Accent, fontSize = 12.sp) }
                }
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    reverseLayout = false,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { line ->
                        val mine = !line.fromCharacter
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Text(line.speaker, color = if (mine) Muted else Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            Text(
                                text = actionStyled(line.body),
                                color = MainText,
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                modifier = Modifier.widthIn(max = 330.dp).background(if (mine) Color(0xFF393044) else Panel, RoundedCornerShape(17.dp)).padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    }
                    if (busy) item { Text("캐릭터가 생각하고 있어요…", color = Muted, fontSize = 13.sp) }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8B8B), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(Panel, RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text("@캐릭터이름: 대사 또는 메시지 입력", color = Muted, fontSize = 13.sp)
                            inner()
                        }
                    )
                    IconButton(onClick = { sendMessage(draft) }, enabled = !busy && draft.isNotBlank()) {
                        Icon(Icons.Default.Send, "전송", tint = if (busy || draft.isBlank()) Muted else Accent)
                    }
                }
            }
        }
    }

    if (showPersonaEditor) {
        PersonaEditor(
            initial = editingPersona,
            apiKey = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY },
            onDismiss = { showPersonaEditor = false },
            onSave = { p ->
                val index = personas.indexOfFirst { it.id == p.id }
                if (index >= 0) personas[index] = p else personas.add(p)
                if (currentPersona.isBlank()) currentPersona = p.id
                persist()
                showPersonaEditor = false
            }
        )
    }
    if (showParticipants) {
        AlertDialog(
            onDismissRequest = { showParticipants = false },
            containerColor = Panel,
            title = { Text("대화 참여 캐릭터", color = MainText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    personas.forEach { persona ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedForChat.contains(persona.id),
                                onCheckedChange = { checked ->
                                    if (checked) selectedForChat = selectedForChat + persona.id
                                    else if (selectedForChat.size > 1) selectedForChat = selectedForChat - persona.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Accent)
                            )
                            Text(persona.name, color = MainText)
                        }
                    }
                    Text("선택한 캐릭터들이 대화 맥락을 공유합니다.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showParticipants = false }) { Text("완료", color = Accent) } }
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("설정", color = MainText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Gemini API 키", color = MainText, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    var keyDraft by remember { mutableStateOf(apiKey) }
                    OutlinedTextField(value = keyDraft, onValueChange = { keyDraft = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), placeholder = { Text("GitHub Secret을 사용하려면 비워두세요") })
                    Spacer(Modifier.height(8.dp))
                    Text("모델: Gemini 2.5 Flash-Lite · 대화와 캐릭터는 기기에 저장됩니다.", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { apiKey = keyDraft; prefs.edit().putString("api_key", apiKey).apply(); showSettings = false }) { Text("저장", color = Accent) }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("닫기", color = Accent) } }
        )
    }
}

@Composable
private fun PersonaEditor(initial: Persona?, apiKey: String, onDismiss: () -> Unit, onSave: (Persona) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var tagline by remember { mutableStateOf(initial?.tagline ?: "") }
    var personality by remember { mutableStateOf(initial?.personality ?: "") }
    var greeting by remember { mutableStateOf(initial?.greeting ?: "") }
    var generating by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (initial == null) "캐릭터 만들기" else "캐릭터 편집", color = MainText, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                EditorField("이름", name, { name = it })
                EditorField("한 줄 소개", tagline, { tagline = it })
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            generateError = "설정에서 Gemini API 키를 먼저 등록해 주세요."
                        } else {
                            scope.launch {
                                generating = true
                                generateError = ""
                                val result = withContext(Dispatchers.IO) { generatePersonaDetails(apiKey, name, tagline) }
                                if (result.isSuccess) {
                                    val p = result.getOrThrow()
                                    name = p.name
                                    tagline = p.tagline
                                    personality = p.personality
                                    greeting = p.greeting
                                } else {
                                    generateError = result.exceptionOrNull()?.message ?: "생성 실패"
                                }
                                generating = false
                            }
                        }
                    },
                    enabled = !generating,
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Accent)
                ) {
                    if (generating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("AI로 캐릭터 설정 만들기")
                }
                if (generateError.isNotBlank()) Text(generateError, color = Color(0xFFFF8B8B), fontSize = 11.sp)
                EditorField("성격과 말투", personality, { personality = it }, minLines = 3)
                EditorField("첫 인사", greeting, { greeting = it }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(Persona(initial?.id ?: UUID.randomUUID().toString(), name.trim(), tagline.trim(), personality.trim(), greeting.trim().ifBlank { "안녕하세요!" }))
            }) { Text("저장", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Muted) } }
    )
}

@Composable
private fun EditorField(label: String, value: String, onValue: (String) -> Unit, minLines: Int = 1) {
    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp, bottom = 3.dp))
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), minLines = minLines, textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp))
}

private fun askZeta(apiKey: String, personas: List<Persona>, history: List<ChatLine>, directedTo: String?): Result<Pair<String, String>> {
    if (apiKey.isBlank()) return Result.failure(IllegalStateException("API 키가 없어요. 설정에서 Gemini API 키를 입력하거나 GitHub Secret GEMINI_API_KEY를 등록해 주세요."))
    if (personas.isEmpty()) return Result.failure(IllegalStateException("먼저 캐릭터를 만들어 주세요."))
    val characterPrompt = personas.joinToString("\n\n") {
        "이름: " + it.name + "\n소개: " + it.tagline + "\n성격/말투: " + it.personality
    }
    val system = """너는 개인용 AI 캐릭터 롤플레이 앱의 대화 엔진이다.
다음 캐릭터 설정을 정확히 연기한다.
$characterPrompt

규칙:
- 사용자의 말에 자연스럽고 몰입감 있게 반응하며 단답만 하지 말고 상황과 감정을 풍부하게 표현한다.
- 캐릭터의 대사와 *행동 묘사*를 함께 사용할 수 있다. 행동 묘사는 별표 한 쌍으로 감싼다.
- 사용자가 @캐릭터이름: 내용 형식으로 입력하면 해당 캐릭터가 말하거나 행동한 것으로 취급하고, 그 상황을 이어서 반응한다.
- 여러 캐릭터가 참여할 때는 각 발화를 '캐릭터이름: 대사' 형식으로 구분한다. 한 명만 답할 때는 이름 접두사를 생략해도 된다.
- 설정에 없는 과거 사실을 임의로 확정하지 말고 대화 맥락을 유지한다.
- 모든 등장인물은 명시적으로 성인(만 18세 이상)인 허구의 인물로만 다룬다. 미성년자 성적 상황은 만들지 않는다.
- 공개 커뮤니티나 랭킹을 언급하지 않는다.
"""
    val contents = JSONArray()
    history.takeLast(40).forEach { line ->
        val role = if (line.fromCharacter) "model" else "user"
        val label = if (line.fromCharacter) line.speaker + ": " else ""
        contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", label + line.body))))
    }
    val body = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        .put("contents", contents)
        .put("generationConfig", JSONObject().put("temperature", 0.95).put("topP", 0.95).put("maxOutputTokens", 1800))
    val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    val media = "application/json; charset=utf-8".toMediaType()
    var lastError = "요청 실패"
    for (attempt in 0..3) {
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey)
                .post(body.toString().toRequestBody(media))
                .build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = try { JSONObject(responseText).optJSONObject("error")?.optString("message") ?: "HTTP " + response.code } catch (_: Exception) { "HTTP " + response.code }
                    if ((response.code == 429 || response.code == 503 || response.code >= 500) && attempt < 3) {
                        Thread.sleep(longArrayOf(1000, 2000, 4000)[attempt])
                    } else return Result.failure(IllegalStateException(lastError))
                } else {
                    val root = JSONObject(responseText)
                    val text = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                    if (text.isBlank()) return Result.failure(IllegalStateException("AI 응답이 비어 있어요. 다시 시도해 주세요."))
                    val match = Regex("^([^:\\n]{1,24}):\\s*([\\s\\S]+)$").find(text.trim())
                    return Result.success(if (match != null && personas.any { it.name == match.groupValues[1].trim() }) match.groupValues[1].trim() to match.groupValues[2].trim() else (directedTo ?: personas.first().name) to text.trim())
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: "네트워크 오류"
            if (attempt < 3) Thread.sleep(longArrayOf(1000, 2000, 4000)[attempt]) else return Result.failure(IllegalStateException(lastError))
        }
    }
    return Result.failure(IllegalStateException(lastError))
}


private fun generatePersonaDetails(apiKey: String, concept: String, tagline: String): Result<Persona> {
    return try {
        val prompt = """캐릭터 프로필을 만들어라. 사용자가 준 아이디어를 확장하되 허구의 성인 캐릭터로 작성한다.
아이디어: $concept
추가 설명: $tagline
반드시 JSON 객체 하나만 출력한다. 키는 name, tagline, personality, greeting 이다.
personality는 말투, 성격, 배경, 사용자와의 관계를 포함해 구체적으로 작성한다.
"""
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 1200))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try { JSONObject(raw).optJSONObject("error")?.optString("message") ?: "HTTP " + response.code } catch (_: Exception) { "HTTP " + response.code }
                return Result.failure(IllegalStateException(message))
            }
            val answer = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            val jsonText = Regex("\\{[\\s\\S]*\\}").find(answer)?.value ?: return Result.failure(IllegalStateException("AI가 올바른 프로필을 반환하지 않았어요. 다시 시도해 주세요."))
            val o = JSONObject(jsonText)
            Result.success(Persona(UUID.randomUUID().toString(), o.optString("name", concept).ifBlank { concept.ifBlank { "새 캐릭터" } }, o.optString("tagline"), o.optString("personality"), o.optString("greeting", "안녕하세요!")))
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "캐릭터 생성 중 오류가 발생했어요."))
    }
}


private fun actionStyled(value: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        while (cursor < value.length) {
            val start = value.indexOf('*', cursor)
            if (start < 0) {
                append(value.substring(cursor))
                break
            }
            val end = value.indexOf('*', start + 1)
            if (end < 0) {
                append(value.substring(cursor))
                break
            }
            append(value.substring(cursor, start))
            pushStyle(androidx.compose.ui.text.SpanStyle(
                color = Muted,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            ))
            append(value.substring(start + 1, end))
            pop()
            cursor = end + 1
        }
    }
}
