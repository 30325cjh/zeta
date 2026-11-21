package com.zeta.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 var tab by remember { mutableStateOf("플롯") }
 Scaffold(containerColor=Bg, bottomBar={
  NavigationBar(containerColor=Color(0xFF191919)) {
   listOf("플롯","설정집","피드").forEach { name ->
    NavigationBarItem(selected=tab==name,onClick={tab=name},
     icon={Text(if(name=="플롯") "▤" else if(name=="설정집") "▧" else "◎")},
     label={Text(name)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Purple,selectedTextColor=Color.White,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=Color(0xFF30244C)))
   }
  }
 }) { p ->
  Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(18.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Text("zeta",fontSize=29.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
    Spacer(Modifier.weight(1f)); Text("⌕",fontSize=25.sp,color=Color.White); Text("☰",fontSize=23.sp,color=Color.White,modifier=Modifier.padding(start=18.dp))
   }
   Spacer(Modifier.height(28.dp))
   if(tab=="플롯") {
    Text("나의 플롯",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color.White)
    HomeCard(if(plotTitle.isBlank()) "새로운 이야기 만들기" else plotTitle,"프롬프트와 캐릭터, 세계관을 설정해 나만의 플롯을 만들어 보세요.",onOpenPlot)
    Spacer(Modifier.height(25.dp))
    Text("빠른 시작",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Color.White)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
     Box(Modifier.weight(1f)){ HomeCard("대화 시작","저장된 대화 이어하기",onOpenChat) }
     Box(Modifier.weight(1f)){ HomeCard("캐릭터",characterCount.toString()+"명 등록됨",onOpenCharacters) }
    }
    Spacer(Modifier.height(22.dp))
    Text("최근 대화",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Color.White)
    HomeCard("대화 목록","저장된 대화 "+chatCount+"개 · 새 대화 만들기",onNewChat)
    Button(onClick=onOpenPlot,modifier=Modifier.fillMaxWidth().padding(top=18.dp).height(52.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple)) { Text("＋ 새 플롯 만들기") }
   } else {
    Text(if(tab=="설정집") "나의 설정집" else "피드",fontSize=22.sp,color=Color.White,fontWeight=FontWeight.Bold)
    HomeCard(if(tab=="설정집") "플롯에서 설정집 관리" else "아직 표시할 피드가 없어요","플롯을 열어 설정집과 키워드를 등록하세요.",onOpenPlot)
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
 onGenerateStudio:(String)->Unit,studioBusy:Boolean,studioMessage:String,onBack:()->Unit) {
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
       var enabled by remember(name){mutableStateOf(false)}
       StudioPanel { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(name,color=Color.White,fontWeight=FontWeight.Bold);Text("플롯에서 사용할 "+name+" 기능을 설정합니다.",color=Muted,fontSize=11.sp)}; Switch(checked=enabled,onCheckedChange={enabled=it}) } }
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
      listOf("공개 여부","댓글 허용","리믹스 허용","성인 지향 설정","대화 기록 저장").forEach { label ->
       var enabled by remember(label){mutableStateOf(label=="성인 지향 설정"||label=="대화 기록 저장")}
       StudioPanel { Row(verticalAlignment=Alignment.CenterVertically){Text(label,color=Color.White,modifier=Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={enabled=it})} }
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
  LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
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
 if(bookmarks) AlertDialog(onDismissRequest={bookmarks=false},title={Text("책갈피 목록")},text={Column{val marked=slot.lines.filter{it.bookmarked};if(marked.isEmpty())Text("저장된 책갈피가 없습니다.");marked.forEach{Text(it.speaker+": "+it.text.take(100),modifier=Modifier.padding(5.dp))}}},confirmButton={TextButton(onClick={bookmarks=false}){Text("닫기")}})
}

@Composable private fun StudioPanel(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=PanelColor),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable private fun StudioField(label:String,value:String,onValue:(String)->Unit,minLines:Int=2,placeholder:String=""){
 Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(label,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=13.sp);OutlinedTextField(value,onValue,modifier=Modifier.fillMaxWidth(),placeholder={Text(placeholder,color=Muted,fontSize=12.sp)},minLines=minLines,colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Purple,unfocusedBorderColor=Color(0xFF393939)))}
}
