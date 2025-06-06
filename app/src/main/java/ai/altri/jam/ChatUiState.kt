package ai.altri.jam

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "JaM"
const val THINKING_MARKER_END = "</think>"


/** Management of the message queue. */
class UiState(
    private val supportsThinking: Boolean = false,
    messages: List<ChatMessage> = emptyList()
) {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    /** Creates a new loading message. */
    fun createLoadingMessage() {
        val chatMessage = ChatMessage(
            author = MODEL_PREFIX,
            isLoading = true,
            isThinking = supportsThinking,
            isGeneratingResponse = true
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /**
     * Appends the specified text to the message with the specified ID.
     * The underlying implementations may split the re-use messages or create new ones. The method
     * always returns the ID of the message used.
     */
    fun appendMessage(text: String) {
        if (_currentMessageId.isEmpty()) {
            // If no current message, create a new one
            createLoadingMessage()
        }
        val index = _messages.indexOfFirst { it.id == _currentMessageId }

        if (text.contains(THINKING_MARKER_END)) { // The model is done thinking, we add a new bubble
            val thinkingEnd = text.indexOf(THINKING_MARKER_END) + THINKING_MARKER_END.length

            // Add text to current "thinking" bubble
            val prefix = text.substring(0, thinkingEnd);
            val suffix = text.substring(thinkingEnd);

            appendToMessage(_currentMessageId, prefix)

            if (_messages[index].isEmpty) {
                // There are no thoughts from the model. We can just re-use the current bubble
                _messages[index] = _messages[index].copy(
                    isThinking = false
                )
                appendToMessage(_currentMessageId, suffix)
            } else {
                // Create a new bubble for the remainder of the model response
                val message = ChatMessage(
                    rawMessage = suffix,
                    author = MODEL_PREFIX,
                    isLoading = true,
                    isThinking = false,
                    isGeneratingResponse = true
                )
                _messages.add(message)
                _currentMessageId = message.id
            }
        } else {
            appendToMessage(_currentMessageId, text)
        }
    }

    private fun appendToMessage(id: String, suffix: String): Int {
        val index = _messages.indexOfFirst { it.id == id }
        if (index == -1) {
            // If message not found, create a new one
            val message = ChatMessage(
                rawMessage = suffix.replace(THINKING_MARKER_END, ""),
                author = MODEL_PREFIX,
                isLoading = false,
                isGeneratingResponse = true
            )
            _messages.add(message)
            _currentMessageId = message.id
            return _messages.size - 1
        }
        
        val newText = suffix.replace(THINKING_MARKER_END, "")
        _messages[index] = _messages[index].copy(
            rawMessage = _messages[index].rawMessage + newText,
            isLoading = false,
            isGeneratingResponse = true
        )
        return index
    }

    /** Sets isGeneratingResponse flag to false for the current message */
    fun setGeneratingResponseComplete() {
        val index = _messages.indexOfFirst { it.id == _currentMessageId }
        if (index >= 0) {
            _messages[index] = _messages[index].copy(
                isGeneratingResponse = false
            )
        }
    }

    /** Creates a new message with the specified text and author. */
    fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /** Clear all messages. */
    fun clearMessages() {
        _messages.clear()
    }
}
