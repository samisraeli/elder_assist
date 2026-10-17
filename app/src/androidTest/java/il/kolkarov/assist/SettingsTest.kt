package il.kolkarov.assist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CompanionApp
    private fun settings() {
        compose.onNodeWithContentDescription("הגדרות למשפחה").performClick()
        compose.onNodeWithText("כניסה להגדרות").performClick()
    }
    @After fun cleanTestMedication() = runBlocking {
        app.database.dao().medicationsNow().filter {it.name=="תרופה לבדיקת ממשק"}.forEach {app.database.dao().deleteMedication(it)}
        NoticeScheduler(app).refresh()
    }
    @Test fun languageFontAddressImportAndSourcesAreDiscoverable() {
        settings()
        compose.onNodeWithText("שפה, קול וגודל גופן").performScrollTo().performClick()
        compose.onNodeWithText("שפת הדיבור וההקראה; הממשק נשאר בעברית").assertExists()
        compose.onNodeWithContentDescription("גודל גופן").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("חזרה לתפריט ההגדרות").performScrollTo().performClick()
        compose.onNodeWithText("שם, כתובת ומיקום").performScrollTo().performClick()
        compose.onNodeWithText("כתובת").assertExists()
        compose.onNodeWithText("חזרה לתפריט ההגדרות").performScrollTo().performClick()
        compose.onNodeWithText("אנשי קשר וימי הולדת").performScrollTo().performClick()
        compose.onNodeWithText("ימי הולדת").performClick()
        compose.onNodeWithText("יבוא קובץ Excel").assertExists()
        compose.onNodeWithText("הדבקת טבלה").assertExists()
        compose.onNodeWithText("חזרה לתפריט ההגדרות").performScrollTo().performClick()
        compose.onNodeWithText("אתרי חדשות וספורט").performScrollTo().performClick()
        compose.onNodeWithText("הוספת מקור").assertExists()
    }
    @Test fun medicationIsSavedOnlyAfterReviewAndCreatesRecurringNotices() {
        settings()
        compose.onNodeWithText("תרופות לפי חלקי היום").performScrollTo().performClick()
        compose.onNodeWithText("הוספת תרופה").performScrollTo().performClick()
        compose.onNodeWithText("שם התרופה").performTextInput("תרופה לבדיקת ממשק")
        compose.onNodeWithContentDescription("בוקר").performScrollTo().performClick()
        compose.onNodeWithText("שעה לבוקר").performScrollTo().performTextInput("08:00")
        compose.onNodeWithText("המשך לאישור").performClick()
        assertTrue(runBlocking { app.database.dao().medicationsNow().none {it.name=="תרופה לבדיקת ממשק"} })
        compose.onNodeWithText("כן, אישור").performClick()
        compose.waitUntil(15000) {runBlocking {app.database.dao().medicationsNow().any {it.name=="תרופה לבדיקת ממשק"}}}
        runBlocking {
            NoticeScheduler(app).refresh()
            assertTrue(app.database.dao().notices().any {it.key.startsWith("medication:") && it.title.contains("תרופה לבדיקת ממשק")})
        }
    }
}
