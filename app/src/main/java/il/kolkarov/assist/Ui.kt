@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package il.kolkarov.assist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import java.time.*

private val Blue = Color(0xFF245AA5)
enum class SettingsArea(val label: String) {
    PROFILE("שם, כתובת ומיקום"), VOICE("שפה, קול וגודל גופן"), PEOPLE("אנשי קשר וימי הולדת"),
    MEDICATIONS("תרופות לפי חלקי היום"), CALENDAR("יומן Google והתראות"), SOURCES("אתרי חדשות וספורט"), BACKUP("גיבוי והעברה")
}
@Composable fun CompanionTheme(p: Profile, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val colors = if(p.dark) darkColorScheme(primary=Color(0xFFA7C8FF),background=Color(0xFF101B2B),surface=Color(0xFF19283C),onSurface=Color(0xFFF2F5FA))
        else lightColorScheme(primary=Blue,background=Color(0xFFF4F7FB),surface=Color.White,onSurface=Color(0xFF172B46))
    val type = Typography(bodyLarge=TextStyle(fontSize=22.sp,lineHeight=32.sp),bodyMedium=TextStyle(fontSize=20.sp,lineHeight=29.sp),
        titleLarge=TextStyle(fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),headlineMedium=TextStyle(fontSize=32.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold),
        labelLarge=TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density,density.fontScale * p.fontScale)) {
        MaterialTheme(colorScheme=colors,typography=type,content=content)
    }
}

@Composable fun Action(text: String, icon: ImageVector? = null, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val body: @Composable RowScope.() -> Unit = {
        if(icon != null) { Icon(icon,null,Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)) }
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
    }
    if(secondary) OutlinedButton(onClick,Modifier.fillMaxWidth().heightIn(min=64.dp),enabled=enabled,shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(16.dp),content=body)
    else Button(onClick,Modifier.fillMaxWidth().heightIn(min=64.dp),enabled=enabled,shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(16.dp),content=body)
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable private fun Heading(text: String) { Text(text,style=MaterialTheme.typography.titleLarge) }
@Composable fun Field(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={ Text(label) },textStyle=MaterialTheme.typography.bodyLarge,shape=RoundedCornerShape(14.dp))
}

