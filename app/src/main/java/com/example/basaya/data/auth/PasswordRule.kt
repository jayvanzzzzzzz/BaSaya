package com.example.basaya.data.auth

import android.graphics.Color
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import com.example.basaya.R

/**
 * The rule for every password a student picks (register, change password): at least
 * 8 characters with a small letter, a capital letter and a number.
 * Same as basaya-web (js/password-rules.js).
 */
object PasswordRule {

    const val MIN_LENGTH = 8

    private fun hasLength(password: String) = password.length >= MIN_LENGTH
    private fun hasLower(password: String) = password.any { it in 'a'..'z' }
    private fun hasUpper(password: String) = password.any { it in 'A'..'Z' }
    private fun hasNumber(password: String) = password.any { it in '0'..'9' }

    /** The message for the first part of the rule [password] misses, or null when it passes. */
    @StringRes
    fun problem(password: String): Int? = when {
        !hasLength(password) -> R.string.password_error_length
        !hasLower(password) -> R.string.password_error_lower
        !hasUpper(password) -> R.string.password_error_upper
        !hasNumber(password) -> R.string.password_error_number
        else -> null
    }

    /**
     * Ticks each line of the checklist [rules] (an included view_password_rules.xml)
     * as soon as what's typed in [input] meets it.
     */
    fun watch(input: EditText, rules: View) {
        val lines = listOf<Pair<TextView, (String) -> Boolean>>(
            rules.findViewById<TextView>(R.id.ruleLength) to ::hasLength,
            rules.findViewById<TextView>(R.id.ruleLower) to ::hasLower,
            rules.findViewById<TextView>(R.id.ruleUpper) to ::hasUpper,
            rules.findViewById<TextView>(R.id.ruleNumber) to ::hasNumber
        )
        fun update(password: String) = lines.forEach { (line, check) ->
            val met = check(password)
            line.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (met) R.drawable.ic_rule_met else R.drawable.ic_rule_pending, 0, 0, 0
            )
            line.setTextColor(Color.parseColor(if (met) "#26334D" else "#778199"))
        }
        input.doAfterTextChanged { update(it?.toString().orEmpty()) }
        update(input.text?.toString().orEmpty())
    }
}
