package com.clinty.client.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clinty.client.models.AgentInbox
import com.clinty.client.viewmodels.InboxSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: InboxSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val clintAPIKey by viewModel.clintAPIKey.collectAsState()
    val langsmithAPIKey by viewModel.langsmithAPIKey.collectAsState()
    val inboxes by viewModel.inboxes.collectAsState()
    val newGraphId by viewModel.newGraphId.collectAsState()
    val newDeploymentURL by viewModel.newDeploymentURL.collectAsState()
    val newInboxName by viewModel.newInboxName.collectAsState()
    val editingInboxId by viewModel.editingInboxId.collectAsState()
    val editGraphId by viewModel.editGraphId.collectAsState()
    val editDeploymentURL by viewModel.editDeploymentURL.collectAsState()
    val editInboxName by viewModel.editInboxName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Authentication", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ApiKeyTextField(
                value = clintAPIKey,
                onValueChange = viewModel::updateClintAPIKey,
                label = "Clint API Key",
            )
            Spacer(Modifier.height(8.dp))
            ApiKeyTextField(
                value = langsmithAPIKey,
                onValueChange = viewModel::updateLangsmithAPIKey,
                label = "LangSmith API Key",
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::saveAPIKeys) {
                Text("Save API Keys")
            }
            Text(
                text = "Clint API Key is sent as X-Api-Key for clinty.net. LangSmith key is used for LangGraph Cloud when Clint key is not set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Inboxes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            inboxes.forEach { inbox ->
                InboxSettingsRow(
                    inbox = inbox,
                    isEditing = editingInboxId == inbox.id,
                    onSelect = { viewModel.selectInbox(inbox) },
                    onEdit = { viewModel.startEditingInbox(inbox) },
                    onDelete = { viewModel.deleteInbox(inbox) },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (editingInboxId != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Edit Inbox", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editGraphId,
                    onValueChange = viewModel::updateEditGraphId,
                    label = { Text("Assistant / Graph ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDeploymentURL,
                    onValueChange = viewModel::updateEditDeploymentURL,
                    label = { Text("Deployment URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editInboxName,
                    onValueChange = viewModel::updateEditInboxName,
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::cancelEditingInbox,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = viewModel::saveEditedInbox,
                        enabled = editGraphId.trim().isNotEmpty() && editDeploymentURL.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save Changes")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Add Inbox", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newGraphId,
                onValueChange = viewModel::updateNewGraphId,
                label = { Text("Assistant / Graph ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newDeploymentURL,
                onValueChange = viewModel::updateNewDeploymentURL,
                label = { Text("Deployment URL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newInboxName,
                onValueChange = viewModel::updateNewInboxName,
                label = { Text("Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.addInbox(onSuccess = onDismiss) },
                enabled = newGraphId.trim().isNotEmpty() && newDeploymentURL.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Inbox")
            }
            Text(
                text = "Graph ID from langgraph.json. Base URL only: https://clinty.net (no www, no /threads/search).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )

            errorMessage?.let { error ->
                Spacer(Modifier.height(16.dp))
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
private fun ApiKeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var showPlainText by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (showPlainText) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { showPlainText = !showPlainText }) {
                Icon(
                    imageVector = if (showPlainText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPlainText) "Hide API key" else "Show API key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun InboxSettingsRow(
    inbox: AgentInbox,
    isEditing: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = inbox.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = inbox.deploymentUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
            Text(
                text = inbox.graphId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
            )
        }
        if (inbox.selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
