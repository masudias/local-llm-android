package ai.altri.jam

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

private const val TAG = "ChatViewModel"

class ChatViewModel(
    private var inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {
    private val promptManager = PromptManager(context)
    private val contentSelector = ContentSelector(context)
    private val contactSmsHandler = ContactSmsHandler(context)
    private var fullDocumentContent: String = ""
    private var currentJob: Job? = null

    private val _documentName = MutableStateFlow<String?>(null)
    val documentName: StateFlow<String?> = _documentName.asStateFlow()

    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    fun clearDocument() {
        contentSelector.cancelOngoing()
        currentJob?.cancel()
        fullDocumentContent = ""
        _documentName.value = null
        _selectedContact.value = null
        _uiState.value = inferenceModel.uiState
        recomputeSizeInTokens("")
    }

    fun resetSession() {
        contentSelector.cancelOngoing()
        currentJob?.cancel()
        viewModelScope.launch {
            delay(100) // Give time for cancellation to complete
            inferenceModel.resetSession()
            _uiState.value = inferenceModel.uiState
            recomputeSizeInTokens("")
        }
    }

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(inferenceModel.uiState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _tokensRemaining = MutableStateFlow(-1)
    val tokensRemaining: StateFlow<Int> = _tokensRemaining.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    fun resetInferenceModel(newModel: InferenceModel) {
        inferenceModel = newModel
        _uiState.value = inferenceModel.uiState
    }

    fun sendMessage(userMessage: String) {
        contentSelector.cancelOngoing()
        currentJob?.cancel() // Cancel any ongoing job
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            _uiState.value.createLoadingMessage()

            // Get full content from document, SMS messages, or default file
            val sourceContent = when {
                _selectedContact.value != null -> {
                    val contact = _selectedContact.value!!
                    val messages = contactSmsHandler.getLastSmsMessages(contact.phoneNumber)
                    buildString {
                        appendLine("Chat history with ${contact.name}:")
                        messages.forEach { sms ->
                            val prefix = if (sms.type == 1) "${contact.name}:" else "User:"
                            appendLine("$prefix ${sms.body}")
                        }
                    }
                }

                fullDocumentContent.isNotEmpty() -> fullDocumentContent
                else -> context.assets.open("about_jam.txt").bufferedReader().use { it.readText() }
            }

            // Get relevant content based only on the current question
            val relevantInfo = contentSelector.selectRelevantContent(
                sourceContent,
                userMessage,  // Use only current question
                inferenceModel
            )

            Log.d(TAG, "Selected relevant content of length: ${relevantInfo.length}")
            Log.d(TAG, "Relevant info: $relevantInfo")

            val template = if (_selectedContact.value != null) {
                PromptTemplate.SEND_MESSAGE
            } else {
                PromptTemplate.CONTENT_QUERY
            }
            
            val prompt = promptManager.getPrompt(template, userMessage, relevantInfo)
            Log.d(TAG, "Prompt used: $prompt")

            setInputEnabled(false)
            try {
                val asyncInference =
                    inferenceModel.generateResponseAsync(prompt, { partialResult, done ->
                        _uiState.value.appendMessage(partialResult)
                        if (done) {
                            setInputEnabled(true)  // Re-enable text input
                        } else {
                            // Reduce current token count (estimate only). sizeInTokens() will be used
                            // when computation is done
                            _tokensRemaining.update { max(0, it - 1) }
                        }
                    })
                // Once the inference is done, recompute the remaining size in tokens
                asyncInference.addListener({
                    viewModelScope.launch(Dispatchers.IO) {
                        recomputeSizeInTokens(userMessage)
                        inferenceModel.resetSession()
                    }
                }, Dispatchers.Main.asExecutor())
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled

        // When re-enabling input, set isGeneratingResponse to false
        if (isEnabled) {
            _uiState.value.setGeneratingResponseComplete()
        }
    }

    fun recomputeSizeInTokens(message: String) {
        val remainingTokens = inferenceModel.estimateTokensRemaining(message)
        _tokensRemaining.value = remainingTokens
    }

    fun loadContact(uri: Uri) {
        contentSelector.cancelOngoing()
        currentJob?.cancel()
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!contactSmsHandler.hasRequiredPermissions()) {
                    _uiState.value.addMessage(
                        "Error: Contact and SMS permissions are required to use this feature.",
                        MODEL_PREFIX
                    )
                    return@launch
                }

                val contact = contactSmsHandler.getContact(uri)
                if (contact != null) {
                    _selectedContact.value = contact
                    _documentName.value = contact.name
                    fullDocumentContent = "" // Clear any loaded document
                    _uiState.value = inferenceModel.uiState
                    recomputeSizeInTokens("")
                } else {
                    _uiState.value.addMessage(
                        "Error: Could not load contact information",
                        MODEL_PREFIX
                    )
                }
            } catch (e: Exception) {
                _uiState.value.addMessage(
                    "Error loading contact: ${e.localizedMessage}",
                    MODEL_PREFIX
                )
            }
        }
    }

    fun loadDocument(uri: Uri) {
        contentSelector.cancelOngoing()
        currentJob?.cancel() // Cancel any ongoing job
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                fullDocumentContent = DocumentParser.parseDocument(context, uri)
                _uiState.value = inferenceModel.uiState // Reset the chat
                recomputeSizeInTokens("") // Reset token count

                // Get and set the document name
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        _documentName.value = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                _uiState.value.addMessage(
                    "Error loading document: ${e.localizedMessage}",
                    MODEL_PREFIX
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentSelector.cleanup()
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val inferenceModel = InferenceModel.getInstance(context)
                return ChatViewModel(inferenceModel, context) as T
            }
        }
    }
}
