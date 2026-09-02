package ai.deepcode.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailPage(
    templateId: String,
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs
    val prefs = context.getSharedPreferences("deepcode_templates", Context.MODE_PRIVATE)

    val resolvedTemplate = remember {
        if (templateId == "persona") {
            PromptTemplate(
                id = "persona",
                title = "Custom Persona",
                content = securePrefs.getSetting("custom_persona", ""),
                category = "Persona",
                isBuiltin = true,
                isPersona = true
            )
        } else {
            BUILTIN_TEMPLATES.find { it.id == templateId }
                ?: loadCustomTemplates(prefs).find { it.id == templateId }
        }
    }

    if (resolvedTemplate == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Template not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(resolvedTemplate.title) }
    var editContent by remember { mutableStateOf(resolvedTemplate.content) }
    var editCategory by remember { mutableStateOf(resolvedTemplate.category) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isEditing) "Edit Template" else resolvedTemplate.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (isEditing) {
                        isEditing = false
                        editTitle = resolvedTemplate.title
                        editContent = resolvedTemplate.content
                        editCategory = resolvedTemplate.category
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (resolvedTemplate.isPersona) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (!resolvedTemplate.isBuiltin && isEditing) {
                    IconButton(onClick = {
                        val updated = listOf(BUILTIN_TEMPLATES, loadCustomTemplates(prefs)).flatMap { it }
                            .find { it.id == resolvedTemplate.id }
                        if (updated != null) {
                            val allCustom = loadCustomTemplates(prefs).toMutableList()
                            val idx = allCustom.indexOfFirst { it.id == resolvedTemplate.id }
                            if (idx >= 0) {
                                allCustom[idx] = allCustom[idx].copy(
                                    title = editTitle.trim(),
                                    content = editContent.trim(),
                                    category = editCategory.trim()
                                )
                                saveCustomTemplates(prefs, allCustom)
                            }
                        }
                        isEditing = false
                        Toast.makeText(context, "Template saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (!resolvedTemplate.isBuiltin && !isEditing) {
                    IconButton(onClick = {
                        editTitle = resolvedTemplate.title
                        editContent = resolvedTemplate.content
                        editCategory = resolvedTemplate.category
                        isEditing = true
                    }) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
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

            if (resolvedTemplate.isPersona) {
                PersonaTemplateView(
                    content = resolvedTemplate.content,
                    securePrefs = securePrefs,
                    onBack = onBack
                )
            } else {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isEditing && !resolvedTemplate.isBuiltin) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Title") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            val categories = listOf("General", "Development", "Writing", "Learning", "Language")
                            var categoryExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = editCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                editCategory = cat
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = editContent,
                                onValueChange = { editContent = it },
                                label = { Text("Content") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                maxLines = 10,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        } else {
                            LabelText(text = "Category")
                            Text(
                                text = resolvedTemplate.category,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)

                            LabelText(text = "Content")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = resolvedTemplate.content,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (!isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedAppButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Template", resolvedTemplate.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            icon = Icons.Default.ContentCopy,
                            text = "Copy",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (!resolvedTemplate.isBuiltin) {
                            OutlinedAppButton(
                                onClick = {
                                    val existing = loadCustomTemplates(prefs).find { it.id == resolvedTemplate.id }
                                    if (existing != null && existing.content.isNotBlank()) {
                                        securePrefs.saveSetting("custom_persona", existing.content)
                                        securePrefs.saveSetting("persona_enabled", "true")
                                        Toast.makeText(context, "Saved as active persona", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                icon = Icons.Default.Face,
                                text = "Use as Persona",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelText(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PersonaTemplateView(
    content: String,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    onBack: () -> Unit
) {
    var personaText by remember { mutableStateOf(content) }
    val personaEnabled = securePrefs.getSetting("persona_enabled", "false") == "true"

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Custom Persona", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            SettingsRow(
                icon = Icons.Default.Portrait,
                label = "Enable Custom Persona",
                control = {
                    AppToggle(
                        checked = personaEnabled,
                        onCheckedChange = {
                            securePrefs.saveSetting("persona_enabled", it.toString())
                        }
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

            OutlinedTextField(
                value = personaText,
                onValueChange = {
                    personaText = it
                    securePrefs.saveSetting("custom_persona", it)
                },
                label = { Text("Persona Prompt", color = MaterialTheme.colorScheme.primary) },
                placeholder = { Text("e.g. Talk like a pirate, keep responses under 2 sentences...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
