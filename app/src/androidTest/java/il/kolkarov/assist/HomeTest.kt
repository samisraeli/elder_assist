package il.kolkarov.assist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class HomeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mainVoiceButtonAndPersistentNavigationAreVisible() {
        compose.onNodeWithText("דבר איתי").assertIsDisplayed()
        compose.onNodeWithText("בית",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("עצור",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun contactsEmptyStateIsHonest() {
        compose.onNodeWithText("התקשרות").performScrollTo().performClick()
        compose.onNodeWithText("אין עדיין אנשי קשר. אפשר להוסיף בהגדרות המשפחה.").assertExists()
    }
}