@Composable fun CompanionUi(
    m: CompanionModel, p: Profile, speech: DeviceSpeech, listen: () -> Unit, stop: () -> Unit,
    requestCalendar: () -> Unit, requestNotifications: () -> Unit, requestExact: () -> Unit, appSettings: () -> Unit,
    importFile: () -> Unit, exportFile: () -> Unit, restoreFile: () -> Unit, openUrl: (String) -> Unit
) {
    val people by m.people.collectAsState(); val reminders by m.reminders.collectAsState(); val sources by m.sources.collectAsState()
    val medications by m.medications.collectAsState()
    var showPerson by remember { mutableStateOf(false) }; var editingPerson by remember { mutableStateOf<Person?>(null) }
    var showReminder by remember { mutableStateOf(false) }; var showPaste by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }; var familyGate by remember { mutableStateOf(false) }
    var settingsArea by remember { mutableStateOf<SettingsArea?>(null) }
    var showMedication by remember { mutableStateOf(false) }; var editingMedication by remember { mutableStateOf<Medication?>(null) }
    val pageScroll=rememberScrollState()
    LaunchedEffect(m.screen,settingsArea) { pageScroll.scrollTo(0) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val measurer=rememberTextMeasurer(); val width=with(LocalDensity.current) { maxWidth.roundToPx() }
                    val label="עזרה - העוזר האישי שלך"
                    val size=(22 downTo 8).firstOrNull { measurer.measure(label,TextStyle(fontSize=it.sp,fontWeight=FontWeight.Bold)).size.width <= width } ?: 8
                    Text(label,fontSize=size.sp,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false)
                }
                IconButton({ settingsArea=null; familyGate = true },Modifier.size(64.dp)) { Icon(Icons.Default.Settings,"הגדרות למשפחה",Modifier.size(30.dp)) }
            }
        }, bottomBar = {
            Surface(shadowElevation=8.dp) {
                Row(Modifier.navigationBarsPadding().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ m.navigate(Screen.HOME) },Modifier.weight(1f).heightIn(min=64.dp)) { Icon(Icons.Default.Home,null); Spacer(Modifier.width(6.dp)); Text("בית") }
                    Button(stop,Modifier.weight(1f).heightIn(min=64.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer)) {
                        Icon(Icons.Default.Stop,null); Spacer(Modifier.width(6.dp)); Text("עצור")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(pageScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(m.status,Modifier.semantics { liveRegion = LiveRegionMode.Polite },color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
            if(m.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when(m.screen) {
                Screen.HOME -> {
                    Text(if(p.name.isBlank()) "שלום, טוב שבאת" else "שלום, ${p.name}",style=MaterialTheme.typography.headlineMedium)
                    Text(LocalDate.now(ZoneId.of(p.zone)).format(displayDate))
                    if(p.name.isBlank()) Panel { Text("בן משפחה יכול להתחיל בהגדרת השם, העיר ואנשי הקשר."); Action("הגדרה ראשונה",Icons.Default.Settings) { familyGate=true } }
                    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    val diameter=minOf(maxWidth,(208f*maxOf(1f,LocalDensity.current.fontScale/1.15f)).dp)
                    Button(listen,Modifier.size(diameter),enabled=!m.busy,shape=CircleShape,contentPadding=PaddingValues(12.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Mic,null,Modifier.size(52.dp)); Text("דבר איתי",style=MaterialTheme.typography.titleLarge)
                        }
                    }
                    }
                    Text("לחצו ואמרו מה תרצו",Modifier.align(Alignment.CenterHorizontally),style=MaterialTheme.typography.bodyMedium)
                    Action("הבוקר שלי",Icons.Default.WbSunny,enabled=!m.busy) { m.morning() }
                    Action("חדשות",Icons.Default.Newspaper,secondary=true) { m.navigate(Screen.NEWS) }
                    Action("התקשרות",Icons.Default.Call,secondary=true) { m.navigate(Screen.CALLS) }
                    Action("מה קרוב",Icons.Default.Event,secondary=true) { m.navigate(Screen.AGENDA) }
                    Action("התרופות שלי",Icons.Default.Medication,secondary=true) { m.navigate(Screen.MEDICATIONS) }
                    Action("מזג האוויר",Icons.Default.Cloud,secondary=true,enabled=!m.busy) { m.getWeather() }
                    if(m.transcript.isNotBlank()) Panel { Text("אמרת",fontWeight=FontWeight.Bold); Text(m.transcript) }
                }
                Screen.NEWS -> {
                    Heading("מה חדש היום?")
                    Topic.entries.forEach { t -> Action(t.label,Icons.Default.Newspaper,secondary=m.topic!=t,enabled=!m.busy) { m.loadNews(t) } }
                    if(m.newsResult == null && !m.busy) Text("בחרו תחום כדי לטעון חדשות מהמקורות שהוגדרו במשפחה.")
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
                    Text("כולל היום וששת הימים שאחריו")
                    Action("הקראת האירועים",Icons.AutoMirrored.Filled.VolumeUp) { m.loadAgenda(true) }
                    Action("הוספת תזכורת",Icons.Default.Add,secondary=true) { showReminder=true }
                    if(m.agenda.isEmpty()) Panel { Text("אין אירועים בטווח זה. ניתן להוסיף ימי הולדת ולחבר יומן בהגדרות.") }
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
                    Text("אפשר לומר: התקשר לשלום")
                    Action("חיפוש בקול",Icons.Default.Mic,enabled=!m.busy,onClick=listen)
                    if(people.isEmpty()) Text("אין עדיין אנשי קשר. אפשר להוסיף בהגדרות המשפחה.")
                    (m.callChoices.ifEmpty { people }).forEach { person ->
                        Action("${person.name}\n${person.phone.ifBlank { "לא הוגדר מספר" }}",Icons.Default.Call,secondary=true) { m.requestCall(person) }
                    }
                }
                Screen.MEDICATIONS -> {
                    Heading("התרופות שלי")
                    Text("לפי הרשימה והשעות שהוגדרו במשפחה")
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
                        Text("בחרו מה להגדיר. לכל נושא מסך משלו.")
                        SettingsArea.entries.forEach { area -> Action(area.label,Icons.Default.ChevronLeft,secondary=true) { settingsArea=area } }
                    } else {
                    Action("חזרה לתפריט ההגדרות",Icons.Default.ArrowForward,secondary=true) { settingsArea=null }
                    if(settingsArea in listOf(SettingsArea.PROFILE,SettingsArea.VOICE,SettingsArea.CALENDAR))
                        SettingsForm(m,p,speech,settingsArea!!,requestCalendar,requestNotifications,requestExact,appSettings)
                    if(settingsArea==SettingsArea.PEOPLE) Panel {
                        Heading("אנשי קשר וימי הולדת")
                        Action("הוספת איש קשר",Icons.Default.PersonAdd) { editingPerson=null; showPerson=true }
                        Action("יבוא קובץ Excel",Icons.Default.UploadFile,secondary=true,onClick=importFile)
                        Action("הדבקת טבלה",Icons.Default.ContentPaste,secondary=true) { showPaste=true }
                        Text("כותרות: שם, כינוי, טלפון, תאריך לידה. תאריכים: יום/חודש או יום/חודש/שנה. שמרו טלפונים כטקסט ב־Excel.",style=MaterialTheme.typography.bodyMedium)
                        people.forEach { person ->
                            HorizontalDivider(); Text(person.name,fontWeight=FontWeight.Bold); Text(person.phone)
                            Action("עריכה",Icons.Default.Edit,secondary=true) { editingPerson=person; showPerson=true }
                            Action("מחיקה",Icons.Default.Delete,secondary=true) { m.deletePerson(person) }
                        }
                    }
                    if(settingsArea==SettingsArea.MEDICATIONS) Panel {
                        Heading("תרופות לפי חלקי היום")
                        Text("הזינו את שם התרופה, ההנחיות שנקבעו לכם והשעה בכל חלק יום. התזכורות יומיות; האפליקציה אינה קובעת מינון.")
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
                        Text("הוסיפו קישור HTTPS להזנת RSS או Atom. הקישור ייבדק לפני שמירה.")
                        Action("הוספת מקור",Icons.Default.Add) { showSource=true }
                        sources.forEach { source ->
                            Text("${source.name} · ${Topic.valueOf(source.topic).label}"); Text(source.url,style=MaterialTheme.typography.bodyMedium)
                            Action("הסרת מקור",Icons.Default.Delete,secondary=true) { m.deleteSource(source) }
                        }
                    }
                    if(settingsArea==SettingsArea.BACKUP) Panel {
                        Heading("גיבוי והעברה")
                        Text("גיבוי אישי כולל שמות, טלפונים, תאריכים והגדרות. הקובץ אינו מוצפן; שמרו אותו במקום פרטי.")
                        Action("שמירת גיבוי",Icons.Default.Save,secondary=true,onClick=exportFile)
                        Action("שחזור מגיבוי",Icons.Default.Restore,secondary=true,onClick=restoreFile)
                    }
                    }
                }
            }
            if(m.answer.isNotBlank()) Panel {
                Heading("התשובה שלי"); Text(m.answer,Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Action("הקראה שוב",Icons.AutoMirrored.Filled.VolumeUp) { speech.say(m.answer,p) }
                Action("המשך הקראה",Icons.Default.PlayArrow,secondary=true) { speech.resume() }
            }
            Text("מזג אוויר: Open-Meteo · CC BY 4.0",style=MaterialTheme.typography.bodyMedium)
        }
    }
    if(familyGate) AlertDialog(onDismissRequest={familyGate=false},title={Heading("הגדרות למשפחה")},text={Text("כאן מנהלים את פרטי המשתמש, אנשי הקשר והחיבורים.")},
        confirmButton={ TextButton({familyGate=false; settingsArea=null; m.navigate(Screen.SETTINGS)},Modifier.heightIn(min=64.dp)) { Text("כניסה להגדרות") } },
        dismissButton={ TextButton({familyGate=false},Modifier.heightIn(min=64.dp)) { Text("חזרה") } })
    m.confirmation?.let { pending -> AlertDialog(onDismissRequest={m.cancel()},title={Heading("אישור פעולה")},text={ Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(pending.text); Action("לענות בקול",Icons.Default.Mic,onClick=listen)
    } },confirmButton={ TextButton({m.confirm()},Modifier.heightIn(min=64.dp),enabled=!m.busy) { Text("כן, אישור") } },dismissButton={TextButton({m.cancel()},Modifier.heightIn(min=64.dp)) {Text("ביטול")} }) }
    if(showPerson) PersonDialog(editingPerson,onDismiss={showPerson=false},onSave={m.savePerson(it);showPerson=false})
    if(showReminder) ReminderDialog(p,onDismiss={showReminder=false},onSave={title,at -> showReminder=false;m.requestReminder(title,at)})
    if(showPaste) InputDialog("הדבקת רשימה","טבלה עם שורת כותרות",onDismiss={showPaste=false}) { m.previewPaste(it);showPaste=false }
    if(showSource) SourceDialog(onDismiss={showSource=false}) { name,url,topic -> showSource=false;m.saveSource(name,url,topic) }
    if(showMedication) MedicationDialog(editingMedication,onDismiss={showMedication=false}) { med -> showMedication=false;m.requestMedication(med) }
    m.importPreview?.let { preview -> AlertDialog(onDismissRequest={m.importPreview=null},title={Heading("בדיקת היבוא")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${preview.accepted.size} רשומות תקינות להוספה")
            preview.rows.forEach { row -> Text("שורה ${row.line}: ${row.error ?: ((row.person?.name ?: "") + if(row.duplicate) " — כפילות, תידלג" else " — תקין")}") }
        }
    },confirmButton={TextButton({m.acceptImport()},Modifier.heightIn(min=64.dp),enabled=preview.accepted.isNotEmpty()&&!m.busy){Text("יבוא הרשומות התקינות")}},dismissButton={TextButton({m.importPreview=null},Modifier.heightIn(min=64.dp)){Text("ביטול")}}) }
    m.restorePreview?.let { backup -> AlertDialog(onDismissRequest={m.restorePreview=null},title={Heading("שחזור גיבוי")},text={Text("${backup.people.size} אנשי קשר, ${backup.reminders.size} תזכורות, ${backup.medications.size} תרופות ו־${backup.sources.size} מקורות. ההגדרות יוחלפו ורשומות ימוזגו לפי מזהה. יש לבחור יומנים מחדש.")},
        confirmButton={TextButton({m.restorePreview=null;m.runTask{Backup.merge(m.app,backup);m.status="הגיבוי שוחזר"}},Modifier.heightIn(min=64.dp)){Text("אישור שחזור")}},dismissButton={TextButton({m.restorePreview=null},Modifier.heightIn(min=64.dp)){Text("ביטול")}}) }
}

