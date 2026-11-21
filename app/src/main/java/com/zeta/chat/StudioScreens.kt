package com.zeta.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF111111)
private val PanelColor = Color(0xFF202020)
private val Purple = Color(0xFF7650E8)
private val Muted = Color(0xFF929292)

@Composable
fun HomeStudioScreen(plotTitle:String, characterCount:Int, chatCount:Int, onOpenPlot:()->Unit,
 onOpenChat:()->Unit, onOpenCharacters:()->Unit, onNewChat:()->Unit) {
 var tab by remember { mutableStateOf("홈") }
 var query by remember { mutableStateOf("") }
 var category by remember { mutableStateOf("전체") }
 val categories=listOf("전체","로맨스","판타지","일상","스릴러","SF")
 Scaffold(containerColor=Bg,bottomBar={
  NavigationBar(containerColor=Color(0xFF191919)) {
   listOf("홈","내 작품","만들기","보관함","설정").forEach { name ->
    NavigationBarItem(selected=tab==name,onClick={ if(name=="만들기") onOpenPlot() else tab=name },
     icon={Text(when(name){"홈"->"⌂";"내 작품"->"▤";"만들기"->"＋";"보관함"->"▣";else->"⚙"},fontSize=19.sp)},
     label={Text(name)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Purple,selectedTextColor=Color.White,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=Color(0xFF30244C)))
   }
  }
 }) { p ->
  Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(horizontal=18.dp, vertical=12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Text("zeta",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
    Spacer(Modifier.weight(1f))
    Text("⌕",fontSize=24.sp,color=Color.White,modifier=Modifier.clickable{tab="내 작품"}.padding(8.dp))
    Text("☰",fontSize=22.sp,color=Color.White,modifier=Modifier.clickable{tab="설정"}.padding(8.dp))
   }
   when(tab) {
    "홈" -> {
     Spacer(Modifier.height(18.dp))
     Text("상상한 이야기를 시작해 보세요",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color.White)
     Text("나만의 캐릭터와 세계를 만들고 대화해 보세요.",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
     Card(Modifier.fillMaxWidth().padding(top=18.dp).clickable(onClick=onNewChat),colors=CardDefaults.cardColors(containerColor=Color(0xFF30254A)),shape=RoundedCornerShape(18.dp)) {
      Column(Modifier.padding(20.dp)) {
       Text("✦  새로운 이야기",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
       Text("AI 캐릭터와 지금 바로 대화 시작",color=Color(0xFFD8CFF5),fontSize=13.sp,modifier=Modifier.padding(top=7.dp))
       Button(onClick=onNewChat,modifier=Modifier.padding(top=12.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple)){Text("대화 시작하기")}
      }
     }
     Text("추천 작품",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=25.dp,bottom=8.dp))
     listOf("오늘부터 시작하는 우리들의 이야기" to "일상 · 관계 · 선택에 따라 달라지는 이야기","별빛 아래 마법 학교" to "판타지 · 마법과 비밀을 탐험하는 세계","마지막 열차의 승객" to "미스터리 · 낯선 승객들과의 대화").forEachIndexed { i,item ->
      Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable(onClick=onOpenPlot),colors=CardDefaults.cardColors(containerColor=PanelColor),shape=RoundedCornerShape(15.dp)) {
       Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(66.dp).background(listOf(Color(0xFF51416B),Color(0xFF354B66),Color(0xFF655044))[i],RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Text(listOf("✦","✧","◇")[i],fontSize=28.sp,color=Color.White)}
        Column(Modifier.weight(1f).padding(start=12.dp)){Text(item.first,color=Color.White,fontWeight=FontWeight.Bold);Text(item.second,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=5.dp));Text("작품 둘러보기  ›",color=Color(0xFFC7B8FF),fontSize=11.sp,modifier=Modifier.padding(top=8.dp))}
       }
      }
     }
     Text("내 활동",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=20.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.padding(top=8.dp)) {
      Box(Modifier.weight(1f)){HomeCard("내 플롯",if(plotTitle.isBlank())"새 작품 만들기" else plotTitle,onOpenPlot)}
      Box(Modifier.weight(1f)){HomeCard("내 캐릭터","캐릭터 ${characterCount}명",onOpenCharacters)}
     }
     HomeCard("최근 대화","저장된 대화 ${chatCount}개 · 이어하기",onOpenChat)
    }
    "내 작품" -> {
     Text("내 작품",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=20.dp))
     OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().padding(top=12.dp),singleLine=true,placeholder={Text("내 작품·캐릭터 검색",color=Muted)},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Purple,unfocusedBorderColor=Color.DarkGray))
     Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.padding(vertical=12.dp)){categories.forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c)}) }}
     Text("내 작품 목록",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)
     listOf("첫눈에 반한 옆자리" to "로맨스 · 캐릭터 중심 이야기","용사의 귀환" to "판타지 · 모험과 선택","우리 동네 작은 카페" to "일상 · 편안한 대화","기억을 잃은 탐정" to "미스터리 · 추리").filter { query.isBlank() || it.first.contains(query,true) || it.second.contains(query,true) }.forEach { item ->
      HomeCard(item.first,item.second+" · 예시 작품 카드",onOpenPlot)
     }
     Text("현재는 탐색 UI와 샘플 카드 단계이며 실제 공개 작품 서버와 연결되지는 않았어.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
    }
    "보관함" -> {
     Text("보관함",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=20.dp))
     HomeCard("내 플롯",if(plotTitle.isBlank())"아직 저장한 작품이 없어" else plotTitle,onOpenPlot)
     HomeCard("내 대화","저장된 대화 $chatCount개",onOpenChat)
     HomeCard("내 캐릭터","등록된 캐릭터 $characterCount명",onOpenCharacters)
    }
    "설정" -> {
     Text("개인 설정",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(top=20.dp))
     StudioPanel { Text("개인용 ZETA",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("작품과 대화는 이 기기에 저장",color=Muted,fontSize=12.sp) }
     HomeCard("내 작품 관리","플롯 편집 및 설정",onOpenPlot)
     HomeCard("캐릭터 관리","캐릭터 생성·수정·삭제",onOpenCharacters)
     HomeCard("대화 기록","총 $chatCount개 대화",onOpenChat)
    }
   }
  }
 }
}

