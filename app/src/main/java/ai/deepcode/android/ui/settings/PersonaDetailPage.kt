package ai.deepcode.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaDetailPage(
    persona: Persona,
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    onUpdated: ((Persona) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(persona.name) }
    var editContent by remember { mutableStateOf(persona.content) }

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
                    Text(
                        text = if (isEditing) "Edit Persona" else persona.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (!persona.isSystem) {
                    IconButton(onClick = {
                        if (isEditing) {
                            val updated = persona.copy(
                                name = editName.trim(),
                                content = editContent.trim()
                            )
                            onUpdated?.invoke(updated)
                            isEditing = false
                            Toast.makeText(context, "Persona updated", Toast.LENGTH_SHORT).show()
                        } else {
                            editName = persona.name
                            editContent = persona.content
                            isEditing = true
                        }
                    }) {
                        Icon(
                            if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            if (isEditing) "Save" else "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            val activePersona = securePrefs.getSetting("custom_persona", "")
            val personaEnabled = securePrefs.getSetting("persona_enabled", "false") == "true"
            val isActive = personaEnabled && activePersona == persona.content

            if (isActive) {
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Active Persona",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    securePrefs.saveSetting("custom_persona", persona.content)
                    securePrefs.saveSetting("persona_enabled", "true")
                    Toast.makeText(context, "\"${persona.name}\" applied as active persona", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary
                ),
                enabled = !isActive
            ) {
                Icon(
                    if (isActive) Icons.Default.CheckCircle else Icons.Default.Check,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isActive) "Already Active" else "Apply Persona",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (persona.isSystem) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("System Persona", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                    }

                    if (isEditing && !persona.isSystem) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Persona Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Text(
                            text = "Name",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = persona.name,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)

                    Text(
                        text = "Prompt",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isEditing && !persona.isSystem) {
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            label = { Text("Persona Prompt") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = persona.content,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            if (persona.isSystem) {
                AppCard {
                    Text(
                        text = "System personas cannot be edited. To customize your AI experience, create a new custom persona instead.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
