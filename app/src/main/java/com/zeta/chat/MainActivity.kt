package com.zeta.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
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
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Panel2 = Color(0xFF292832)
private val Accent = Color(0xFFB8A1FF)
private val MainText = Color(0xFFF4F1FA)
private val Muted = Color(0xFF9A96A5)
private val Danger = Color(0xFFFF8585)

data class Persona(val id: String, val name: String, val tagline: String, val personality: String, val greeting: String, val avatarUri: String = "")
data class ChatLine(val id: String, val speaker: String, val body: String, val fromCharacter: Boolean, val time: Long = System.currentTimeMillis())
data class ChatRoom(val id: String, val title: String, val personaIds: List<String>, val messages: List<ChatLine>, val updatedAt: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onBackground = MainText, onSurface = MainText)) {
                ZetaApp()
            }
        }
    }
}

@Composable
private fun ZetaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zeta_local", 0) }
    val scope = rememberCoroutineScope()

    val personas = remember {
        mutableStateListOf<Persona>().apply {
            val raw = prefs.getString("personas_v2", prefs.getString("personas", null))
            if (raw.isNullOrBlank()) {
                add(Persona("default", "하나", "다정하고 장난기 있는 대화 상대", "친근하고 자연스럽게 말한다. 상대의 말에 적극적으로 반응하고 대화를 이어간다.", "안녕! 오늘은 어떤 이야기를 해볼까?"))
            } else runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Persona(o.optString("id"), o.optString("name"), o.optString("tagline"), o.optString("personality"), o.optString("greeting"), o.optString("avatarUri")))
                }
            }
        }
    }

    val rooms = remember {
        mutableStateListOf<ChatRoom>().apply {
            val raw = prefs.getString("rooms", null)
            if (!raw.isNullOrBlank()) runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) add(roomFromJson(arr.getJSONObject(i)))
            } else {
                val old = prefs.getString("messages", null)
                if (!old.isNullOrBlank()) runCatching {
                    val arr = JSONArray(old)
                    val lines = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        ChatLine(UUID.randomUUID().toString(), o.optString("speaker"), o.optString("body"), o.optBoolean("character"))
                    }
                    if (lines.isNotEmpty()) add(ChatRoom(UUID.randomUUID().toString(), "이전 대화", personas.map { it.id }, lines))
                }
            }
        }
    }

    var page by remember { mutableStateOf("home") }
    var currentRoomId by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var adultMode by remember { mutableStateOf(prefs.getBoolean("adult_mode", true)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var showNewChat by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var showDeleteRoom by remember { mutableStateOf(false) }
    var showDeletePersona by remember { mutableStateOf(false) }
    var showRenameRoom by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var selectedForNewChat by remember { mutableStateOf(setOf<String>()) }
    var selectedForRoom by remember { mutableStateOf(setOf<String>()) }
    var deletingPersona by remember { mutableStateOf<Persona?>(null) }
    val currentRoom = rooms.firstOrNull { it.id == currentRoomId }
    val activePeople = currentRoom?.personaIds?.mapNotNull { id -> personas.firstOrNull { it.id == id } } ?: emptyList()

    fun persist() {
        val p = JSONArray()
        personas.forEach { x ->
            p.put(JSONObject().put("id", x.id).put("name", x.name).put("tagline", x.tagline).put("personality", x.personality).put("greeting", x.greeting).put("avatarUri", x.avatarUri))
        }
        val r = JSONArray()
        rooms.forEach { r.put(roomToJson(it)) }
        prefs.edit().putString("personas_v2", p.toString()).putString("rooms", r.toString()).apply()
    }

    fun updateRoom(id: String, transform: (ChatRoom) -> ChatRoom) {
        val i = rooms.indexOfFirst { it.id == id }
        if (i >= 0) {
            rooms[i] = transform(rooms[i])
            persist()
        }
    }

    fun createRoom(ids: Set<String>) {
        val chosen = personas.filter { ids.contains(it.id) }
        if (chosen.isEmpty()) return
        val room = ChatRoom(UUID.randomUUID().toString(), chosen.joinToString(" · ") { it.name }, chosen.map { it.id },
            chosen.map { ChatLine(UUID.randomUUID().toString(), it.name, it.greeting, true) })
        rooms.add(0, room)
        currentRoomId = room.id
        page = "chat"
        persist()
        showNewChat = false
    }

    fun sendMessage(raw: String) {
        val room = rooms.firstOrNull { it.id == currentRoomId } ?: return
        val typed = raw.trim()
        if (typed.isBlank() || busy) return
        val directed = Regex("^@([^:：]+)[:：]\\s*([\\s\\S]*)$").find(typed)
        val targetName = directed?.groupValues?.get(1)?.trim()
        val target = personas.firstOrNull { it.name.equals(targetName, true) }
        val content = if (directed != null && target != null) directed.groupValues[2].trim() else typed
        val userLine = if (target != null) ChatLine(UUID.randomUUID().toString(), target.name, content, true)
            else ChatLine(UUID.randomUUID().toString(), "나", typed, false)
        val updatedIds = if (target != null && !room.personaIds.contains(target.id)) room.personaIds + target.id else room.personaIds
        updateRoom(room.id) { it.copy(personaIds = updatedIds, messages = it.messages + userLine, updatedAt = System.currentTimeMillis()) }
        draft = ""
        busy = true
        error = ""
        scope.launch {
            val latest = rooms.firstOrNull { it.id == room.id } ?: room
            val people = latest.personaIds.mapNotNull { id -> personas.firstOrNull { it.id == id } }
            val result = withContext(Dispatchers.IO) {
                askZeta(apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }, people, latest.messages, target?.name, adultMode)
            }
            if (result.isSuccess) {
                updateRoom(room.id) { it.copy(messages = it.messages + result.getOrThrow(), updatedAt = System.currentTimeMillis()) }
            } else error = result.exceptionOrNull()?.message ?: "AI 응답을 가져오지 못했어요."
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page == "chat") {
                    IconButton(onClick = { page = "home"; error = "" }) { Icon(Icons.Default.ArrowBack, null, tint = MainText) }
                    Column(Modifier.weight(1f)) {
                        Text(currentRoom?.title ?: "대화", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(activePeople.joinToString(", ") { it.name }.ifBlank { "참여 캐릭터 없음" }, color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { renameDraft = currentRoom?.title ?: ""; showRenameRoom = true }) { Icon(Icons.Default.Edit, "이름 변경", tint = Muted) }
                    IconButton(onClick = { showParticipants = true; selectedForRoom = currentRoom?.personaIds?.toSet() ?: emptySet() }) { Icon(Icons.Default.MoreVert, "참여 설정", tint = Muted) }
                } else {
                    Text("ZETA", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "설정", tint = Muted) }
                }
            }

            if (page == "home") {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Text("나만의 캐릭터 채팅", color = MainText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("캐릭터와 대화방을 만들고, 모든 기록은 이 기기에 저장하세요.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("캐릭터", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editingPersona = null; showPersonaEditor = true }) {
                            Icon(Icons.Default.Add, null, tint = Accent)
                            Text("캐릭터 만들기", color = Accent)
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(personas, key = { it.id }) { persona ->
                            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(persona, 54)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f).clickable { createRoom(setOf(persona.id)) }) {
                                    Text(persona.name, color = MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(persona.tagline.ifBlank { "나만의 AI 캐릭터" }, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { editingPersona = persona; showPersonaEditor = true }) { Icon(Icons.Default.Edit, "편집", tint = Muted) }
                                IconButton(onClick = { deletingPersona = persona; showDeletePersona = true }) { Icon(Icons.Default.Delete, "삭제", tint = Muted) }
                            }
                        }
                        if (personas.isEmpty()) item { Text("캐릭터를 먼저 만들어 주세요.", color = Muted) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("내 대화방", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selectedForNewChat = emptySet(); showNewChat = true }) {
                            Icon(Icons.Default.Add, null, tint = Accent)
                            Text("새 대화", color = Accent)
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(rooms, key = { it.id }) { room ->
                            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable {
                                currentRoomId = room.id
                                page = "chat"
                                error = ""
                            }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).background(Accent.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Accent)
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(room.title, color = MainText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(room.messages.lastOrNull()?.body ?: "대화를 시작해 보세요.", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { currentRoomId = room.id; showDeleteRoom = true }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                            }
                        }
                        if (rooms.isEmpty()) item { Text("아직 대화방이 없습니다. 새 대화를 눌러 시작하세요.", color = Muted) }
                    }
                }
            } else {
                val room = currentRoom
                if (room == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("대화방을 찾을 수 없습니다.", color = Muted) }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(room.messages.size, busy) { if (room.messages.isNotEmpty()) listState.animateScrollToItem(room.messages.lastIndex) }
                    LazyColumn(
                        state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 13.dp),
                        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        items(room.messages, key = { it.id }) { line -> ChatBubble(line) }
                        if (busy) item { Text("캐릭터가 생각하고 있어요…", color = Muted, fontSize = 12.sp) }
                    }
                    if (error.isNotBlank()) Text(error, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)
                            .background(Panel, RoundedCornerShape(22.dp)).padding(start = 15.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            textStyle = TextStyle(color = MainText, fontSize = 15.sp, lineHeight = 21.sp),
                            cursorBrush = SolidColor(Accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            maxLines = 5,
                            decorationBox = { inner ->
                                if (draft.isEmpty()) Text("@캐릭터이름: 대사 / 메시지 입력", color = Muted, fontSize = 13.sp)
                                inner()
                            }
                        )
                        IconButton(onClick = { sendMessage(draft) }, enabled = draft.isNotBlank() && !busy) {
                            Icon(Icons.Default.Send, "전송", tint = if (draft.isBlank() || busy) Muted else Accent)
                        }
                    }
                }
            }
        }
    }

    if (showPersonaEditor) {
        PersonaEditor(initial = editingPersona, apiKey = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY },
            onDismiss = { showPersonaEditor = false },
            onSave = { p ->
                val i = personas.indexOfFirst { it.id == p.id }
                if (i >= 0) personas[i] = p else personas.add(p)
                persist()
                showPersonaEditor = false
            })
    }

    if (showNewChat) {
        AlertDialog(
            onDismissRequest = { showNewChat = false }, containerColor = Panel,
            title = { Text("새 대화방 만들기", color = MainText, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("함께 대화할 캐릭터를 선택하세요.", color = Muted, fontSize = 12.sp)
                    personas.forEach { persona ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedForNewChat.contains(persona.id), onCheckedChange = { checked ->
                                selectedForNewChat = if (checked) selectedForNewChat + persona.id else selectedForNewChat - persona.id
                            }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                            Avatar(persona, 32)
                            Spacer(Modifier.width(8.dp))
                            Text(persona.name, color = MainText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { createRoom(selectedForNewChat) }, enabled = selectedForNewChat.isNotEmpty()) { Text("만들기", color = Accent) } },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text("취소", color = Muted) } }
        )
    }

    if (showParticipants) {
        AlertDialog(
            onDismissRequest = { showParticipants = false }, containerColor = Panel,
            title = { Text("대화 참여 캐릭터", color = MainText, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    personas.forEach { persona ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedForRoom.contains(persona.id), onCheckedChange = { checked ->
                                selectedForRoom = if (checked) selectedForRoom + persona.id else selectedForRoom - persona.id
                            }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                            Avatar(persona, 32)
                            Spacer(Modifier.width(8.dp))
                            Text(persona.name, color = MainText)
                        }
                    }
                    Text("선택한 캐릭터들은 같은 대화 맥락을 공유합니다.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedForRoom.isNotEmpty()) updateRoom(currentRoomId) { it.copy(personaIds = selectedForRoom.toList()) }
                    showParticipants = false
                }) { Text("완료", color = Accent) }
            }
        )
    }

    if (showSettings) {
        var keyDraft by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false }, containerColor = Panel,
            title = { Text("설정", color = MainText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Gemini API 키", color = MainText, fontSize = 13.sp)
                    OutlinedTextField(value = keyDraft, onValueChange = { keyDraft = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), placeholder = { Text("API 키 입력") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("모델: Gemini 2.5 Flash-Lite", color = Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("성인 롤플레이 모드", color = MainText, fontSize = 13.sp)
                            Text("기본 활성화 · 캐릭터는 성인으로 설정", color = Muted, fontSize = 11.sp)
                        }
                        Switch(checked = adultMode, onCheckedChange = {
                            adultMode = it
                            prefs.edit().putBoolean("adult_mode", it).apply()
                        })
                    }
                    Text("캐릭터·대화방·대화 기록은 이 기기에 저장됩니다. 공개 피드, 랭킹, 댓글, 유료 재화는 없습니다.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                apiKey = keyDraft.trim()
                prefs.edit().putString("api_key", apiKey).apply()
                showSettings = false
            }) { Text("저장", color = Accent) } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("닫기", color = Muted) } }
        )
    }

    if (showDeleteRoom) {
        AlertDialog(onDismissRequest = { showDeleteRoom = false }, containerColor = Panel,
            title = { Text("대화방 삭제", color = MainText) },
            text = { Text("이 대화방과 대화 기록을 삭제할까요?", color = Muted) },
            confirmButton = { TextButton(onClick = {
                rooms.removeAll { it.id == currentRoomId }
                if (currentRoomId == currentRoom?.id) currentRoomId = ""
                persist()
                showDeleteRoom = false
            }) { Text("삭제", color = Danger) } },
            dismissButton = { TextButton(onClick = { showDeleteRoom = false }) { Text("취소", color = Muted) } })
    }

    if (showDeletePersona) {
        AlertDialog(onDismissRequest = { showDeletePersona = false }, containerColor = Panel,
            title = { Text("캐릭터 삭제", color = MainText) },
            text = { Text((deletingPersona?.name ?: "이 캐릭터") + "를 삭제할까요? 기존 대화 기록은 보존됩니다.", color = Muted) },
            confirmButton = { TextButton(onClick = {
                deletingPersona?.let { persona ->
                    personas.removeAll { it.id == persona.id }
                    rooms.indices.forEach { i -> rooms[i] = rooms[i].copy(personaIds = rooms[i].personaIds.filterNot { it == persona.id }) }
                    persist()
                }
                showDeletePersona = false
            }) { Text("삭제", color = Danger) } },
            dismissButton = { TextButton(onClick = { showDeletePersona = false }) { Text("취소", color = Muted) } })
    }

    if (showRenameRoom) {
        AlertDialog(onDismissRequest = { showRenameRoom = false }, containerColor = Panel,
            title = { Text("대화방 이름 변경", color = MainText) },
            text = { OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                if (renameDraft.isNotBlank()) updateRoom(currentRoomId) { it.copy(title = renameDraft.trim()) }
                showRenameRoom = false
            }) { Text("저장", color = Accent) } },
            dismissButton = { TextButton(onClick = { showRenameRoom = false }) { Text("취소", color = Muted) } })
    }
}

