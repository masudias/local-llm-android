package ai.altri.jam

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatRoute(
    onClose: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.getFactory(context))

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { chatViewModel.loadDocument(it) }
    }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { chatViewModel.loadContact(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            contactPicker.launch(null)
        } else {
            // Show error message in chat that permissions are required
            chatViewModel.sendMessage("I need contact and SMS permissions to access your chat history.")
        }
    }

    // Reset InferenceModel when entering ChatScreen
    LaunchedEffect(Unit) {
        val inferenceModel = InferenceModel.getInstance(context)
        chatViewModel.resetInferenceModel(inferenceModel)
    }

    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val textInputEnabled by chatViewModel.isTextInputEnabled.collectAsStateWithLifecycle()
    val documentName by chatViewModel.documentName.collectAsStateWithLifecycle()
    ChatScreen(
        context,
        chatViewModel,
        uiState,
        textInputEnabled,
        remainingTokens = chatViewModel.tokensRemaining,
        resetTokenCount = {
            chatViewModel.recomputeSizeInTokens("")
        },
        onSendMessage = { message ->
            chatViewModel.sendMessage(message)
        },
        onChangedMessage = { message ->
            chatViewModel.recomputeSizeInTokens(message)
        },
        onPickFile = {
            filePicker.launch("*/*")
        },
        onPickContact = {
            val requiredPermissions = arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_SMS
            )
            permissionLauncher.launch(requiredPermissions)
        },
        documentName = documentName,
        onClose = onClose
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    context: Context,
    chatViewModel: ChatViewModel,
    uiState: UiState,
    textInputEnabled: Boolean,
    remainingTokens: StateFlow<Int>,
    resetTokenCount: () -> Unit,
    onSendMessage: (String) -> Unit,
    onChangedMessage: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickContact: () -> Unit,
    documentName: String?,
    onClose: () -> Unit
) {
    var userMessage by rememberSaveable { mutableStateOf("") }
    val tokens by remainingTokens.collectAsState(initial = -1)

    Column(
        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom
    ) {
        // Top bar with close button and document name
        documentName?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { chatViewModel.clearDocument() },
                        enabled = textInputEnabled
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove document",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                // Wrap the buttons in another Row to keep them together
                Row {
                    IconButton(
                        onClick = {
                            InferenceModel.getInstance(context).resetSession()
                            uiState.clearMessages()
                            resetTokenCount()
                            chatViewModel.resetSession()
                        }, enabled = textInputEnabled
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear Chat")
                    }
                }
            }
        }

        if (tokens == 0) {
            // Show warning label that context is full
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.context_full_message),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(uiState.messages) { chat ->
                ChatItem(chat)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column { }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onPickFile, modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Default.Add, contentDescription = "Upload Document"
                )
            }

            IconButton(
                onClick = onPickContact, modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Default.Person, contentDescription = "Select Contact"
                )
            }

            TextField(
                value = userMessage,
                onValueChange = {
                    userMessage = it
                    // Only recompute on first word or when we get a new word
                    if (!userMessage.contains(" ") || userMessage.trim() != userMessage) {
                        onChangedMessage(userMessage)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                label = {
                    Text(stringResource(R.string.chat_label))
                },
                modifier = Modifier
                    .weight(0.85f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onChangedMessage(userMessage)
                        }
                    },
                enabled = textInputEnabled,
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.White,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    disabledIndicatorColor = MaterialTheme.colorScheme.primary
                )
            )

            IconButton(
                onClick = {
                    if (userMessage.isNotBlank()) {
                        onSendMessage(userMessage)
                        userMessage = ""
                    }
                },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
                    .fillMaxWidth()
                    .weight(0.15f),
                enabled = textInputEnabled && tokens > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Default.Send,
                    contentDescription = stringResource(R.string.action_send),
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
fun ChatItem(
    chatMessage: ChatMessage
) {
    val backgroundColor = if (chatMessage.isFromUser) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else if (chatMessage.isThinking) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val bubbleShape = if (chatMessage.isFromUser) {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    }

    val horizontalAlignment = if (chatMessage.isFromUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        val author = if (chatMessage.isFromUser) {
            stringResource(R.string.user_label)
        } else if (chatMessage.isThinking) {
            stringResource(R.string.thinking_label)
        } else if (chatMessage.isGeneratingResponse) {
            stringResource(R.string.generating_response_label)
        } else {
            stringResource(R.string.model_label)
        }
        Text(
            text = author,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row {
            BoxWithConstraints {
                Card(
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                    shape = bubbleShape,
                    modifier = Modifier.widthIn(0.dp, maxWidth * 0.9f)
                ) {
                    if (chatMessage.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Text(
                            text = chatMessage.message, modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
