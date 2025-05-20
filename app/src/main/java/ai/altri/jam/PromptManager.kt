package ai.altri.jam

import android.content.Context
import java.io.IOException

enum class PromptTemplate {
    CONTENT_QUERY, SEND_MESSAGE
}

class PromptManager(private val context: Context) {
    private val templateCache = mutableMapOf<PromptTemplate, String>()

    fun getPrompt(template: PromptTemplate, userMessage: String, relevantInfo: String): String {
        val templateContent = getTemplateContent(template)
        return String.format(templateContent, userMessage, relevantInfo)
    }

    private fun getTemplateContent(template: PromptTemplate): String {
        // Return cached template if available
        templateCache[template]?.let { return it }

        // Load template from assets
        val fileName = when (template) {
            PromptTemplate.CONTENT_QUERY -> "prompts/content_query.txt"
            PromptTemplate.SEND_MESSAGE -> "prompts/send_a_message.txt"
        }

        try {
            context.assets.open(fileName).bufferedReader().use { reader ->
                val content = reader.readText()
                templateCache[template] = content
                return content
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load prompt template: $fileName", e)
        }
    }
}
