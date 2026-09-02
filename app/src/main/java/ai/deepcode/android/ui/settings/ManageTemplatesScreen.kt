package ai.deepcode.android.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import java.util.UUID

val BUILTIN_TEMPLATES = listOf(
    PromptTemplate("builtin_1", "Code Review",
        "Please review the following code and provide feedback on:\n1. Code quality and best practices\n2. Potential bugs or issues\n3. Performance improvements\n4. Security concerns\n\n```\n{code}\n```",
        "Development", isBuiltin = true),
    PromptTemplate("builtin_2", "Summarize Text",
        "Please summarize the following text in a clear and concise manner, highlighting the key points:\n\n{text}",
        "Writing", isBuiltin = true),
    PromptTemplate("builtin_3", "Fix Bug",
        "I have the following bug in my code:\n\nError message: {error}\n\nCode:\n```\n{code}\n```\n\nPlease help me identify and fix the issue.",
        "Development", isBuiltin = true),
    PromptTemplate("builtin_4", "Explain Concept",
        "Please explain {concept} in simple terms, using examples where helpful. Assume I am a beginner with no prior knowledge of this topic.",
        "Learning", isBuiltin = true),
    PromptTemplate("builtin_5", "Write Email",
        "Please write a professional email with the following details:\n\nTo: {recipient}\nSubject: {subject}\nKey points to cover: {points}\n\nTone: Professional and concise",
        "Writing", isBuiltin = true),
    PromptTemplate("builtin_6", "Translate Text",
        "Please translate the following text to {language}:\n\n{text}\n\nProvide a natural, accurate translation that preserves the original meaning and tone.",
        "Language", isBuiltin = true),
    PromptTemplate("builtin_7", "Generate Unit Tests",
        "Please generate comprehensive unit tests for the following code:\n\n```{language}\n{code}\n```\n\nInclude:\n- Happy path tests\n- Edge cases\n- Error handling tests",
        "Development", isBuiltin = true),
    PromptTemplate("builtin_8", "Refactor Code",
        "Please refactor the following code to improve readability, maintainability, and follow best practices:\n\n```\n{code}\n```\n\nExplain the changes you made and why.",
        "Development", isBuiltin = true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTemplatesScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("deepcode_templates", Context.MODE_PRIVATE)
    var customTemplates by remember { mutableStateOf(loadCustomTemplates(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingTemplate by remember { mutableStateOf<PromptTemplate?>(null) }

    val allTemplates = remember(customTemplates) {
        BUILTIN_TEMPLATES + customTemplates
    }

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
                    Text("Prompt Templates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Template", tint = MaterialTheme.colorScheme.primary)
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Use templates to quickly insert common prompts. ${allTemplates.size} templates available.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            val grouped = allTemplates.groupBy { it.category }
            grouped.forEach { (category, templates) ->
                item {
                    TemplateSectionLabel(text = category)
                }

                items(templates, key = { it.id }) { template ->
                    val isBuiltin = template.isBuiltin
                    val previewContent = template.content.take(80).replace('\n', ' ') + if (template.content.length > 80) "..." else ""

                    TemplateListRow(
                        template = template,
                        previewContent = previewContent,
                        showDelete = !isBuiltin,
                        onDelete = { deletingTemplate = template },
                        onClick = { onTemplateClick(template.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TemplateEditDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, content, category ->
                val newTemplate = PromptTemplate(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    category = category,
                    isBuiltin = false,
                    isPersona = false
                )
                customTemplates = customTemplates + newTemplate
                saveCustomTemplates(prefs, customTemplates)
                showAddDialog = false
            }
        )
    }

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text("Delete Template", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"${template.title}\"? This cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    customTemplates = customTemplates.filter { it.id != template.id }
                    saveCustomTemplates(prefs, customTemplates)
                    deletingTemplate = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun TemplateListRow(
    template: PromptTemplate,
    previewContent: String,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (template.category) {
                            "Development" -> Icons.Default.Code
                            "Writing" -> Icons.Default.Edit
                            "Learning" -> Icons.Default.School
                            "Language" -> Icons.Default.Language
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = previewContent,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (template.isBuiltin) {
                    Text(
                        text = "Built-in",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (showDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TemplateSectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditDialog(
    existing: PromptTemplate?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Template" else "New Template", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Template Title") },
                    placeholder = { Text("e.g. Code Review") },
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
                    label = { Text("Template Content") },
                    placeholder = { Text("Use {placeholders} for variables...") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 8,
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
                        value = category,
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
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title.trim(), content.trim(), category)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text(if (existing != null) "Save" else "Create", color = MaterialTheme.colorScheme.primary)
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

fun loadCustomTemplates(prefs: SharedPreferences): List<PromptTemplate> {
    val count = prefs.getInt("template_count", 0)
    val result = mutableListOf<PromptTemplate>()
    for (i in 0 until count) {
        val id = prefs.getString("template_${i}_id", null) ?: continue
        val title = prefs.getString("template_${i}_title", null) ?: continue
        val content = prefs.getString("template_${i}_content", null) ?: continue
        val category = prefs.getString("template_${i}_category", "General") ?: "General"
        result.add(PromptTemplate(id, title, content, category, isBuiltin = false, isPersona = false))
    }
    return result
}

fun saveCustomTemplates(prefs: SharedPreferences, templates: List<PromptTemplate>) {
    val editor = prefs.edit()
    editor.putInt("template_count", templates.size)
    templates.forEachIndexed { i, template ->
        editor.putString("template_${i}_id", template.id)
        editor.putString("template_${i}_title", template.title)
        editor.putString("template_${i}_content", template.content)
        editor.putString("template_${i}_category", template.category)
    }
    editor.apply()
}
