@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package il.kolkarov.assist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

private val Blue = Color(0xFF245AA5)
private val ListeningGreen = Color(0xFF1F8F4E)
private val AssistantFont = FontFamily(
    Font(R.font.assistant, FontWeight.Normal),
    Font(R.font.assistant, FontWeight.Medium),
    Font(R.font.assistant, FontWeight.SemiBold),
    Font(R.font.assistant, FontWeight.Bold)
)
private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val hebrewLetters = listOf("א","ב","ג","ד","ה","ו","ז","ח","ט","י","כ","ל","מ","נ","ס","ע","פ","צ","ק","ר","ש","ת")
private val englishLetters = ('A'..'Z').map { it.toString() }

enum class SettingsArea(val label: String) {
    FEATURES("פיצ׳רים פעילים"),
    PROFILE("שם, כתובת ומיקום"),
    VOICE("שפה, קול וגודל גופן"),
    PEOPLE("אנשי קשר וימי הולדת"),
    MEDICATIONS("תרופות לפי חלקי היום"),
    CALENDAR("יומן Google והתראות"),
    SOURCES("אתרי חדשות וספורט"),
    BACKUP("גיבוי והעברה")
}

@Composable fun CompanionTheme(p: Profile, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val colors = if(p.dark) darkColorScheme(primary=Color(0xFFA7C8FF),background=Color(0xFF101B2B),surface=Color(0xFF19283C),onSurface=Color(0xFFF2F5FA))
        else lightColorScheme(primary=Blue,background=Color(0xFFF4F7FB),surface=Color.White,onSurface=Color(0xFF172B46))
    val type = Typography(
        bodyLarge=TextStyle(fontFamily=AssistantFont,fontSize=21.sp,lineHeight=30.sp),
        bodyMedium=TextStyle(fontFamily=AssistantFont,fontSize=18.sp,lineHeight=26.sp),
        titleLarge=TextStyle(fontFamily=AssistantFont,fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold),
        headlineMedium=TextStyle(fontFamily=AssistantFont,fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
        labelLarge=TextStyle(fontFamily=AssistantFont,fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold)
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density,density.fontScale * p.fontScale)) {
        MaterialTheme(colorScheme=colors,typography=type,content=content)
    }
}

@Composable fun Action(text: String, icon: ImageVector? = null, enabled: Boolean = true, secondary: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val body: @Composable RowScope.() -> Unit = {
        if(icon != null) { Icon(icon,null,Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
    }
    if (active) {
        Button(onClick,Modifier.fillMaxWidth().heightIn(min=58.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),
            colors=ButtonDefaults.buttonColors(containerColor=ListeningGreen,contentColor=Color.White),contentPadding=PaddingValues(14.dp),content=body)
    } else if(secondary) {
        OutlinedButton(onClick,Modifier.fillMaxWidth().heightIn(min=58.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(14.dp),content=body)
    } else {
        Button(onClick,Modifier.fillMaxWidth().heightIn(min=58.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(14.dp),content=body)
    }
}

@Composable private fun HomeTile(text: String, icon: ImageVector, primary: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val body: @Composable RowScope.() -> Unit = {
        Icon(icon,null,Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text,style=MaterialTheme.typography.labelLarge,maxLines=1)
    }
    if (primary) {
        Button(onClick,modifier.heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),content=body)
    } else {
        OutlinedButton(onClick,modifier.heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),content=body)
    }
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable private fun Heading(text: String) { Text(text,style=MaterialTheme.typography.titleLarge) }
@Composable fun Field(value: String, label: String, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus() })
    )
}

