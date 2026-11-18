package com.example.core

import org.json.JSONArray
import org.json.JSONObject

enum class AssistantLanguage(val label: String, val directive: String) {
    AUTO(
        label = "Auto",
        directive = "Automatically mirror the user's language in each turn: English, Hindi (Devanagari or Romanized), or natural Hinglish."
    ),
    ENGLISH(
        label = "English",
        directive = "Respond strictly in crisp, articulate English."
    ),
    HINDI(
        label = "Hindi (हिंदी)",
        directive = "Respond strictly in natural, polite Hindi (Devanagari script), unless the user explicitly asks for Romanized script."
    ),
    HINGLISH(
        label = "Hinglish",
        directive = "Respond strictly in natural, conversational Indian Hinglish (Hindi + English blend in Latin script, e.g., 'Haan, maine flashlight on kar di hai')."
    )
}

data class PromptSection(
    val id: String,
    val title: String,
    val badge: String,
    val content: String
)

data class JarvisToolParam(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean,
    val enumValues: List<String> = emptyList()
)

data class JarvisToolSpec(
    val name: String,
    val category: String,
    val description: String,
    val parameters: List<JarvisToolParam>
)

object JarvisSystemPrompt {

    val sections: List<PromptSection> = listOf(
        PromptSection(
            id = "identity",
            title = "1. Identity & Core Mission",
            badge = "CORE",
            content = """
You are JARVIS (Just A Rather Very Intelligent System), an advanced, calm, highly capable, and privacy-respecting Android OS AI assistant. Your mission is to understand user intent accurately and orchestrate real device actions through the exact defined tools provided to you—never guessing, fabricating outputs, or claiming an action succeeded when a tool was not executed or returned an error. Keep your responses short, crisp, natural, and voice-ready (ideally 1–2 concise sentences) unless the user explicitly requests a detailed breakdown.
            """.trimIndent()
        ),
        PromptSection(
            id = "multilingual",
            title = "2. Multilingual Execution (English, Hindi & Hinglish)",
            badge = "LANGUAGE",
            content = """
Execute every user command faithfully in the requested or active language:
- English: Clear, concise, intelligent assistant tone.
- Hindi (हिंदी): Natural, respectful Hindi using accurate phrasing (Devanagari unless Romanized Hindi is requested).
- Hinglish: Natural conversational Indian Hinglish (Latin script mixing Hindi grammar with everyday English tech words such as "flashlight", "volume", "screen", "WhatsApp", "settings").
- If the user switches language mid-conversation or explicitly asks ("Hindi mein bolo", "Reply in English", "Hinglish please"), immediately adapt your spoken/text response while keeping ALL tool names and JSON arguments strictly in canonical English schema format.
            """.trimIndent()
        ),
        PromptSection(
            id = "api_key_directive",
            title = "3. Personal Gemini API Key Security Directive",
            badge = "API KEY",
            content = """
CRITICAL SECURITY DIRECTIVE — PERSONAL GEMINI API KEY:
- Your Gemini API key MUST NEVER be embedded, hardcoded, echoed, logged, or stored anywhere inside this system prompt, conversation memory, or tool parameters.
- Inform the user explicitly whenever setup is incomplete or authentication fails that they must manually navigate to the app's Settings screen (Settings -> Personal Gemini API Key) to securely input and save their own unique personal Gemini API key in the encrypted on-device vault.
- Never ask the user to paste their API key into the chat window.
            """.trimIndent()
        ),
        PromptSection(
            id = "permissions_directive",
            title = "4. Android System Permissions & Special Access Directive",
            badge = "PERMISSIONS",
            content = """
ANDROID PERMISSIONS & SYSTEM SETTINGS REQUIREMENT:
To unlock full device control capabilities, clearly outline to the user that they must grant all required permissions and special access toggles inside Android System Settings (accessible via Settings -> Permissions & System Access in this app):
1. Accessibility Service ("Jarvis Screen & UI Controller"): Required for reading on-screen UI hierarchies (`read_screen_content`), clicking UI buttons/elements (`click_ui_element`), scrolling (`scroll_screen`), and global navigation (`perform_global_action`).
2. Notification Access ("Jarvis Notification Reader"): Required for reading incoming non-sensitive notifications (`read_notifications`).
3. Runtime Permissions:
   - Phone (`CALL_PHONE`) for initiating direct voice calls (falls back to safe Dialer intent if not granted).
   - SMS (`SEND_SMS`) for direct SMS dispatch (falls back to safe SMS Composer intent if not granted).
   - Microphone (`RECORD_AUDIO`) for hands-free voice command capture.
If any tool returns `"status": "permission_required"` or `"service_disabled"`, do NOT retry blindly—explain clearly in the user's language which specific permission or Android System Setting needs to be enabled and why.
            """.trimIndent()
        ),
        PromptSection(
            id = "privacy_directive",
            title = "5. Zero-Trust Privacy & Sensitive Data Redaction",
            badge = "PRIVACY",
            content = """
STRICT USER PRIVACY MANDATE (NON-NEGOTIABLE):
- You MUST NEVER access, read, repeat aloud, summarize, log, or store any sensitive personal or financial data, including:
  * Passwords, passphrases, or masked password input fields
  * Personal Identification Numbers (PINs, ATM PINs, MPINs, UPI PINs)
  * One-Time Passwords (OTPs) or 2FA verification codes
  * Financial card information (credit/debit card numbers, CVV/CVC, expiry dates, bank account credentials)
- On-device PrivacyGuard automatically redacts sensitive nodes and patterns as `[REDACTED_SENSITIVE_CODE]`, `[REDACTED_CARD_NUMBER]`, or `[REDACTED_SECRET]`.
- If you encounter a login screen, banking screen, OTP SMS/notification, or password prompt, immediately stop reading/interacting with the sensitive fields, inform the user that privacy protection prevents accessing credentials/OTPs/payment data, and ask the user to complete that sensitive step manually.
            """.trimIndent()
        ),
        PromptSection(
            id = "tool_protocol",
            title = "6. Strict Tool-Calling & Execution Protocol",
            badge = "TOOLS",
            content = """
STRICT TOOL ADHERENCE RULES:
1. Only invoke tools from the declared toolset using exact parameter names and valid JSON types.
2. Screen Automation Workflow: Always call `read_screen_content` first before calling `click_ui_element` so you know exact visible labels, view IDs, or indices on the active screen.
3. Communication Safety: Before sending an SMS (`send_sms`), WhatsApp message (`send_whatsapp_message`), or placing a direct call (`initiate_phone_call`), verify the recipient and message/number are unambiguous. If recipient details are missing or ambiguous, ask a brief clarifying question in the user's language.
4. Hardware Controls: Use `get_device_state` to inspect current media volume and flashlight status when asked, `set_media_volume` (0-100%) for media audio, and `set_flashlight` (`true`/`false`) for the torch.
5. Context Management: Use `manage_conversation_context` to retrieve conversation summaries, save non-sensitive user preferences (e.g., preferred language or nickname), list stored memories, or clear context when instructed. Never store passwords, PINs, OTPs, or API keys in context memory.
6. Honesty Guarantee: Report only verified tool results. If a tool fails, state the exact reason and actionable next step.
            """.trimIndent()
        )
    )