@Composable private fun HomeCard(title:String,sub:String,onClick:()->Unit) {
 Card(Modifier.fillMaxWidth().padding(top=12.dp).clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=PanelColor),shape=RoundedCornerShape(14.dp)) {
  Column(Modifier.padding(17.dp)) { Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp); Spacer(Modifier.height(7.dp)); Text(sub,color=Muted,fontSize=12.sp) }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotStudioScreen(title:String,onTitle:(String)->Unit,description:String,onDescription:(String)->Unit,
 tab:String,onTab:(String)->Unit,characters:List<CharacterProfile>,onCharacters:()->Unit,
 loreEntries:List<Pair<String,String>>,onLoreEntries:(List<Pair<String,String>>)->Unit,
 styleValues:Map<String,String>,onStyleValues:(Map<String,String>)->Unit,
 intro:String,onIntro:(String)->Unit,saved:Boolean,onSavePlot:()->Unit,
 onGenerateStudio:(String)->Unit,studioBusy:Boolean,studioMessage:String,pluginFlags:Map<String,Boolean>,onPluginToggle:(String,Boolean)->Unit,onBack:()->Unit) {
 val tabs=listOf("프롬프트","설정집","스타일","플러그인","인트로","소개","설정")
 Scaffold(containerColor=Bg,topBar={
  TopAppBar(title={Text("플롯",color=Color.White,fontWeight=FontWeight.Bold)},
   navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}},
   actions={TextButton(onClick={onGenerateStudio(tab)},enabled=!studioBusy){Text(if(studioBusy) "AI 작성 중" else "✨ AI로 만들기",color=Color(0xFFC7B8FF))};TextButton(onClick=onSavePlot){Text(if(saved) "저장됨" else "저장",color=Color.White)}},
   colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))
 }) { p ->
  Column(Modifier.fillMaxSize().padding(p)) {
   ScrollableTabRow(selectedTabIndex=tabs.indexOf(tab).coerceAtLeast(0),containerColor=Bg,contentColor=Color.White,edgePadding=8.dp,divider={}) {
    tabs.forEach { t -> Tab(selected=tab==t,onClick={onTab(t)},text={Text(t,color=if(tab==t) Color.White else Muted)}) }
   }
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    when(tab) {
     "프롬프트" -> {
      Text("기본 설정",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
      StudioPanel {
       StudioField("제목",title,onTitle,1,"제목을 입력해주세요.")
       StudioField("설명",description,onDescription,5,"상황, 관계, 세계관 등을 설명해주세요.")
       Text("고급 설정  ⌄",color=Color.LightGray)
      }
      Row(verticalAlignment=Alignment.CenterVertically) { Text("캐릭터",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("＋ 추가 / 편집",color=Color(0xFFC7B8FF),modifier=Modifier.clickable(onClick=onCharacters)) }
      StudioPanel {
       Text(characters.size.toString()+"/10 캐릭터",color=Muted)
       characters.forEach { c -> Text("◉  "+c.name+"     "+c.personality.take(48),color=Color.White,fontSize=13.sp) }
       Button(onClick=onCharacters,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF383838))) { Text("＋ 캐릭터 추가") }
      }
      Text("플레이하는 유저가 사용할 대화 프로필",color=Color.White,fontWeight=FontWeight.Bold)
      StudioPanel { Text("글자 수 제한에 포함되지 않는 사용자 프로필",color=Muted) }
     }
     "설정집" -> {
      Text("설정집을 연결해 주세요",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
       if (studioMessage.isNotBlank()) Text(studioMessage,color=Color(0xFFC7B8FF),fontSize=12.sp)
      Text("설정집에 등록한 키워드가 언급될 때 작성한 내용이 AI에게 전달됩니다.",color=Muted,fontSize=13.sp)
      Button(onClick={onLoreEntries(loreEntries+("" to ""))},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF3A3A3A))) { Text("＋ 설정집 연결 / 새 설정 추가") }
      Text("내 설정집  ›",color=Color.White,fontWeight=FontWeight.Bold)
      loreEntries.forEachIndexed { i,e ->
       StudioPanel {
        StudioField("제목",e.first,{v->onLoreEntries(loreEntries.toMutableList().also{it[i]=v to e.second})},1,"설정 이름")
        StudioField("내용",e.second,{v->onLoreEntries(loreEntries.toMutableList().also{it[i]=e.first to v})},4,"AI에게 전달할 설정 내용")
        Text("삭제",color=Color(0xFFFF8888),modifier=Modifier.clickable{onLoreEntries(loreEntries.filterIndexed{n,_->n!=i})})
       }
      }
     }
     "스타일" -> {
      Text("스타일을 자유롭게 조정해 보세요",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
       if (styleValues["AI 추가 지침"].orEmpty().isNotBlank()) StudioPanel { Text("AI 추가 지침",color=Color.White,fontWeight=FontWeight.Bold); Text(styleValues["AI 추가 지침"].orEmpty(),color=Muted) }
       if (studioMessage.isNotBlank()) Text(studioMessage,color=Color(0xFFC7B8FF),fontSize=12.sp)
      listOf("난이도" to listOf("쉬운","보통","어려운","극한"),"전개 속도" to listOf("빠른","자연스러운","느린"),"시점" to listOf("1인칭","2인칭","3인칭"),"시제" to listOf("과거","현재"),"응답 길이" to listOf("짧은","중간","긴","자동"),"표현 방식" to listOf("대화 더하기","기본","행동 더하기")).forEach { pair ->
       StudioPanel {
        Text(pair.first,color=Color.White,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
         pair.second.forEach { choice ->
          val active=styleValues[pair.first]==choice
          Box(Modifier.weight(1f).background(if(active) Color(0xFF302449) else Color(0xFF303030),RoundedCornerShape(8.dp)).clickable{onStyleValues(styleValues+(pair.first to choice)).also{} }.padding(vertical=12.dp),contentAlignment=Alignment.Center) { Text(choice,color=Color.White,fontSize=10.sp) }
         }
        }
       }
      }
     }
     "플러그인" -> {
      listOf("이벤트 컷","BGM","메시지 주고받기","주사위 이벤트","캐릭터의 일기","티어 리스트","LIVE 채팅창","엔딩","달력·시계","퀘스트","제타그램","익명 게시판","휴대폰 몰래 보기","돌발 이벤트","웹툰","러브라인","꿈 엿보기").forEach { name ->
       val enabled = pluginFlags[name] ?: false
       StudioPanel { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(name,color=Color.White,fontWeight=FontWeight.Bold);Text("플롯에서 사용할 "+name+" 기능을 설정합니다.",color=Muted,fontSize=11.sp)}; Switch(checked=enabled,onCheckedChange={onPluginToggle(name,it)}) } }
      }
     }
     "인트로" -> {
      Text("첫 상황을 만들어 주세요",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
      StudioPanel { StudioField("첫 상황 / 인트로",intro,onIntro,12,"*상황을 설명하고 캐릭터의 첫 대사를 작성해주세요*") }
       if (studioMessage.isNotBlank()) Text(studioMessage,color=Color(0xFFC7B8FF),fontSize=12.sp)
     }
     "소개" -> {
      Text("커버",color=Color.White,fontWeight=FontWeight.Bold)
      StudioPanel { Text("짧은 소개 및 해시태그",color=Color.White,fontWeight=FontWeight.Bold); Text("상단의 AI로 만들기로 소개문과 해시태그 초안을 생성할 수 있어.",color=Muted) }
       if (studioMessage.isNotBlank()) Text(studioMessage,color=Color(0xFFC7B8FF),fontSize=12.sp)
     }
     "설정" -> {
      Text("플롯 설정",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
      listOf("공개 여부","댓글 허용","리믹스 허용","대화 기록 저장").forEach { label ->
       val enabled = pluginFlags[label] ?: (label=="대화 기록 저장")
       StudioPanel { Row(verticalAlignment=Alignment.CenterVertically){Text(label,color=Color.White,modifier=Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={onPluginToggle(label,it)})} }
      }
     }
    }
    Button(onClick=onSavePlot,modifier=Modifier.fillMaxWidth().height(50.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple)){Text(if(saved) "저장 완료" else "임시저장 / 등록")}
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStudioScreen(slot:ChatSlot,slots:List<ChatSlot>,selected:Int,onSelect:(Int)->Unit,
 input:String,onInput:(String)->Unit,busy:Boolean,status:String,onSend:()->Unit,onBack:()->Unit,
 onNew:()->Unit,onBookmark:(ChatLine)->Unit) {
 var menu by remember{mutableStateOf(false)}
 var bookmarks by remember{mutableStateOf(false)}
 val chatListState = rememberLazyListState()
 val chatScope = rememberCoroutineScope()
 Scaffold(containerColor=Bg,topBar={
  TopAppBar(title={Column{Text(slot.title,color=Color.White,fontSize=15.sp);Text("ZETA · "+(selected+1)+"/"+slots.size,color=Muted,fontSize=10.sp)}},
   navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}},
   actions={IconButton(onClick={menu=true}){Icon(Icons.Default.Menu,null,tint=Color.White)};IconButton(onClick=onNew){Icon(Icons.Default.Add,null,tint=Color.White)}},
   colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))
 },bottomBar={
  Column(Modifier.fillMaxWidth().background(Color(0xFF191919)).navigationBarsPadding().padding(9.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){slots.take(4).forEachIndexed{i,s->AssistChip(onClick={onSelect(i)},label={Text(s.title.take(7))})}}
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("⚡ 무료",color=Muted,fontSize=11.sp)
    OutlinedTextField(value=input,onValueChange=onInput,modifier=Modifier.weight(1f),placeholder={Text("내용 입력하기",color=Muted)},minLines=1,maxLines=4)
    IconButton(onClick=onSend,enabled=!busy){Icon(Icons.Default.Send,null,tint=Purple)}
   }
   Text(status,color=Muted,fontSize=10.sp)
  }
 }){p->
  LazyColumn(state=chatListState,modifier=Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(slot.lines.isEmpty()) item{Text("첫 상황을 시작해 보세요",color=Color.White,modifier=Modifier.padding(top=100.dp))}
   items(slot.lines,key={it.id}){line->
    val mine=line.speaker=="나"
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine) Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Top){
     if(!mine){Box(Modifier.size(34.dp).background(Color(0xFF51465F),CircleShape),contentAlignment=Alignment.Center){Text(line.speaker.take(1),color=Color.White)};Spacer(Modifier.width(8.dp))}
     Column(Modifier.widthIn(max=330.dp)){
      if(!mine)Text(line.speaker,color=Color(0xFFC2B0FF),fontSize=12.sp)
      Surface(color=if(mine) Purple else PanelColor,shape=RoundedCornerShape(14.dp)){Text(line.text,color=Color.White,modifier=Modifier.padding(13.dp),fontSize=15.sp)}
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){IconButton(onClick={onBookmark(line)},modifier=Modifier.size(27.dp)){Icon(Icons.Default.Bookmark,null,tint=if(line.bookmarked) Color(0xFFFFD36A) else Muted,modifier=Modifier.size(15.dp))}}
     }
    }
   }
   if(busy)item{Text("캐릭터가 입력 중...",color=Muted)}
  }
 }
 if(menu) AlertDialog(onDismissRequest={menu=false},title={Text("대화 메뉴")},text={
  Column{slots.forEachIndexed{i,s->Text((i+1).toString()+". "+s.title,modifier=Modifier.fillMaxWidth().clickable{onSelect(i);menu=false}.padding(8.dp))};Text("책갈피 목록",modifier=Modifier.clickable{bookmarks=true;menu=false}.padding(8.dp));Text("새 대화",modifier=Modifier.clickable{onNew();menu=false}.padding(8.dp))}
 },confirmButton={TextButton(onClick={menu=false}){Text("닫기")}})
 if(bookmarks) AlertDialog(onDismissRequest={bookmarks=false},title={Text("책갈피 목록")},text={Column{val marked=slot.lines.filter{it.bookmarked};if(marked.isEmpty())Text("저장된 책갈피가 없습니다.");marked.forEach { line -> Text(line.speaker+": "+line.text.take(100),modifier=Modifier.fillMaxWidth().clickable { bookmarks=false; chatScope.launch { val index=slot.lines.indexOfFirst { it.id==line.id }; if(index>=0) chatListState.animateScrollToItem(index) } }.padding(8.dp)) }}},confirmButton={TextButton(onClick={bookmarks=false}){Text("닫기")}})
}

@Composable private fun StudioPanel(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=PanelColor),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable private fun StudioField(label:String,value:String,onValue:(String)->Unit,minLines:Int=2,placeholder:String=""){
 Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(label,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=13.sp);OutlinedTextField(value,onValue,modifier=Modifier.fillMaxWidth(),placeholder={Text(placeholder,color=Muted,fontSize=12.sp)},minLines=minLines,colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF393939)))}
}