@Composable fun CompanionUi(
    m: CompanionModel, p: Profile, speech: DeviceSpeech, listen: () -> Unit, stop: () -> Unit,
    requestContacts: () -> Unit = {},
    requestCalendar: () -> Unit, requestNotifications: () -> Unit, requestExact: () -> Unit, appSettings: () -> Unit,
    importFile: () -> Unit, exportFile: () -> Unit, restoreFile: () -> Unit, openUrl: (String) -> Unit
) {
    val people by m.people.collectAsState(); val reminders by m.reminders.collectAsState(); val sources by m.sources.collectAsState()
    val medications by m.medications.collectAsState()
    var showPerson by remember { mutableStateOf(false) }; var editingPerson by remember { mutableStateOf<Person?>(null) }
    var personOnlyBirthday by remember { mutableStateOf(false) }
    var peopleTab by remember { mutableStateOf(0) }
    var callFilter by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var englishMode by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }; var showPaste by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }; var familyGate by remember { mutableStateOf(false) }
    var settingsArea by remember { mutableStateOf<SettingsArea?>(null) }
    var showMedication by remember { mutableStateOf(false) }; var editingMedication by remember { mutableStateOf<Medication?>(null) }
    val pageScroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var nowTime by remember { mutableStateOf(ZonedDateTime.now(ZoneId.of(p.zone))) }
    LaunchedEffect(p.zone) {
        while(true) {
            nowTime = ZonedDateTime.now(ZoneId.of(p.zone))
            delay(15000)
        }
    }
    LaunchedEffect(m.screen,settingsArea) { pageScroll.scrollTo(0) }
    val canStop = m.listening || speech.speaking || m.busy
    val quietStatuses = setOf("מוכן להקשיב", "הפעולה בוטלה", "ההקראה נעצרה")
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val measurer=rememberTextMeasurer(); val width=with(LocalDensity.current) { maxWidth.roundToPx() }
                    val label="עזרה - העוזר האישי שלך"
                    val size=(22 downTo 8).firstOrNull { measurer.measure(label,TextStyle(fontFamily=AssistantFont,fontSize=it.sp,fontWeight=FontWeight.Bold)).size.width <= width } ?: 8
                    Text(label,fontFamily=AssistantFont,fontSize=size.sp,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false)
                }
                IconButton({ settingsArea=null; familyGate = true },Modifier.size(52.dp)) { Icon(Icons.Default.Settings,"הגדרות למשפחה",Modifier.size(28.dp)) }
            }
        }, bottomBar = {
            Surface(shadowElevation=8.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=10.dp),
                    horizontalArrangement=Arrangement.spacedBy(16.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { keyboard?.hide(); focusManager.clearFocus(); m.navigate(Screen.HOME) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "בית", modifier = Modifier.size(30.dp))
                    }
                    if (canStop) {
                        Button(
                            onClick = stop,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "עצור", modifier = Modifier.size(30.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = stop,
                            enabled = false,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "עצור", modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(pageScroll).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if (m.listening || m.busy || speech.speaking || (m.status.isNotBlank() && m.status !in quietStatuses)) {
                Text(
                    m.status,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (m.listening) ListeningGreen else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if(m.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when(m.screen) {
                Screen.HOME -> {
                    Text(if(p.name.isBlank()) "שלום, טוב שבאת" else "שלום, ${p.name}",style=MaterialTheme.typography.headlineMedium)
                    Text("${nowTime.toLocalDate().format(displayDate)} · ${nowTime.toLocalTime().format(clockFormat)}",style=MaterialTheme.typography.bodyMedium)
                    if(p.name.isBlank()) Panel { Text("בן משפחה יכול להתחיל בהגדרת השם והפיצ׳רים הפעילים."); Action("הגדרה ראשונה",Icons.Default.Settings) { familyGate=true } }
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        val diameter = 112.dp
                        Button(
                            onClick = listen,
                            modifier = Modifier.size(diameter),
                            enabled = !m.busy,
                            shape = CircleShape,
                            colors = if (m.listening) ButtonDefaults.buttonColors(containerColor = ListeningGreen, contentColor = Color.White)
                                     else ButtonDefaults.buttonColors(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Icon(Icons.Default.Mic,null,Modifier.size(32.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(if (m.listening) "מקשיב..." else "דבר איתי",fontSize=18.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                            }
                        }
                        Text(
                            if (m.listening) "דברו עכשיו – לחצו שוב לעצירה" else "לחצו ואמרו מה תרצו",
                            color = if (m.listening) ListeningGreen else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    data class HomeItem(val title: String, val icon: ImageVector, val primary: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)
                    val items = buildList {
                        if (p.enableCalls) add(HomeItem("התקשרות", Icons.Default.Call, primary = !p.enableMorning) { m.navigate(Screen.CALLS) })
                        if (p.enableMorning) add(HomeItem("הבוקר שלי", Icons.Default.WbSunny, primary = true, enabled = !m.busy) { m.morning() })
                        if (p.enableNews && sources.isNotEmpty()) add(HomeItem("חדשות", Icons.Default.Newspaper) { m.navigate(Screen.NEWS) })
                        if (p.enableAgenda || p.enableBirthdays) add(HomeItem("מה קרוב", Icons.Default.Event) { m.navigate(Screen.AGENDA) })
                        if (p.enableMedications) add(HomeItem("התרופות שלי", Icons.Default.Medication) { m.navigate(Screen.MEDICATIONS) })
                        if (p.enableWeather) add(HomeItem("מזג האוויר", Icons.Default.Cloud, enabled = !m.busy) { m.getWeather() })
                    }
                    items.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { item ->
                                HomeTile(item.title, item.icon, primary = item.primary, enabled = item.enabled, modifier = Modifier.weight(1f), onClick = item.onClick)
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if(m.transcript.isNotBlank()) Panel { Text("אמרת: ${m.transcript}",fontWeight=FontWeight.SemiBold) }
                }
                Screen.NEWS -> {
                    Heading("מה חדש היום?")
                    val activeTopics = Topic.entries.filter { t -> sources.any { it.topic == t.name } }
                    if (activeTopics.isEmpty()) {
                        Panel {
                            Text("טרם הוגדרו אתרי חדשות. אפשר להוסיף בהגדרות המשפחה ← אתרי חדשות וספורט.")
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeTopics.forEach { t ->
                                HomeTile(t.label, Icons.Default.Newspaper, primary = m.topic == t, enabled = !m.busy, modifier = Modifier.weight(1f)) { m.loadNews(t) }
                            }
                        }
                        if(m.newsResult == null && !m.busy) Text("בחרו תחום כדי לטעון חדשות.")
                    }
                    m.newsResult?.let { result ->
                        Text("עודכן ${result.fetched.atZone(ZoneId.of(p.zone)).format(displayTime)}",style=MaterialTheme.typography.bodyMedium)
                        result.warnings.forEach { Text(it,color=MaterialTheme.colorScheme.error) }
                        result.items.forEach { item -> Panel {
                            Text(item.source,color=MaterialTheme.colorScheme.primary); Heading(item.title)
                            Text(item.published.ifBlank { "המקור לא מסר תאריך פרסום" },style=MaterialTheme.typography.bodyMedium)
                            if(item.summary.isNotBlank()) Text(item.summary)
                            Action("הקראת הידיעה",Icons.AutoMirrored.Filled.VolumeUp) { m.respond("${item.source}. ${item.title}. ${item.summary}") }
                            Action("פתיחת הכתבה",Icons.Default.OpenInNew,secondary=true) { openUrl(item.url) }
                        } }
                    }
                }
                Screen.AGENDA -> {
                    Heading("היום ובשבוע הקרוב")
                    Action("הקראת האירועים",Icons.AutoMirrored.Filled.VolumeUp) { m.loadAgenda(true) }
                    Action("הוספת תזכורת",Icons.Default.Add,secondary=true) { showReminder=true }
                    if(m.agenda.isEmpty()) Panel { Text("אין אירועים קרובים.") }
                    m.agenda.forEach { item -> Panel {
                        Text(item.kind,color=MaterialTheme.colorScheme.primary); Heading(item.title)
                        Text(item.time.toLocalDate().format(displayDate)); if(!item.allDay) Text(item.time.toLocalTime().toString())
                        if(item.key.startsWith("reminder:")) reminders.firstOrNull { "reminder:${it.id}"==item.key }?.let { r ->
                            Action("ביטול תזכורת",Icons.Default.Delete,secondary=true) { m.requestDeleteReminder(r) }
                        }
                    } }
                }
                Screen.CALLS -> {
                    Heading("למי להתקשר?")
                    Action(
                        text = if (m.listening) "מקשיב לשם איש הקשר... (לחצו לעצירה)" else "חיפוש בקול (אמרו שם)",
                        icon = Icons.Default.Mic,
                        enabled = !m.busy,
                        active = m.listening,
                        onClick = { keyboard?.hide(); focusManager.clearFocus(); listen() }
                    )
                    if (p.enablePhoneContacts && !m.deviceContacts.allowed()) {
                        Action("אישור גישה לאנשי הקשר בטלפון", Icons.Default.Contacts, secondary = true, onClick = requestContacts)
                    }

                    // Search Field with Clear and Hide Keyboard buttons
                    OutlinedTextField(
                        value = callFilter,
                        onValueChange = { callFilter = it; selectedLetter = null; m.callChoices = emptyList(); m.answer = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("חיפוש לפי שם") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focusManager.clearFocus() }),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (callFilter.isNotEmpty() || selectedLetter != null || m.callChoices.isNotEmpty()) {
                                    IconButton({ callFilter = ""; selectedLetter = null; m.callChoices = emptyList(); m.answer = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "נקה חיפוש")
                                    }
                                }
                                IconButton({ keyboard?.hide(); focusManager.clearFocus() }) {
                                    Icon(Icons.Default.KeyboardHide, contentDescription = "סגירת מקלדת")
                                }
                            }
                        }
                    )

                    // Alphabet Quick Filter Bar (א-ב / A-Z)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedLetter == null && callFilter.isBlank() && m.callChoices.isEmpty(),
                            onClick = { selectedLetter = null; callFilter = ""; m.callChoices = emptyList(); m.answer = ""; keyboard?.hide(); focusManager.clearFocus() },
                            label = { Text("הכל", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { englishMode = !englishMode; selectedLetter = null },
                            label = { Text(if (englishMode) "א-ב" else "A-Z", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                        )
                        val letters = if (englishMode) englishLetters else hebrewLetters
                        letters.forEach { letter ->
                            FilterChip(
                                selected = selectedLetter == letter,
                                onClick = {
                                    selectedLetter = if (selectedLetter == letter) null else letter
                                    callFilter = ""
                                    m.callChoices = emptyList()
                                    m.answer = ""
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                },
                                label = { Text(letter, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    // Prominent Clear Search button whenever any filter or voice result is active
                    if (callFilter.isNotBlank() || selectedLetter != null || m.callChoices.isNotEmpty()) {
                        Action(
                            text = "ניקוי חיפוש והצגת כל אנשי הקשר",
                            icon = Icons.Default.Clear,
                            secondary = true,
                            onClick = {
                                callFilter = ""
                                selectedLetter = null
                                m.callChoices = emptyList()
                                m.answer = ""
                                keyboard?.hide()
                                focusManager.clearFocus()
                            }
                        )
                    }

                    // Precomputed fast contact list
                    val allCallable = remember(people, m.phonePeople, p.enablePhoneContacts) { m.allCallablePeople() }
                    val indexedCallable = remember(allCallable) {
                        allCallable.map { person ->
                            val cleanName = normalized(person.name)
                            val words = (listOf(cleanName) + person.aliases.split(',', ';').map { normalized(it) })
                                .flatMap { it.split(Regex("[\\s\\-_.;,/()]+")) }
                                .filter { it.isNotBlank() }
                            val searchBlob = (words + listOf(cleanName, person.phone)).joinToString(" ")
                            Triple(person, words, searchBlob)
                        }
                    }
                    val filtered = remember(indexedCallable, callFilter, selectedLetter, m.callChoices) {
                        if (m.callChoices.isNotEmpty()) m.callChoices
                        else if (selectedLetter != null) {
                            val l = selectedLetter!!.lowercase()
                            indexedCallable.filter { (_, words, _) -> words.any { it.startsWith(l) } }.map { it.first }
                        } else if (callFilter.isBlank()) {
                            allCallable
                        } else {
                            m.matchPeople(listOf(callFilter), allCallable).ifEmpty {
                                val q = normalized(callFilter)
                                indexedCallable.filter { (_, _, blob) -> blob.contains(q) }.map { it.first }
                            }
                        }
                    }

                    if(allCallable.isEmpty()) {
                        Text("אין עדיין אנשי קשר. אשרו גישה לאנשי הקשר בטלפון או הוסיפו בהגדרות המשפחה.")
                    } else if (filtered.isEmpty()) {
                        Text("לא נמצא איש קשר תואם.")
                    } else {
                        Panel {
                            filtered.take(50).forEachIndexed { idx, person ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            keyboard?.hide()
                                            focusManager.clearFocus()
                                            m.requestCall(person)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (person.favorite) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "מועדף",
                                                    tint = Color(0xFFE6A800),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Text(person.name, style = MaterialTheme.typography.titleLarge)
                                        }
                                        Text(person.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                    }
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = "חיוג אל ${person.name}",
                                        tint = ListeningGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Screen.MEDICATIONS -> {
                    Heading("התרופות שלי")
                    Action("הקראת הרשימה",Icons.AutoMirrored.Filled.VolumeUp) { m.respond(m.medicationText()) }
                    if(medications.none { it.enabled }) Text("עדיין לא הוגדרו תרופות. היכנסו להגדרות למשפחה ← תרופות לפי חלקי היום.")
                    DayPart.entries.forEach { part ->
                        val entries=medications.filter { it.enabled }.flatMap { med -> MedicationSchedule.decode(med.slots).filter { it.part==part }.map { med to it.time } }.sortedBy { it.second }
                        if(entries.isNotEmpty()) Panel { Heading(part.label); entries.forEach { (med,time) ->
                            Text("$time · ${med.name}",fontWeight=FontWeight.Bold)
                            if(med.instructions.isNotBlank()) Text(med.instructions)
                        } }
                    }
                }
                Screen.SETTINGS -> {
                    Heading("הגדרות למשפחה")
                    if(settingsArea==null) {
                        Text("בחרו מה להגדיר:")
                        SettingsArea.entries.forEach { area -> Action(area.label,Icons.Default.ChevronLeft,secondary=area!=SettingsArea.FEATURES) { settingsArea=area } }
                    } else {
                        Action("חזרה לתפריט ההגדרות",Icons.Default.ArrowForward,secondary=true) { settingsArea=null }
                        if(settingsArea in listOf(SettingsArea.FEATURES,SettingsArea.PROFILE,SettingsArea.VOICE,SettingsArea.CALENDAR))
                            SettingsForm(m,p,speech,settingsArea!!,requestContacts,requestCalendar,requestNotifications,requestExact,appSettings)
                        if(settingsArea==SettingsArea.PEOPLE) Panel {
                            Heading("אנשי קשר וימי הולדת")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeTile("אנשי קשר", Icons.Default.Call, primary = peopleTab == 0, modifier = Modifier.weight(1f)) { peopleTab = 0 }
                                HomeTile("ימי הולדת", Icons.Default.Cake, primary = peopleTab == 1, modifier = Modifier.weight(1f)) { peopleTab = 1 }
                            }
                            if (peopleTab == 0) {
                                if (!m.deviceContacts.allowed()) {
                                    Action("חיבור אנשי קשר מהטלפון", Icons.Default.Contacts, onClick = requestContacts)
                                } else {
                                    Text("מחובר לאנשי הקשר בטלפון (${m.phonePeople.size} אנשי קשר)", color = ListeningGreen, fontWeight = FontWeight.SemiBold)
                                }
                                Action("הוספת איש קשר ידנית",Icons.Default.PersonAdd,secondary=true) { editingPerson=null; personOnlyBirthday=false; showPerson=true }
                                val contactPeople = people.filter { it.phone.isNotBlank() }
                                contactPeople.forEach { person ->
                                    HorizontalDivider()
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(person.name,fontWeight=FontWeight.Bold)
                                            Text(person.phone,style=MaterialTheme.typography.bodyMedium)
                                        }
                                        Row {
                                            IconButton({ editingPerson=person; personOnlyBirthday=false; showPerson=true }) { Icon(Icons.Default.Edit,"עריכה") }
                                            IconButton({ m.deletePerson(person) }) { Icon(Icons.Default.Delete,"מחיקה") }
                                        }
                                    }
                                }
                            } else {
                                Action("הוספת יום הולדת",Icons.Default.Cake) { editingPerson=null; personOnlyBirthday=true; showPerson=true }
                                Action("יבוא קובץ Excel",Icons.Default.UploadFile,secondary=true,onClick=importFile)
                                Action("הדבקת טבלה",Icons.Default.ContentPaste,secondary=true) { showPaste=true }
                                Text("ליבוא ימי הולדת נדרשות רק עמודות שם ותאריך לידה (יום/חודש או יום/חודש/שנה). רשומות ללא טלפון יישמרו כימי הולדת בלבד ולא יופיעו בחיוג.",style=MaterialTheme.typography.bodyMedium)
                                val birthdayPeople = people.filter { it.birthDay != null && it.birthMonth != null }
                                if (birthdayPeople.isEmpty()) Text("טרם נוספו ימי הולדת.")
                                birthdayPeople.forEach { person ->
                                    HorizontalDivider()
                                    val bStr = "${person.birthDay}/${person.birthMonth}" + (person.birthYear?.let { "/$it" } ?: "")
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(person.name,fontWeight=FontWeight.Bold)
                                            Text("תאריך לידה: $bStr",style=MaterialTheme.typography.bodyMedium)
                                        }
                                        Row {
                                            IconButton({ editingPerson=person; personOnlyBirthday=person.phone.isBlank(); showPerson=true }) { Icon(Icons.Default.Edit,"עריכה") }
                                            IconButton({ m.deletePerson(person) }) { Icon(Icons.Default.Delete,"מחיקה") }
                                        }
                                    }
                                }
                            }
                        }
                        if(settingsArea==SettingsArea.MEDICATIONS) Panel {
                            Heading("תרופות לפי חלקי היום")
                            Text("הזינו את שם התרופה, ההנחיות שנקבעו לכם והשעה בכל חלק יום.")
                            Action("הוספת תרופה",Icons.Default.Add) { editingMedication=null; showMedication=true }
                            medications.forEach { med ->
                                HorizontalDivider(); Heading(med.name)
                                if(med.instructions.isNotBlank()) Text(med.instructions)
                                MedicationSchedule.decode(med.slots).forEach { Text("${it.part.label}: ${it.time}") }
                                Text(if(med.enabled) "התזכורות פעילות" else "התזכורות מושהות")
                                Action("עריכת התרופה",Icons.Default.Edit,secondary=true) { editingMedication=med;showMedication=true }
                                Action("הסרת התרופה",Icons.Default.Delete,secondary=true) {m.deleteMedication(med)}
                            }
                            Action("אישור התראות",Icons.Default.Notifications,secondary=true,onClick=requestNotifications)
                            Action("הרשאת תזמון מדויק",Icons.Default.Alarm,secondary=true,onClick=requestExact)
                        }
                        if(settingsArea==SettingsArea.SOURCES) Panel {
                            Heading("מקורות חדשות")
                            Text("הוסיפו קישור HTTPS להזנת RSS או Atom. רק תחומים שיש להם מקור יוצגו במסך החדשות.")
                            Action("הוספת מקור",Icons.Default.Add) { showSource=true }
                            sources.forEach { source ->
                                Text("${source.name} · ${Topic.valueOf(source.topic).label}"); Text(source.url,style=MaterialTheme.typography.bodyMedium)
                                Action("הסרת מקור",Icons.Default.Delete,secondary=true) { m.deleteSource(source) }
                            }
                        }
                        if(settingsArea==SettingsArea.BACKUP) Panel {
                            Heading("גיבוי והעברה")
                            Text("גיבוי אישי כולל שמות, טלפונים, תאריכים והגדרות.")
                            Action("שמירת גיבוי",Icons.Default.Save,secondary=true,onClick=exportFile)
                            Action("שחזור מגיבוי",Icons.Default.Restore,secondary=true,onClick=restoreFile)
                        }
                    }
                }
            }
            if(m.answer.isNotBlank()) Panel {
                Heading("התשובה שלי")
                Text(m.answer,Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (m.spokenAnswer) {
                    Action("הקראה שוב",Icons.AutoMirrored.Filled.VolumeUp) { speech.say(m.answer,p) }
                }
            }
        }
    }
    if(familyGate) AlertDialog(onDismissRequest={familyGate=false},title={Heading("הגדרות למשפחה")},text={Text("כאן מנהלים את הפיצ׳רים הפעילים, פרטי המשתמש ואנשי הקשר.")},
        confirmButton={ TextButton({familyGate=false; settingsArea=null; m.navigate(Screen.SETTINGS)},Modifier.heightIn(min=56.dp)) { Text("כניסה להגדרות") } },
        dismissButton={ TextButton({familyGate=false},Modifier.heightIn(min=56.dp)) { Text("חזרה") } })
    m.confirmation?.let { pending -> AlertDialog(onDismissRequest={m.cancel()},title={Heading("אישור פעולה")},text={ Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(pending.text)
        Action(if (m.listening) "מקשיב לתשובה..." else "לענות בקול (כן / לא)",Icons.Default.Mic,active=m.listening,onClick=listen)
    } },confirmButton={ TextButton({m.confirm()},Modifier.heightIn(min=56.dp),enabled=!m.busy) { Text("כן, אישור") } },dismissButton={TextButton({m.cancel()},Modifier.heightIn(min=56.dp)) {Text("ביטול")} }) }
    if(showPerson) PersonDialog(editingPerson,onlyBirthday=personOnlyBirthday,onDismiss={showPerson=false},onSave={m.savePerson(it);showPerson=false})
    if(showReminder) ReminderDialog(p,onDismiss={showReminder=false},onSave={title,at -> showReminder=false;m.requestReminder(title,at)})
    if(showPaste) InputDialog("הדבקת רשימה","טבלה עם שורת כותרות",onDismiss={showPaste=false}) { m.previewPaste(it);showPaste=false }
    if(showSource) SourceDialog(onDismiss={showSource=false}) { name,url,topic -> showSource=false;m.saveSource(name,url,topic) }
    if(showMedication) MedicationDialog(editingMedication,onDismiss={showMedication=false}) { med -> showMedication=false;m.requestMedication(med) }
    m.importPreview?.let { preview -> AlertDialog(onDismissRequest={m.importPreview=null},title={Heading("בדיקת היבוא")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val bCount = preview.accepted.count { it.birthDay != null && it.phone.isBlank() }
            val cCount = preview.accepted.count { it.phone.isNotBlank() }
            Text("${preview.accepted.size} רשומות תקינות להוספה ($bCount ימי הולדת, $cCount אנשי קשר עם טלפון)")
            preview.rows.forEach { row -> Text("שורה ${row.line}: ${row.error ?: ((row.person?.name ?: "") + if(row.duplicate) " — כפילות, תידלג" else " — תקין")}") }
        }
    },confirmButton={TextButton({m.acceptImport()},Modifier.heightIn(min=56.dp),enabled=preview.accepted.isNotEmpty()&&!m.busy){Text("יבוא הרשומות התקינות")}},dismissButton={TextButton({m.importPreview=null},Modifier.heightIn(min=56.dp)){Text("ביטול")}}) }
    m.restorePreview?.let { backup -> AlertDialog(onDismissRequest={m.restorePreview=null},title={Heading("שחזור גיבוי")},text={Text("${backup.people.size} רשומות, ${backup.reminders.size} תזכורות, ${backup.medications.size} תרופות ו־${backup.sources.size} מקורות.")},
        confirmButton={TextButton({m.restorePreview=null;m.runTask{Backup.merge(m.app,backup);m.status="הגיבוי שוחזר"}},Modifier.heightIn(min=56.dp)){Text("אישור שחזור")}},dismissButton={TextButton({m.restorePreview=null},Modifier.heightIn(min=56.dp)){Text("ביטול")}}) }
}

@Composable private fun FeatureToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable private fun SettingsForm(
    m: CompanionModel, p: Profile, speech: DeviceSpeech, area: SettingsArea,
    requestContacts: () -> Unit, requestCalendar: () -> Unit, requestNotifications: () -> Unit,
    requestExact: () -> Unit, appSettings: () -> Unit
) {
    var draft by remember(p) { mutableStateOf(p) }; var cityQuery by remember { mutableStateOf("") }
    var hour by remember(p) { mutableStateOf("%02d:%02d".format(p.noticeHour,p.noticeMinute)) }
    var error by remember { mutableStateOf("") }

    if (area == SettingsArea.FEATURES) Panel {
        Heading("פיצ׳רים פעילים במערכת")
        Text("בחרו אילו כפתורים ופיצ׳רים יוצגו במסך הבית כדי לשמור על מסך פשוט וברור ללא גלילה:", style = MaterialTheme.typography.bodyMedium)
        FeatureToggleRow("התקשרות לאנשי קשר", draft.enableCalls) {
            draft = draft.copy(enableCalls = it); m.saveProfile(draft)
        }
        FeatureToggleRow("שימוש באנשי הקשר של הטלפון", draft.enablePhoneContacts) {
            draft = draft.copy(enablePhoneContacts = it); m.saveProfile(draft)
            if (it && !m.deviceContacts.allowed()) requestContacts()
        }
        if (draft.enablePhoneContacts && !m.deviceContacts.allowed()) {
            Action("אישור הרשאה לאנשי הקשר בטלפון", Icons.Default.Contacts, secondary = true, onClick = requestContacts)
        }
        HorizontalDivider()
        FeatureToggleRow("ימי הולדת", draft.enableBirthdays) {
            draft = draft.copy(enableBirthdays = it); m.saveProfile(draft)
        }
        FeatureToggleRow("יומן ותזכורות (מה קרוב)", draft.enableAgenda) {
            draft = draft.copy(enableAgenda = it); m.saveProfile(draft)
        }
        FeatureToggleRow("תרופות לפי חלקי היום", draft.enableMedications) {
            draft = draft.copy(enableMedications = it); m.saveProfile(draft)
        }
        FeatureToggleRow("חדשות וספורט", draft.enableNews) {
            draft = draft.copy(enableNews = it); m.saveProfile(draft)
        }
        FeatureToggleRow("מזג האוויר", draft.enableWeather) {
            draft = draft.copy(enableWeather = it); m.saveProfile(draft)
        }
        FeatureToggleRow("תקציר הבוקר שלי", draft.enableMorning) {
            draft = draft.copy(enableMorning = it); m.saveProfile(draft)
        }
    }

    if(area==SettingsArea.PROFILE) Panel {
        Heading("הפרטים שלי")
        Field(draft.name,"שם המשתמש") {draft=draft.copy(name=it)}
        Field(draft.address,"כתובת") {draft=draft.copy(address=it)}
        Text("מיקום נבחר: ${draft.city} ${draft.country}")
        Field(cityQuery,"חיפוש עיר, למשל חיפה") {cityQuery=it}
        Action("חיפוש עיר",Icons.Default.Search,enabled=!m.busy) {m.searchPlaces(cityQuery)}
        m.places.forEach { place -> Action("${place.name}, ${place.country}",secondary=true) {
            draft=draft.copy(city=place.name,country=place.country,latitude=place.latitude,longitude=place.longitude,zone=place.zone);m.places=emptyList()
        } }
        Text("אזור זמן: ${draft.zone}",style=MaterialTheme.typography.bodyMedium)
    }
    if(area==SettingsArea.VOICE) Panel {
        Heading("קול ותצוגה")
        Text("שפת הדיבור וההקראה; הממשק נשאר בעברית")
        listOf("he-IL" to "עברית","en-US" to "English","ar" to "العربية").forEach { (code,label) ->
            Action((if(draft.language==code) "✓ " else "")+label,secondary=true) {draft=draft.copy(language=code,voice="")}
        }
        Text("מהירות דיבור: ${"%.2f".format(draft.rate)}")
        Slider(draft.rate,{draft=draft.copy(rate=it)},valueRange=.5f..1.5f,modifier=Modifier.semantics { contentDescription="מהירות דיבור" })
        Text("קול: ${draft.voice.ifBlank {"ברירת המחדל של המכשיר"}}",style=MaterialTheme.typography.bodyMedium)
        Action("קול ברירת מחדל",secondary=true) {draft=draft.copy(voice="")}
        speech.voices.filter { it.locale.language==java.util.Locale.forLanguageTag(draft.language).language }.forEach { voice ->
            Action(voice.name + if(voice.isNetworkConnectionRequired) " · דורש רשת" else "",secondary=true) {
                draft=draft.copy(voice=voice.name);speech.say("שלום, זה הקול שנבחר",draft)
            }
        }
        Action("בדיקת הקראה",Icons.AutoMirrored.Filled.VolumeUp) {speech.say("שלום ${draft.name}. אני כאן כדי לעזור",draft)}
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(draft.dark,{draft=draft.copy(dark=it)},Modifier.semantics {contentDescription="תצוגה כהה"}); Spacer(Modifier.width(12.dp));Text("תצוגה כהה") }
        Text("גודל גופן")
        Slider(draft.fontScale,{draft=draft.copy(fontScale=it)},valueRange=1f..1.5f,modifier=Modifier.semantics {contentDescription="גודל גופן"})
        Text("שעת התראות יומי הולדת ואירועים")
        Field(hour,"שעה בפורמט 09:00") {hour=it}
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(draft.morningNotice,{draft=draft.copy(morningNotice=it)},Modifier.semantics {contentDescription="הזמנה לתקציר בוקר"});Spacer(Modifier.width(12.dp));Text("התראה לפתיחת הבוקר שלי")}
    }
    if(area==SettingsArea.PROFILE || area==SettingsArea.VOICE) {
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Action("שמירת ההגדרות",Icons.Default.Check,enabled=!m.busy) {
            try { val time=LocalTime.parse(hour);m.saveProfile(draft.copy(noticeHour=time.hour,noticeMinute=time.minute));error="" }
            catch(_:Exception){error="הזינו שעה תקינה, למשל 09:00"}
        }
    }
    if(area==SettingsArea.CALENDAR) Panel {
        Heading("יומן והרשאות")
        Action("אישור קריאת יומן",Icons.Default.Event,secondary=true,onClick=requestCalendar)
        Text("בחרו רק יומנים שתרצו להציג. חשבון Google צריך להיות מסונכרן במכשיר.")
        if(m.calendarChoices.isEmpty()) Text("עדיין לא נמצאו יומנים. בדקו הרשאת יומן וסנכרון החשבון.")
        m.calendarChoices.forEach { choice -> Row(Modifier.fillMaxWidth().clickable {
            draft=draft.copy(calendarIds=if(choice.id in draft.calendarIds) draft.calendarIds-choice.id else draft.calendarIds+choice.id)
        }.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
            Checkbox(choice.id in draft.calendarIds,{checked -> draft=draft.copy(calendarIds=if(checked) draft.calendarIds+choice.id else draft.calendarIds-choice.id)},Modifier.semantics {contentDescription=choice.name})
            Text(choice.name,Modifier.weight(1f))
        } }
        Action("שמירת בחירת היומנים",Icons.Default.Check,enabled=!m.busy) { m.saveProfile(p.copy(calendarIds=draft.calendarIds)) }
        Action("אישור התראות",Icons.Default.Notifications,secondary=true,onClick=requestNotifications)
        Text(if(NoticeScheduler(m.app).exactAllowed()) "תזמון מדויק זמין" else "תזמון מדויק לא מאושר. תזכורות עלולות להתעכב",style=MaterialTheme.typography.bodyMedium)
        Action("הרשאת תזמון מדויק",Icons.Default.Alarm,secondary=true,onClick=requestExact)
        Action("הגדרות הרשאות במכשיר",Icons.Default.Settings,secondary=true,onClick=appSettings)
    }
}

@Composable private fun PersonDialog(person: Person?, onlyBirthday: Boolean = false, onDismiss:()->Unit, onSave:(Person)->Unit) {
    var name by remember {mutableStateOf(person?.name.orEmpty())};var aliases by remember {mutableStateOf(person?.aliases.orEmpty())}
    var phone by remember {mutableStateOf(person?.phone.orEmpty())};var birth by remember {mutableStateOf(person?.let { p -> p.birthDay?.let {"$it/${p.birthMonth}"+(p.birthYear?.let {y->"/$y"}?:"")} }.orEmpty())}
    var error by remember {mutableStateOf("")}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Heading(if (onlyBirthday) "הוספת יום הולדת" else "איש קשר ויום הולדת")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Field(name,"שם"){name=it}
            Field(aliases,"כינויים, מופרדים בפסיק"){aliases=it}
            if (!onlyBirthday) Field(phone,"מספר טלפון"){phone=it}
            Field(birth,"תאריך לידה: יום/חודש או יום/חודש/שנה"){birth=it}
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton({try {
            val preview=PeopleImport.preview(listOf(listOf("שם","כינוי","טלפון","תאריך לידה"),listOf(name,aliases,if(onlyBirthday) "" else phone,birth)),emptyList())
            val row=preview.rows.single();require(row.person!=null){row.error.orEmpty()};onSave(row.person.copy(id=person?.id?:row.person.id))
        }catch(e:Exception){error=e.message?:"פרטים לא תקינים"}},Modifier.heightIn(min=56.dp)){Text("שמירה")}},
        dismissButton={TextButton(onDismiss,Modifier.heightIn(min=56.dp)){Text("ביטול")}}
    )
}
@Composable private fun ReminderDialog(p:Profile,onDismiss:()->Unit,onSave:(String,LocalDateTime)->Unit) {
    var title by remember {mutableStateOf("")};var date by remember {mutableStateOf(LocalDate.now(ZoneId.of(p.zone)).plusDays(1).toString())};var time by remember {mutableStateOf("09:00")};var error by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Heading("תזכורת חדשה")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Field(title,"מה להזכיר?"){title=it};Field(date,"תאריך, למשל 2026-12-25"){date=it};Field(time,"שעה, למשל 09:00"){time=it};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton({try{val at=LocalDate.parse(date).atTime(LocalTime.parse(time));require(title.isNotBlank()&&at.isAfter(LocalDateTime.now(ZoneId.of(p.zone))));onSave(title,at)}catch(_:Exception){error="יש להזין תיאור, תאריך ושעה עתידיים"}},Modifier.heightIn(min=56.dp)){Text("המשך לאישור")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=56.dp)){Text("ביטול")}})
}
@Composable private fun InputDialog(title:String,label:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember{mutableStateOf("")}
    val clipboard=LocalClipboardManager.current
    AlertDialog(onDismissRequest=onDismiss,title={Heading(title)},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("העתיקו מאקסל את הטבלה יחד עם שורת הכותרות: שם, תאריך לידה (ואם תרצו גם כינוי וטלפון).")
        Action("הדבקה מהלוח",Icons.Default.ContentPaste,secondary=true) {text=clipboard.getText()?.text.orEmpty()}
        Field(text,label){text=it}
    }},confirmButton={TextButton({onSave(text)},Modifier.heightIn(min=56.dp)){Text("תצוגה מקדימה")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=56.dp)){Text("ביטול")}})
}
@Composable private fun SourceDialog(onDismiss:()->Unit,onSave:(String,String,Topic)->Unit) {
    var name by remember{mutableStateOf("")};var url by remember{mutableStateOf("")};var topic by remember{mutableStateOf(Topic.GENERAL)}
    AlertDialog(onDismissRequest=onDismiss,title={Heading("מקור חדשות")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Field(name,"שם האתר"){name=it};Field(url,"קישור RSS המתחיל ב־https://"){url=it}
        Topic.entries.forEach{t->Action((if(topic==t)"✓ " else "")+t.label,secondary=true){topic=t}}
    }},confirmButton={TextButton({onSave(name,url,topic)},Modifier.heightIn(min=56.dp)){Text("בדיקה ושמירה")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=56.dp)){Text("ביטול")}})
}