@Composable
private fun Avatar(persona: Persona, size: Int) {
    if (persona.avatarUri.isNotBlank()) {
        AsyncImage(model = persona.avatarUri, contentDescription = persona.name,
            modifier = Modifier.size(size.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(Modifier.size(size.dp).background(Accent.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(persona.name.take(1), color = Accent, fontSize = (size / 2.3f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine) {
    val mine = !line.fromCharacter
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(line.speaker, color = if (mine) Muted else Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        Text(text = actionStyled(line.body), color = MainText, fontSize = 15.sp, lineHeight = 23.sp,
            modifier = Modifier.widthIn(max = 340.dp).background(if (mine) Color(0xFF393044) else Panel, RoundedCornerShape(17.dp)).padding(horizontal = 14.dp, vertical = 11.dp))
    }
}

@Composable
private fun PersonaEditor(initial: Persona?, apiKey: String, onDismiss: () -> Unit, onSave: (Persona) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var tagline by remember { mutableStateOf(initial?.tagline ?: "") }
    var personality by remember { mutableStateOf(initial?.personality ?: "") }
    var greeting by remember { mutableStateOf(initial?.greeting ?: "") }
    var avatarUri by remember { mutableStateOf(initial?.avatarUri ?: "") }
    var generating by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            avatarUri = uri.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text(if (initial == null) "캐릭터 만들기" else "캐릭터 편집", color = MainText, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarUri.isNotBlank()) AsyncImage(model = avatarUri, contentDescription = "캐릭터 이미지",
                        modifier = Modifier.size(70.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Box(Modifier.size(70.dp).background(Accent.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.take(1).ifBlank { "?" }, color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.Image, null, tint = Accent)
                        Text("이미지 선택", color = Accent)
                    }
                }
                EditorField("캐릭터 이름", name, { name = it })
                EditorField("한 줄 소개", tagline, { tagline = it })
                Button(onClick = {
                    if (apiKey.isBlank()) generateError = "설정에서 Gemini API 키를 먼저 등록해 주세요."
                    else scope.launch {
                        generating = true
                        generateError = ""
                        val result = withContext(Dispatchers.IO) { generatePersonaDetails(apiKey, name, tagline) }
                        if (result.isSuccess) {
                            val p = result.getOrThrow()
                            name = p.name
                            tagline = p.tagline
                            personality = p.personality
                            greeting = p.greeting
                        } else generateError = result.exceptionOrNull()?.message ?: "생성 실패"
                        generating = false
                    }
                }, enabled = !generating, colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Accent)) {
                    if (generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("AI로 캐릭터 설정 만들기")
                }
                if (generateError.isNotBlank()) Text(generateError, color = Danger, fontSize = 11.sp)
                EditorField("성격·말투·배경 설정", personality, { personality = it }, 4)
                EditorField("첫 인사", greeting, { greeting = it }, 2)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (name.isNotBlank()) onSave(Persona(initial?.id ?: UUID.randomUUID().toString(), name.trim(), tagline.trim(), personality.trim(), greeting.trim().ifBlank { "안녕하세요!" }, avatarUri))
        }) { Text("저장", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Muted) } }
    )
}

@Composable
private fun EditorField(label: String, value: String, onValue: (String) -> Unit, minLines: Int = 1) {
    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp, bottom = 3.dp))
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), minLines = minLines,
        textStyle = TextStyle(color = MainText, fontSize = 14.sp))
}

