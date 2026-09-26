package ai.deepcode.android.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val context = LocalContext.current
    val securePrefs = repository.securePrefs
    val personaEnabled by securePrefs.personaEnabledFlow.collectAsStateWithLifecycle()
    val activeCustomPersona by securePrefs.customPersonaFlow.collectAsStateWithLifecycle()
    val isDefaultActive = !personaEnabled || activeCustomPersona.isEmpty()

    val systemPersonas = remember { builtInPersonas }
    var customPersonas by remember { mutableStateOf(loadCustomPersonas(securePrefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Persona?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            windowInsets = WindowInsets(0.dp),
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
            }

            // Default Assistant Option (cleanly disables custom persona)
            item {
                Text(
                    text = "System Default",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
                )
            }
            item {
                DefaultPersonaCard(
                    isActive = isDefaultActive,
                    onActivate = {
                        securePrefs.saveSetting("persona_enabled", "false")
                        securePrefs.saveSetting("custom_persona", "")
                        Toast.makeText(context, "Default AI Assistant activated", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (systemPersonas.isNotEmpty()) {
                item {
                    Text(
                        text = "Prebuilt Personas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(systemPersonas, key = { it.id }) { persona ->
                    val isActive = personaEnabled && activeCustomPersona == persona.content
                    PersonaCard(
                        persona = persona,
                        isActive = isActive,
                        onClick = { onPersonaClick(persona) },
                        onActivate = {
                            securePrefs.saveSetting("custom_persona", persona.content)
                            securePrefs.saveSetting("persona_enabled", "true")
                            Toast.makeText(context, "\"${persona.name}\" activated", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = null
                    )
                }
            }

            if (customPersonas.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Custom Personas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(customPersonas, key = { it.id }) { persona ->
                    val isActive = personaEnabled && activeCustomPersona == persona.content
                    PersonaCard(
                        persona = persona,
                        isActive = isActive,
                        onClick = { onPersonaClick(persona) },
                        onActivate = {
                            securePrefs.saveSetting("custom_persona", persona.content)
                            securePrefs.saveSetting("persona_enabled", "true")
                            Toast.makeText(context, "\"${persona.name}\" activated", Toast.LENGTH_SHORT).show()
                        },
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
                    val wasActive = personaEnabled && activeCustomPersona == persona.content
                    customPersonas = customPersonas.filter { it.id != persona.id }
                    saveCustomPersonas(securePrefs, customPersonas)
                    if (wasActive) {
                        securePrefs.saveSetting("persona_enabled", "false")
                        securePrefs.saveSetting("custom_persona", "")
                    }
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
private fun DefaultPersonaCard(
    isActive: Boolean,
    onActivate: () -> Unit
) {
    AppCard(onClick = onActivate) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Default AI Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Standard DeepCode AI without persona tone modification",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = onActivate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    isActive: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (persona.isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (persona.isSystem) Icons.Default.SmartToy else Icons.Default.Face,
                    null,
                    tint = if (persona.isSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = persona.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (persona.isSystem) "Prebuilt system persona" else "Custom persona",
                    fontSize = 11.sp,
                    color = if (persona.isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = onActivate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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
