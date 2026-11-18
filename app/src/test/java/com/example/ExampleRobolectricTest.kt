package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AssistantLanguage
import com.example.core.JarvisSystemPrompt
import com.example.security.PrivacyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis Assistant", appName)
    }

    @Test
    fun `compiled system prompt contains all required directives without storing key`() {
        val prompt = JarvisSystemPrompt.buildCompiledPrompt(AssistantLanguage.HINGLISH)
        assertTrue(prompt.contains("English"))
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("Hinglish"))
        assertTrue(prompt.contains("Settings -> Personal Gemini API Key"))
        assertTrue(prompt.contains("Accessibility Service"))
        assertTrue(prompt.contains("Notification Access"))
        assertTrue(prompt.contains("OTPs"))
        assertTrue(prompt.contains("read_screen_content"))
        assertTrue(prompt.contains("click_ui_element"))
        assertTrue(prompt.contains("send_sms"))
        assertTrue(prompt.contains("send_whatsapp_message"))
        assertTrue(prompt.contains("initiate_phone_call"))
        assertTrue(prompt.contains("set_media_volume"))
        assertTrue(prompt.contains("set_flashlight"))
        assertTrue(prompt.contains("manage_conversation_context"))
    }

    @Test
    fun `privacy guard redacts otps pins passwords and cards`() {
        val otpMsg = PrivacyGuard.redactSensitiveText("Your OTP is 482910 for login.")
        assertFalse(otpMsg.sanitizedText.contains("482910"))
        assertTrue(otpMsg.redactedCount >= 1)

        val pinMsg = PrivacyGuard.redactSensitiveText("Enter UPI PIN: 9482 to proceed")
        assertFalse(pinMsg.sanitizedText.contains("9482"))

        val passMsg = PrivacyGuard.redactSensitiveText("password=MySecretValue123")
        assertFalse(passMsg.sanitizedText.contains("MySecretValue123"))
    }

    @Test
    fun `default model is gemini 3_5 flash`() {
        assertEquals("gemini-3.5-flash", com.example.data.JarvisSettingsData.DEFAULT_GEMINI_MODEL)
    }
}