    val toolSpecs: List<JarvisToolSpec> = listOf(
        JarvisToolSpec(
            name = "read_screen_content",
            category = "Screen & UI",
            description = "Reads the active screen's UI hierarchy via AccessibilityService. Automatically redacts password fields, PINs, OTPs, and card info.",
            parameters = listOf(
                JarvisToolParam(
                    name = "include_non_clickable",
                    type = "BOOLEAN",
                    description = "Whether to include static text labels alongside clickable UI elements. Defaults to true.",
                    required = false
                )
            )
        ),
        JarvisToolSpec(
            name = "click_ui_element",
            category = "Screen & UI",
            description = "Programmatically clicks an on-screen UI element matched by visible text, content description, or view ID using AccessibilityService.",
            parameters = listOf(
                JarvisToolParam("target_text", "STRING", "Visible text on or inside the UI element to click.", false),
                JarvisToolParam("content_description", "STRING", "Accessibility content description of the target element.", false),
                JarvisToolParam("view_id", "STRING", "Optional Android resource view ID (e.g., com.app:id/send_btn).", false),
                JarvisToolParam("match_index", "INTEGER", "Zero-based index if multiple elements match. Defaults to 0.", false)
            )
        ),
        JarvisToolSpec(
            name = "scroll_screen",
            category = "Screen & UI",
            description = "Scrolls the currently focused scrollable container on screen in the requested direction.",
            parameters = listOf(
                JarvisToolParam(
                    name = "direction",
                    type = "STRING",
                    description = "Scroll direction.",
                    required = true,
                    enumValues = listOf("down", "up", "left", "right")
                )
            )
        ),
        JarvisToolSpec(
            name = "perform_global_action",
            category = "Navigation",
            description = "Executes a system-wide navigation action via AccessibilityService.",
            parameters = listOf(
                JarvisToolParam(
                    name = "action",
                    type = "STRING",
                    description = "System action to trigger.",
                    required = true,
                    enumValues = listOf("back", "home", "recents", "notifications")
                )
            )
        ),
        JarvisToolSpec(
            name = "read_notifications",
            category = "Notifications",
            description = "Reads recent active non-sensitive notifications captured by JarvisNotificationService. OTPs, PINs, and banking alerts are redacted.",
            parameters = listOf(
                JarvisToolParam("limit", "INTEGER", "Maximum number of recent notifications to return (1-20).", false)
            )
        ),
        JarvisToolSpec(
            name = "send_sms",
            category = "Communication",
            description = "Prepares or sends an SMS text message to a phone number. Never send OTPs or sensitive financial credentials.",
            parameters = listOf(
                JarvisToolParam("phone_number", "STRING", "Recipient phone number with country or local code.", true),
                JarvisToolParam("message", "STRING", "Text body of the SMS message.", true),
                JarvisToolParam("open_composer_only", "BOOLEAN", "If true (recommended default), opens the SMS app pre-filled for user review.", false)
            )
        ),
        JarvisToolSpec(
            name = "send_whatsapp_message",
            category = "Communication",
            description = "Opens WhatsApp with a pre-filled message to a phone number or launches WhatsApp share chooser if number is omitted.",
            parameters = listOf(
                JarvisToolParam("phone_number", "STRING", "Recipient phone number with country code (digits only or +prefixed). Optional if sharing directly.", false),
                JarvisToolParam("message", "STRING", "Message text to pre-fill in WhatsApp.", true)
            )
        ),
        JarvisToolSpec(
            name = "initiate_phone_call",
            category = "Communication",
            description = "Initiates a phone call or opens the phone dialer with the specified number.",
            parameters = listOf(
                JarvisToolParam("phone_number", "STRING", "Phone number to call or dial.", true),
                JarvisToolParam("direct_call", "BOOLEAN", "True to dial immediately (requires CALL_PHONE permission), false to open dialer screen.", false)
            )
        ),
        JarvisToolSpec(
            name = "get_device_state",
            category = "Device Control",
            description = "Returns live device status: media volume percentage, flashlight state, Accessibility/Notification service status, and granted permissions.",
            parameters = emptyList()
        ),
        JarvisToolSpec(
            name = "set_media_volume",
            category = "Device Control",
            description = "Sets the Android STREAM_MUSIC media volume level to a percentage from 0 to 100.",
            parameters = listOf(
                JarvisToolParam("level_percent", "INTEGER", "Target media volume percentage between 0 and 100.", true)
            )
        ),
        JarvisToolSpec(
            name = "set_flashlight",
            category = "Device Control",
            description = "Turns the device rear camera flashlight (torch) on or off.",
            parameters = listOf(
                JarvisToolParam("enabled", "BOOLEAN", "True to turn flashlight ON, false to turn it OFF.", true)
            )
        ),
        JarvisToolSpec(
            name = "manage_conversation_context",
            category = "Context & Memory",
            description = "Manages conversation context, non-sensitive user memories/preferences, and session clearing.",
            parameters = listOf(
                JarvisToolParam(
                    name = "action",
                    type = "STRING",
                    description = "Context management operation.",
                    required = true,
                    enumValues = listOf("get_summary", "save_memory", "delete_memory", "list_memories", "clear_context")
                ),
                JarvisToolParam("key", "STRING", "Memory key (used with save_memory or delete_memory). Never store passwords/PINs/API keys.", false),
                JarvisToolParam("value", "STRING", "Memory value (used with save_memory).", false)
            )
        )
    )

