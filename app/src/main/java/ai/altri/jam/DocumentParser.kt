package ai.altri.jam

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.BufferedReader
import java.io.InputStreamReader

object DocumentParser {
    private val CAMEL_CASE_PATTERN = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()
    private val CLEANUP_PATTERNS = listOf(
        // Fix common PDF parsing issues
        "\\s+" to " ",           // Multiple spaces to single space
        "\\n\\s*\\n" to "\n",   // Multiple newlines to single newline
        "\\f" to "\n",           // Form feed to newline
        "\\r" to "",             // Remove carriage returns
        "\\s*-\\s*\\n\\s*" to ""  // Remove hyphenation at line breaks
    )

    private fun cleanupText(text: String): String {
        var cleaned = text

        // Apply basic cleanup patterns
        CLEANUP_PATTERNS.forEach { (pattern, replacement) ->
            cleaned = cleaned.replace(pattern.toRegex(), replacement)
        }

        // Split camelCase/PascalCase words
        cleaned = cleaned.replace(CAMEL_CASE_PATTERN, " ")

        // Additional cleanup steps
        cleaned = cleaned
            .replace("([a-z])([A-Z])".toRegex()) { matchResult ->
                // Add space between lowercase and uppercase letters
                "${matchResult.groupValues[1]} ${matchResult.groupValues[2]}"
            }
            .replace("([A-Za-z])([0-9])|([0-9])([A-Za-z])".toRegex()) { matchResult ->
                // Add space between letters and numbers
                val groups = matchResult.groupValues
                if (groups[1].isNotEmpty() && groups[2].isNotEmpty()) {
                    "${groups[1]} ${groups[2]}"
                } else {
                    "${groups[3]} ${groups[4]}"
                }
            }
            .replace("\\s+".toRegex(), " ")  // Normalize spaces again
            .trim()

        return cleaned
    }
    fun parseDocument(context: Context, uri: Uri): String {
        return when (getMimeType(context, uri)) {
            "application/pdf" -> parsePdf(context, uri)
            "text/plain" -> parseText(context, uri)
            else -> throw IllegalArgumentException("Unsupported file type")
        }
    }

    private fun parsePdf(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val pdfReader = PdfReader(inputStream)
            val pdfDocument = PdfDocument(pdfReader)
            val stringBuilder = StringBuilder()

            for (i in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(i)
                val rawText = PdfTextExtractor.getTextFromPage(page)
                stringBuilder.append(cleanupText(rawText))
                stringBuilder.append("\n")
            }

            pdfDocument.close()
            pdfReader.close()
            return stringBuilder.toString()
        } ?: throw IllegalStateException("Could not open PDF file")
    }

    private fun parseText(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            return cleanupText(reader.readText())
        } ?: throw IllegalStateException("Could not open text file")
    }

    private fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: throw IllegalArgumentException("Unknown file type")
    }
}