private fun askZeta(apiKey: String, people: List<Persona>, history: List<ChatLine>, directedTo: String?, adultMode: Boolean): Result<List<ChatLine>> {
    if (apiKey.isBlank()) return Result.failure(IllegalStateException("API 키가 없어요. 설정에서 Gemini API 키를 입력해 주세요."))
    if (people.isEmpty()) return Result.failure(IllegalStateException("이 대화방에 참여 중인 캐릭터가 없어요. 참여 설정에서 캐릭터를 선택해 주세요."))
    val prompt = people.joinToString("\n\n") { "이름: " + it.name + "\n소개: " + it.tagline + "\n성격/말투/배경: " + it.personality }
    val adultGuidance = if (adultMode) "성인 모드가 켜져 있다. 모든 인물은 명시적인 성인이며, 성인 인물 간 성숙한 로맨스와 성인 주제를 다룰 수 있다. 미성년자를 성적 상황에 포함하지 않는다." else "일반 대화 모드로 유지하고 노골적인 성인 주제는 피한다."
    val system = """너는 개인용 AI 캐릭터 채팅 앱의 대화 엔진이다.
다음 캐릭터들을 한 대화방에서 연기한다.
$prompt

대화 규칙:
- 캐릭터마다 설정된 성격, 말투, 배경, 관계를 일관되게 지킨다.
- 자연스럽고 몰입감 있게 반응한다. 짧은 단답만 하지 말고 대사와 상황 묘사를 충분히 작성한다.
- *행동 묘사*는 별표 한 쌍으로 감싸서 대사와 구분한다.
- 사용자가 @캐릭터이름: 내용 형식으로 보낸 메시지는 해당 캐릭터가 직접 말하거나 행동한 것으로 취급한다. 멘션이나 호출 명령으로 오해하지 않는다.
- 여러 캐릭터가 참여하는 경우 한 응답에 여러 명이 대화할 수 있다. 각 캐릭터 발화는 반드시 '캐릭터이름: 내용' 형식으로 새 줄에서 시작한다.
- 한 명만 말할 때도 '캐릭터이름: 내용' 형식을 사용한다.
- 사용자가 쓴 말이나 캐릭터가 이미 한 말을 그대로 반복하지 말고 다음 상황을 이어간다.
- 대화의 앞뒤 맥락과 인물 관계를 유지한다. 설정에 없는 사실을 이미 확정된 사실처럼 지어내지 않는다.
- $adultGuidance
- 공개 커뮤니티, 랭킹, 댓글, 코인, 유료 결제 기능은 존재하지 않는 앱이다.
"""
    val contents = JSONArray()
    if (history.firstOrNull()?.fromCharacter == true) {
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "대화가 시작되었다. 캐릭터가 먼저 인사를 건넸다."))))
    }
    history.takeLast(40).forEach { line ->
        val role = if (line.fromCharacter) "model" else "user"
        val content = if (line.fromCharacter) line.speaker + ": " + line.body else line.body
        contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", content))))
    }
    val body = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        .put("contents", contents)
        .put("generationConfig", JSONObject().put("temperature", 0.95).put("topP", 0.95).put("maxOutputTokens", 2400))
    val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    val media = "application/json; charset=utf-8".toMediaType()
    var lastError = "요청 실패"
    for (attempt in 0..3) {
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey)
                .post(body.toString().toRequestBody(media)).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = try { JSONObject(raw).optJSONObject("error")?.optString("message") ?: "HTTP " + response.code } catch (_: Exception) { "HTTP " + response.code }
                    if ((response.code == 429 || response.code == 503 || response.code >= 500) && attempt < 3) Thread.sleep(longArrayOf(1000, 2000, 4000)[attempt])
                    else return Result.failure(IllegalStateException(lastError))
                } else {
                    val answer = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                    if (answer.isBlank()) return Result.failure(IllegalStateException("AI 응답이 비어 있어요. 다시 시도해 주세요."))
                    val parsed = Regex("(?ms)^\\s*([^:\\n]{1,28}):\\s*(.*?)(?=^\\s*[^:\\n]{1,28}:\\s*|\\z)")
                        .findAll(answer.trim()).mapNotNull { m ->
                            val speaker = m.groupValues[1].trim()
                            val bodyText = m.groupValues[2].trim()
                            val person = people.firstOrNull { it.name.equals(speaker, true) }
                            if (person != null && bodyText.isNotBlank()) ChatLine(UUID.randomUUID().toString(), person.name, bodyText, true) else null
                        }.toList()
                    if (parsed.isNotEmpty()) return Result.success(parsed)
                    val fallback = people.firstOrNull { it.name.equals(directedTo, true) } ?: people.first()
                    return Result.success(listOf(ChatLine(UUID.randomUUID().toString(), fallback.name, answer.trim(), true)))
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: "네트워크 오류"
            if (attempt < 3) Thread.sleep(longArrayOf(1000, 2000, 4000)[attempt])
            else return Result.failure(IllegalStateException(lastError))
        }
    }
    return Result.failure(IllegalStateException(lastError))
}

