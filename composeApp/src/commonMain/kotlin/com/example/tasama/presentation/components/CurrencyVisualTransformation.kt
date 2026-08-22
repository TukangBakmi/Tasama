package com.example.tasama.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        // 1000 -> 1.000
        val formattedText = originalText.reversed().chunked(3).joinToString(".").reversed()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val totalDots = (originalText.length - 1) / 3
                val dotsAfterOffset = (originalText.length - offset) / 3
                return offset + (totalDots - dotsAfterOffset)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val dotsBefore = formattedText.substring(0, offset.coerceAtMost(formattedText.length)).count { it == '.' }
                return (offset - dotsBefore).coerceAtMost(originalText.length)
            }
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}