@Composable private fun MedicationDialog(medication:Medication?,onDismiss:()->Unit,onSave:(Medication)->Unit) {
    var name by remember { mutableStateOf(medication?.name.orEmpty()) }
    var instructions by remember { mutableStateOf(medication?.instructions.orEmpty()) }
    val existing=remember { medication?.let { MedicationSchedule.decode(it.slots) }.orEmpty() }
    var selected by remember { mutableStateOf(existing.map { it.part }.toSet()) }
    val times=remember { mutableStateMapOf<DayPart,String>().apply { existing.forEach { put(it.part,it.time.toString()) } } }
    var enabled by remember { mutableStateOf(medication?.enabled ?: true) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Heading("הגדרת תרופה")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Field(name,"שם התרופה") { name=it }
            Field(instructions,"מינון והנחיות שנקבעו לכם") { instructions=it }
            Text("בחרו חלקי יום והזינו את השעות שלכם, בפורמט 08:00. התזכורת חוזרת בכל יום.",style=MaterialTheme.typography.bodyMedium)
            DayPart.entries.forEach { part ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(part in selected,{ checked -> selected=if(checked) selected+part else selected-part },Modifier.semantics {contentDescription=part.label})
                    Text(part.label)
                }
                if(part in selected) Field(times[part].orEmpty(),"שעה ל${part.label}") { times[part]=it }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Switch(enabled,{enabled=it},Modifier.semantics {contentDescription="תזכורות פעילות"}); Spacer(Modifier.width(12.dp)); Text("תזכורות פעילות")
            }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton({try {
        require(name.isNotBlank() && name.length<=120) { "הזינו שם תרופה, עד 120 תווים" }
        require(instructions.length<=1000) { "ההנחיות ארוכות מדי" }
        require(selected.isNotEmpty()) { "בחרו לפחות חלק יום אחד" }
        val slots=MedicationSchedule.encode(selected.map { MedicationTime(it,LocalTime.parse(times[it].orEmpty())) })
        onSave(Medication(id=medication?.id?:java.util.UUID.randomUUID().toString(),name=name.trim(),instructions=instructions.trim(),slots=slots,enabled=enabled))
    }catch(_:java.time.DateTimeException){error="לכל חלק יום שנבחר יש להזין שעה תקינה, למשל 08:00"}
     catch(e:IllegalArgumentException){error=e.message?:"בדקו את הפרטים"}},Modifier.heightIn(min=56.dp)){Text("המשך לאישור")}},
        dismissButton={TextButton(onDismiss,Modifier.heightIn(min=56.dp)){Text("ביטול")}})
}