private fun generatePersonaDetails(apiKey: String, concept: String, tagline: String): Result<Persona> {
    return try {
        val prompt = "사용자가 입력한 아이디어를 바탕으로 매력적이고 일관된 허구의 성인 캐릭터 프로필을 만든다.\n" +
            "아이디어: " + concept + "\n추가 설명: " + tagline + "\n" +
            "JSON 객체 하나만 출력한다. 키는 name, tagline, personality, greeting 이다.\n" +
            "personality에는 성격, 말투, 배경, 관계, 대화 습관을 구체적으로 적는다."
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 1400))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try { JSONObject(raw).optJSONObject("error")?.optString("message") ?: "HTTP " + response.code } catch (_: Exception) { "HTTP " + response.code }
                return Result.failure(IllegalStateException(message))
            }
            val answer = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            val jsonText = Regex("\\{[\\s\\S]*\\}").find(answer)?.value ?: return Result.failure(IllegalStateException("AI가 프로필을 만들지 못했어요. 다시 시도해 주세요."))
            val o = JSONObject(jsonText)
            Result.success(Persona(UUID.randomUUID().toString(), o.optString("name", concept).ifBlank { concept.ifBlank { "새 캐릭터" } },
                o.optString("tagline"), o.optString("personality"), o.optString("greeting", "안녕하세요!")))
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "캐릭터 생성 오류"))
    }
}

