package com.example.security

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

data class RedactionResult(
    val sanitizedText: String,
    val redactedCount: Int
)

object PrivacyGuard {

    private val sensitiveViewIdHints = listOf(
        "password", "passwd", "passcode", "pin_code", "pincode", "mpin", "upi_pin",
        "otp", "verification_code", "auth_code", "cvv", "cvc", "card_number",
        "credit_card", "debit_card", "expiry", "routing_number", "ssn", "secret"
    )

    private val otpOrPinContextRegex = Regex(
        pattern = """(?i)\b(otp|one[- ]?time[- ]?password|verification\s*code|auth(?:entication)?\s*code|security\s*code|passcode|password|mpin|upi\s*pin|atm\s*pin|pin|cvv|cvc)\b([^0-9\n]{0,24})(\d{3,8})\b"""
    )

    private val standaloneOtpSuffixRegex = Regex(
        pattern = """(?i)\b(\d{4,8})\b([^0-9\n]{0,20})\b(is your (?:otp|verification code|pin|passcode|security code))\b"""
    )

    private val paymentCardRegex = Regex(
        pattern = """\b(?:\d[ -]*?){13,19}\b"""
    )

    private val explicitPasswordAssignmentRegex = Regex(
        pattern = """(?i)\b(password|passcode|pin|cvv|secret|token)\s*[:=]\s*\S+"""
    )

    fun isSensitiveNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
        ) {
            return true
        }
        if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return true
        }

        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        if (sensitiveViewIdHints.any { viewId.contains(it) }) {
            return true
        }

        val hint = node.hintText?.toString()?.lowercase().orEmpty()
        if (sensitiveViewIdHints.any { hint.contains(it) }) {
            return true
        }

        return false
    }

    fun redactSensitiveText(input: String): RedactionResult {
        if (input.isBlank()) return RedactionResult(input, 0)
        var text = input
        var redactions = 0

        text = otpOrPinContextRegex.replace(text) { match ->
            redactions++
            "${match.groupValues[1]}${match.groupValues[2]}[REDACTED_SENSITIVE_CODE]"
        }

        text = standaloneOtpSuffixRegex.replace(text) { match ->
            redactions++
            "[REDACTED_SENSITIVE_CODE]${match.groupValues[2]}${match.groupValues[3]}"
        }

        text = paymentCardRegex.replace(text) { match ->
            val digitsOnly = match.value.filter { it.isDigit() }
            if (digitsOnly.length in 13..19 && passesLuhnCheck(digitsOnly)) {
                redactions++
                "[REDACTED_CARD_NUMBER]"
            } else {
                match.value
            }
        }

        text = explicitPasswordAssignmentRegex.replace(text) { match ->
            redactions++
            "${match.groupValues[1]}: [REDACTED_SECRET]"
        }

        return RedactionResult(text, redactions)
    }

    private fun passesLuhnCheck(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
