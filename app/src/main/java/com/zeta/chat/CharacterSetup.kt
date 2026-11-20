package com.zeta.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CharacterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val age: String = "",
    val background: String = "",
    val personality: String = "",
    val appearance: String = "",
    val speechStyle: String = "",
    val relationship: String = "",
    val intro: String = "",
    val examples: String = "",
    val extra: String = ""
) {
    fun toPromptText(): String = """
        이름: $name
        나이: $age
        배경 이야기: $background
        성격 및 특성: $personality
        외모: $appearance
        말투 및 대화 습관: $speechStyle
        사용자와의 관계: $relationship
        첫 인트로 대사: $intro
        상황별 대사 예시: $examples
        기타 설정: $extra
    """.trimIndent()
}

fun charactersToJson(characters: List<CharacterProfile>): String {
    val array = JSONArray()
    characters.forEach { c ->
        array.put(JSONObject()
            .put("id", c.id).put("name", c.name).put("age", c.age)
            .put("background", c.background).put("personality", c.personality)
            .put("appearance", c.appearance).put("speechStyle", c.speechStyle)
            .put("relationship", c.relationship).put("intro", c.intro)
            .put("examples", c.examples).put("extra", c.extra))
    }
    return array.toString()
}

fun charactersFromJson(raw: String?): List<CharacterProfile> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            CharacterProfile(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"), age = o.optString("age"),
                background = o.optString("background"), personality = o.optString("personality"),
                appearance = o.optString("appearance"), speechStyle = o.optString("speechStyle"),
                relationship = o.optString("relationship"), intro = o.optString("intro"),
                examples = o.optString("examples"), extra = o.optString("extra")
            )
        }
    } catch (_: Exception) { emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterManagerScreen(
    initialCharacters: List<CharacterProfile>,
    onBack: () -> Unit,
    onSave: (List<CharacterProfile>) -> Unit,
    onGenerate: suspend (String) -> Result<CharacterProfile>
) {
    val characters = remember { mutableStateListOf<CharacterProfile>().apply { addAll(initialCharacters) } }
    var editing by remember { mutableStateOf<CharacterProfile?>(null) }
    var brief by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun saveCharacter(c: CharacterProfile) {
        val index = characters.indexOfFirst { it.id == c.id }
        if (index >= 0) characters[index] = c else characters.add(c)
        onSave(characters.toList())
        editing = null
        brief = ""
        message = "캐릭터를 저장했어."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "캐릭터 설정" else "캐릭터 검토 및 수정") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editing != null) editing = null else onBack()
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                }
            )
        },
        containerColor = Color(0xFF101116)
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (editing == null) {
                Text("새 캐릭터 만들기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("배경 이야기나 성격을 편하게 적어 줘. 부족한 항목은 AI가 초안을 채워 줄 거야.", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(
                    value = brief, onValueChange = { brief = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    label = { Text("캐릭터의 배경 이야기와 특성") },
                    placeholder = { Text("예: 오래된 마법 도서관을 지키는 안내인. 차분하지만 호기심이 많고…") },
                    minLines = 5
                )
                Button(
                    onClick = {
                        if (brief.isNotBlank() && !generating) {
                            generating = true
                            message = "AI가 캐릭터 초안을 만드는 중..."
                            scope.launch {
                                onGenerate(brief).onSuccess {
                                    editing = it
                                    message = "초안이 만들어졌어. 저장 전에 내용을 확인해 줘."
                                }.onFailure {
                                    message = it.message ?: "캐릭터 생성 실패"
                                }
                                generating = false
                            }
                        }
                    },
                    enabled = brief.isNotBlank() && !generating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (generating) "초안 생성 중..." else "AI로 캐릭터 초안 만들기")
                }
                if (message.isNotBlank()) Text(message, color = Color(0xFFB8C7F0), fontSize = 12.sp)
                HorizontalDivider(color = Color.DarkGray)
                Text("저장된 캐릭터 ${characters.size}", fontWeight = FontWeight.Bold, color = Color.White)
                characters.forEach { character ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { editing = character },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF20232D))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(character.name.ifBlank { "이름 없는 캐릭터" }, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(character.personality.take(90), color = Color.LightGray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { editing = character }) {
                                Icon(Icons.Default.Edit, contentDescription = "수정", tint = Color(0xFFB8C7F0))
                            }
                            IconButton(onClick = {
                                characters.removeAll { it.id == character.id }
                                onSave(characters.toList())
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Gray)
                            }
                        }
                    }
                }
                if (characters.isEmpty()) Text("아직 저장된 캐릭터가 없어.", color = Color.Gray)
            } else {
                val c = editing!!
                Text("AI가 만든 초안이야. 각 항목을 직접 수정한 다음 저장해 줘.", color = Color.LightGray, fontSize = 13.sp)
                var name by remember(c.id) { mutableStateOf(c.name) }
                var age by remember(c.id) { mutableStateOf(c.age) }
                var background by remember(c.id) { mutableStateOf(c.background) }
                var personality by remember(c.id) { mutableStateOf(c.personality) }
                var appearance by remember(c.id) { mutableStateOf(c.appearance) }
                var speech by remember(c.id) { mutableStateOf(c.speechStyle) }
                var relationship by remember(c.id) { mutableStateOf(c.relationship) }
                var intro by remember(c.id) { mutableStateOf(c.intro) }
                var examples by remember(c.id) { mutableStateOf(c.examples) }
                var extra by remember(c.id) { mutableStateOf(c.extra) }

                @Composable
                fun field(label: String, value: String, update: (String) -> Unit, minLines: Int = 2) {
                    OutlinedTextField(value, update, modifier = Modifier.fillMaxWidth(),
                        label = { Text(label) }, minLines = minLines)
                }
                field("캐릭터 이름", name, { name = it }, 1)
                field("나이 (모든 등장인물은 성인)", age, { age = it }, 1)
                field("배경 이야기", background, { background = it }, 3)
                field("성격 및 특성", personality, { personality = it }, 3)
                field("외모", appearance, { appearance = it }, 2)
                field("말투 및 대화 습관", speech, { speech = it }, 3)
                field("사용자와의 관계", relationship, { relationship = it }, 2)
                field("첫 인트로 대사", intro, { intro = it }, 2)
                field("상황별 대사 예시", examples, { examples = it }, 3)
                field("기타 설정", extra, { extra = it }, 2)
                Text("프로필은 최대 24,000자까지 사용할 수 있어.", color = Color.Gray, fontSize = 11.sp)
                Button(
                    onClick = {
                        val updated = CharacterProfile(c.id, name, age, background, personality,
                            appearance, speech, relationship, intro, examples, extra)
                        saveCharacter(updated)
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("확인하고 캐릭터 저장") }
            }
        }
    }
}