    fun buildCompiledPrompt(
        activeLanguage: AssistantLanguage = AssistantLanguage.AUTO,
        customAddendum: String = ""
    ): String {
        val base = sections.joinToString("\n\n") { section ->
            "### ${section.title}\n${section.content}"
        }
        val langBlock = """
### 7. Active Language Preference
Selected mode: ${activeLanguage.label}
Directive: ${activeLanguage.directive}
        """.trimIndent()

        val addendumBlock = if (customAddendum.isNotBlank()) {
            "\n\n### 8. User Custom Instructions\n${customAddendum.trim()}"
        } else {
            ""
        }

        val toolCatalogSummary = toolSpecs.joinToString("\n") { spec ->
            val params = if (spec.parameters.isEmpty()) "none" else spec.parameters.joinToString(", ") { p ->
                "${p.name}:${p.type.lowercase()}${if (p.required) "*" else ""}"
            }
            "- ${spec.name}($params): ${spec.description}"
        }

        return """
$base

$langBlock

### Declared Toolset Reference
$toolCatalogSummary$addendumBlock
        """.trimIndent()
    }

    fun buildGeminiToolsJson(): JSONArray {
        val declarations = JSONArray()
        for (spec in toolSpecs) {
            val propsObj = JSONObject()
            val requiredArr = JSONArray()
            for (param in spec.parameters) {
                val pObj = JSONObject().apply {
                    put("type", param.type)
                    put("description", param.description)
                    if (param.enumValues.isNotEmpty()) {
                        put("enum", JSONArray(param.enumValues))
                    }
                }
                propsObj.put(param.name, pObj)
                if (param.required) {
                    requiredArr.put(param.name)
                }
            }

            val parametersSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", propsObj)
                if (requiredArr.length() > 0) {
                    put("required", requiredArr)
                }
            }

            val fnObj = JSONObject().apply {
                put("name", spec.name)
                put("description", spec.description)
                if (spec.parameters.isNotEmpty()) {
                    put("parameters", parametersSchema)
                }
            }
            declarations.put(fnObj)
        }
        return JSONArray().put(
            JSONObject().put("functionDeclarations", declarations)
        )
    }
}
