package ai.deepcode.android.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    onPersonaClick: (Persona) -> Unit,
    modifier: Modifier = Modifier
) {
    val securePrefs = repository.securePrefs
    val systemPersonas = remember { builtInPersonas }
    var customPersonas by remember { mutableStateOf(loadCustomPersonas(securePrefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Persona?>(null) }
    val allPersonas = remember(systemPersonas, customPersonas) { systemPersonas + customPersonas }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Personas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Persona", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
            }

            if (allPersonas.isEmpty()) {
                item {
                    AppCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No personas yet", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap + to create one", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (systemPersonas.isNotEmpty()) {
                item {
                    Text(
                        text = "System Personas",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                    )
                }
                items(systemPersonas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onClick = { onPersonaClick(persona) },
                        onDelete = null
                    )
                }
            }

            if (customPersonas.isNotEmpty()) {
                item {
                    Text(
                        text = "Custom Personas",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                    )
                }
                items(customPersonas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onClick = { onPersonaClick(persona) },
                        onDelete = { showDeleteConfirm = persona }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPersonaDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, content ->
                val newPersona = Persona(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    content = content,
                    isSystem = false
                )
                customPersonas = customPersonas + newPersona
                saveCustomPersonas(securePrefs, customPersonas)
                showAddDialog = false
            }
        )
    }

    showDeleteConfirm?.let { persona ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Persona", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${persona.name}\"?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    customPersonas = customPersonas.filter { it.id != persona.id }
                    saveCustomPersonas(securePrefs, customPersonas)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = tween(300)
        )
    ) {
        AppCard(onClick = onClick) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (persona.isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (persona.isSystem) Icons.Default.SmartToy else Icons.Default.Person,
                        null,
                        tint = if (persona.isSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (persona.isSystem) {
                        Text(
                            text = "System persona",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddPersonaDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Persona", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Persona Name") },
                    placeholder = { Text("e.g. Pirate, Shakespeare...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Persona Prompt") },
                    placeholder = { Text("e.g. Talk like a pirate, keep responses under 2 sentences...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && content.isNotBlank()) {
                        onConfirm(name.trim(), content.trim())
                    }
                },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text("Create", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun loadCustomPersonas(securePrefs: ai.deepcode.android.data.local.EncryptedPrefs): List<Persona> {
    val raw = securePrefs.getSetting("saved_personas", "[]")
    return try {
        val json = org.json.JSONArray(raw)
        (0 until json.length()).map { i ->
            val obj = json.getJSONObject(i)
            Persona(
                id = obj.getString("id"),
                name = obj.getString("name"),
                content = obj.getString("content"),
                isSystem = obj.optBoolean("isSystem", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveCustomPersonas(securePrefs: ai.deepcode.android.data.local.EncryptedPrefs, personas: List<Persona>) {
    val json = org.json.JSONArray()
    personas.forEach { p ->
        json.put(org.json.JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("content", p.content)
            put("isSystem", p.isSystem)
        })
    }
    securePrefs.saveSetting("saved_personas", json.toString())
}
