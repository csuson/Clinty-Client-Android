package com.clinty.client.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinty.client.models.HumanInterrupt
import com.clinty.client.models.InterruptDescriptionParser
import com.clinty.client.models.ParsedDraftSection
import com.clinty.client.models.ParsedEmailSection
import com.clinty.client.models.ParsedInterruptDescription
import com.clinty.client.models.SubmitType
import com.clinty.client.services.PendingHumanResponse
import com.clinty.client.viewmodels.ThreadDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    viewModel: ThreadDetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val threadData by viewModel.threadData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedSubmitType by viewModel.selectedSubmitType.collectAsState()
    val responseText by viewModel.responseText.collectAsState()
    val editedArgs by viewModel.editedArgs.collectAsState()
    val pendingResponses by viewModel.pendingResponses.collectAsState()
    val threadValuesText by viewModel.threadValuesText.collectAsState()
    val shouldReturnToInbox by viewModel.shouldReturnToInbox.collectAsState()

    val interrupt = viewModel.interrupt
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.needsLoad) {
        if (viewModel.needsLoad) {
            viewModel.load()
        }
    }

    LaunchedEffect(shouldReturnToInbox) {
        if (shouldReturnToInbox) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = interrupt?.actionRequest?.action ?: "Thread",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (interrupt?.config?.allowIgnore == true) {
                            DropdownMenuItem(
                                text = { Text("Ignore") },
                                onClick = {
                                    showMenu = false
                                    viewModel.ignoreInterrupt()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Resolve (end thread)") },
                            onClick = {
                                showMenu = false
                                viewModel.resolveThread { success ->
                                    if (success) onNavigateBack()
                                }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (viewModel.canSubmit) {
                SubmitBar(
                    label = submitLabel(selectedSubmitType),
                    isSubmitting = isSubmitting,
                    onSubmit = viewModel::submit,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                interrupt != null -> {
                    InterruptHeader(interrupt)
                    InterruptActionView(
                        interrupt = interrupt,
                        selectedSubmitType = selectedSubmitType,
                        onSubmitTypeChange = viewModel::updateSelectedSubmitType,
                        responseText = responseText,
                        onResponseTextChange = viewModel::updateResponseText,
                        editedArgs = editedArgs,
                        onEditedArgChange = viewModel::updateEditedArg,
                        pendingResponses = pendingResponses,
                    )
                }
                viewModel.isIdle -> {
                    IdleThreadView(threadValuesText)
                }
                threadData?.invalidSchema == true -> {
                    InvalidSchemaView()
                }
            }

            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InterruptHeader(interrupt: HumanInterrupt) {
    if (interrupt.description.isNullOrEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val parsed = interrupt.description?.let { InterruptDescriptionParser.parse(it) }
        if (parsed != null) {
            ParsedDescriptionView(parsed)
        } else {
            interrupt.description?.takeIf { it.isNotEmpty() }?.let { description ->
                SelectionContainer {
                    Text(text = description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ParsedDescriptionView(parsed: ParsedInterruptDescription) {
    parsed.email?.let { email ->
        ParsedEmailView(email)
    }
    parsed.draft?.let { draft ->
        ParsedDraftView(draft)
    }
}

@Composable
private fun ParsedEmailView(email: ParsedEmailSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Incoming Email", style = MaterialTheme.typography.titleMedium)
        email.subject?.let { LabeledField("Subject", it) }
        email.from?.let { LabeledField("From", it) }
        email.to?.let { LabeledField("To", it) }
        email.id?.let { LabeledField("ID", it) }
        email.body?.takeIf { it.isNotEmpty() }?.let { body ->
            SelectionContainer {
                Text(text = body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ParsedDraftView(draft: ParsedDraftSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Email Draft", style = MaterialTheme.typography.titleMedium)
        draft.replyToMessageId?.let { LabeledField("Reply to", it) }
        draft.from?.let { LabeledField("From", it) }
        draft.body?.takeIf { it.isNotEmpty() }?.let { body ->
            SelectionContainer {
                Text(text = body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterruptActionView(
    interrupt: HumanInterrupt,
    selectedSubmitType: SubmitType,
    onSubmitTypeChange: (SubmitType) -> Unit,
    responseText: String,
    onResponseTextChange: (String) -> Unit,
    editedArgs: Map<String, String>,
    onEditedArgChange: (String, String) -> Unit,
    pendingResponses: List<PendingHumanResponse>,
) {
    val availableTypes = availableSubmitTypes(interrupt, pendingResponses)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (availableTypes.size > 1) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                availableTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = selectedSubmitType == type,
                        onClick = { onSubmitTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = availableTypes.size,
                        ),
                    ) {
                        Text(labelFor(type))
                    }
                }
            }
        }

        when (selectedSubmitType) {
            SubmitType.RESPONSE -> {
                Text("Response to Agent", style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.OutlinedTextField(
                    value = responseText,
                    onValueChange = onResponseTextChange,
                    placeholder = { Text("Type a message to the AI agent…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            }
            SubmitType.EDIT -> {
                Text(
                    text = if (interrupt.config.allowAccept) {
                        "Review and Edit"
                    } else {
                        "Edit arguments"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                editedArgs.keys.sorted().forEach { key ->
                    Column {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = editedArgs[key].orEmpty(),
                            onValueChange = { onEditedArgChange(key, it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 12,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            SubmitType.ACCEPT -> {
                Text(
                    text = "Accept will submit the action with the arguments shown above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun IdleThreadView(valuesText: String?) {
    if (!valuesText.isNullOrEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            Text("Thread State", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = valuesText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No state", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "This idle thread has no stored values yet.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun InvalidSchemaView() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Invalid Schema", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This interrupt does not match the Agent Inbox HumanInterrupt schema. " +
                "Ensure your graph uses interrupt() with action_request and config fields.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SubmitBar(
    label: String,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Button(
            onClick = onSubmit,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun availableSubmitTypes(
    interrupt: HumanInterrupt,
    pendingResponses: List<PendingHumanResponse>,
): List<SubmitType> {
    val types = mutableListOf<SubmitType>()
    if (interrupt.config.allowAccept || pendingResponses.any { it.acceptAllowed }) {
        types.add(SubmitType.ACCEPT)
    }
    if (interrupt.config.allowRespond) types.add(SubmitType.RESPONSE)
    if (interrupt.config.allowEdit) types.add(SubmitType.EDIT)
    return types
}

private fun labelFor(type: SubmitType): String = when (type) {
    SubmitType.ACCEPT -> "Accept"
    SubmitType.RESPONSE -> "Respond 2 Clinty"
    SubmitType.EDIT -> "Edit"
}

private fun submitLabel(type: SubmitType): String = when (type) {
    SubmitType.ACCEPT -> "Accept"
    SubmitType.RESPONSE -> "Send Response"
    SubmitType.EDIT -> "Submit Edit"
}