private fun actionStyled(value: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        while (cursor < value.length) {
            val start = value.indexOf('*', cursor)
            if (start < 0) { append(value.substring(cursor)); break }
            val end = value.indexOf('*', start + 1)
            if (end < 0) { append(value.substring(cursor)); break }
            append(value.substring(cursor, start))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            append(value.substring(start + 1, end))
            pop()
            cursor = end + 1
        }
    }
}

private fun roomToJson(room: ChatRoom): JSONObject {
    val lines = JSONArray()
    room.messages.forEach { lines.put(JSONObject().put("id", it.id).put("speaker", it.speaker).put("body", it.body).put("character", it.fromCharacter).put("time", it.time)) }
    val ids = JSONArray()
    room.personaIds.forEach { ids.put(it) }
    return JSONObject().put("id", room.id).put("title", room.title).put("personaIds", ids).put("messages", lines).put("updatedAt", room.updatedAt)
}

private fun roomFromJson(o: JSONObject): ChatRoom {
    val idsArray = o.optJSONArray("personaIds") ?: JSONArray()
    val ids = (0 until idsArray.length()).map { idsArray.optString(it) }
    val lineArray = o.optJSONArray("messages") ?: JSONArray()
    val lines = (0 until lineArray.length()).map { i ->
        val x = lineArray.getJSONObject(i)
        ChatLine(x.optString("id", UUID.randomUUID().toString()), x.optString("speaker"), x.optString("body"), x.optBoolean("character"), x.optLong("time", System.currentTimeMillis()))
    }
    return ChatRoom(o.optString("id", UUID.randomUUID().toString()), o.optString("title", "대화방"), ids, lines, o.optLong("updatedAt", System.currentTimeMillis()))
}