@Composable private fun SettingsForm(m: CompanionModel,p: Profile,speech: DeviceSpeech,area: SettingsArea,requestCalendar:()->Unit,requestNotifications:()->Unit,requestExact:()->Unit,appSettings:()->Unit) {
    var draft by remember(p) { mutableStateOf(p) }; var cityQuery by remember { mutableStateOf("") }
    var hour by remember(p) { mutableStateOf("%02d:%02d".format(p.noticeHour,p.noticeMinute)) }
    var error by remember { mutableStateOf("") }
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
        Text("הפקודות המוגדרות בגרסה זו הן בעברית.",style=MaterialTheme.typography.bodyMedium)
        Text("מהירות דיבור: ${"%.2f".format(draft.rate)}")
        Slider(draft.rate,{draft=draft.copy(rate=it)},valueRange=.5f..1.5f,modifier=Modifier.semantics { contentDescription="מהירות דיבור" })
        Text("קול: ${draft.voice.ifBlank {"ברירת המחדל של המכשיר"}}",style=MaterialTheme.typography.bodyMedium)
        Action("קול ברירת מחדל",secondary=true) {draft=draft.copy(voice="")}
        Text("אפשר להאזין לקולות הזמינים. המכשיר אינו תמיד מספק מידע על גבר או אישה.",style=MaterialTheme.typography.bodyMedium)
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
        Text("בטלפון: הגדרות Android ← חשבונות ← Google ← החשבון שלכם ← סנכרון יומן. לאחר הסנכרון חזרו לכאן ואשרו קריאה.",style=MaterialTheme.typography.bodyMedium)
        if(m.calendarChoices.isEmpty()) Text("עדיין לא נמצאו יומנים. בדקו הרשאת יומן וסנכרון החשבון.")
        m.calendarChoices.forEach { choice -> Row(Modifier.fillMaxWidth().clickable {
            draft=draft.copy(calendarIds=if(choice.id in draft.calendarIds) draft.calendarIds-choice.id else draft.calendarIds+choice.id)
        }.heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
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

@Composable private fun PersonDialog(person: Person?,onDismiss:()->Unit,onSave:(Person)->Unit) {
    var name by remember {mutableStateOf(person?.name.orEmpty())};var aliases by remember {mutableStateOf(person?.aliases.orEmpty())}
    var phone by remember {mutableStateOf(person?.phone.orEmpty())};var birth by remember {mutableStateOf(person?.let { p -> p.birthDay?.let {"$it/${p.birthMonth}"+(p.birthYear?.let {y->"/$y"}?:"")} }.orEmpty())}
    var error by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Heading("איש קשר ויום הולדת")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Field(name,"שם"){name=it};Field(aliases,"כינויים, מופרדים בפסיק"){aliases=it};Field(phone,"מספר טלפון"){phone=it};Field(birth,"תאריך לידה: יום/חודש/שנה"){birth=it};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton({try {
        val preview=PeopleImport.preview(listOf(listOf("שם","כינוי","טלפון","תאריך לידה"),listOf(name,aliases,phone,birth)),emptyList())
        val row=preview.rows.single();require(row.person!=null){row.error.orEmpty()};onSave(row.person.copy(id=person?.id?:row.person.id))
    }catch(e:Exception){error=e.message?:"פרטים לא תקינים"}},Modifier.heightIn(min=64.dp)){Text("שמירה")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=64.dp)){Text("ביטול")}})
}
@Composable private fun ReminderDialog(p:Profile,onDismiss:()->Unit,onSave:(String,LocalDateTime)->Unit) {
    var title by remember {mutableStateOf("")};var date by remember {mutableStateOf(LocalDate.now(ZoneId.of(p.zone)).plusDays(1).toString())};var time by remember {mutableStateOf("09:00")};var error by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Heading("תזכורת חדשה")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Field(title,"מה להזכיר?"){title=it};Field(date,"תאריך, למשל 2026-12-25"){date=it};Field(time,"שעה, למשל 09:00"){time=it};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton({try{val at=LocalDate.parse(date).atTime(LocalTime.parse(time));require(title.isNotBlank()&&at.isAfter(LocalDateTime.now(ZoneId.of(p.zone))));onSave(title,at)}catch(_:Exception){error="יש להזין תיאור, תאריך ושעה עתידיים"}},Modifier.heightIn(min=64.dp)){Text("המשך לאישור")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=64.dp)){Text("ביטול")}})
}
@Composable private fun InputDialog(title:String,label:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember{mutableStateOf("")}
    val clipboard=LocalClipboardManager.current
    AlertDialog(onDismissRequest=onDismiss,title={Heading(title)},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("העתיקו מאקסל את הטבלה יחד עם שורת הכותרות: שם, כינוי, טלפון, תאריך לידה.")
        Action("הדבקה מהלוח",Icons.Default.ContentPaste,secondary=true) {text=clipboard.getText()?.text.orEmpty()}
        Field(text,label){text=it}
    }},confirmButton={TextButton({onSave(text)},Modifier.heightIn(min=64.dp)){Text("תצוגה מקדימה")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=64.dp)){Text("ביטול")}})
}
@Composable private fun SourceDialog(onDismiss:()->Unit,onSave:(String,String,Topic)->Unit) {
    var name by remember{mutableStateOf("")};var url by remember{mutableStateOf("")};var topic by remember{mutableStateOf(Topic.GENERAL)}
    AlertDialog(onDismissRequest=onDismiss,title={Heading("מקור חדשות")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Field(name,"שם האתר"){name=it};Field(url,"קישור RSS המתחיל ב־https://"){url=it}
        Topic.entries.forEach{t->Action((if(topic==t)"✓ " else "")+t.label,secondary=true){topic=t}}
    }},confirmButton={TextButton({onSave(name,url,topic)},Modifier.heightIn(min=64.dp)){Text("בדיקה ושמירה")}},dismissButton={TextButton(onDismiss,Modifier.heightIn(min=64.dp)){Text("ביטול")}})
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
     catch(e:IllegalArgumentException){error=e.message?:"בדקו את הפרטים"}},Modifier.heightIn(min=64.dp)){Text("המשך לאישור")}},
        dismissButton={TextButton(onDismiss,Modifier.heightIn(min=64.dp)){Text("ביטול")}})
}
