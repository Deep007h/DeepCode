package ai.deepcode.android.ui.settings

data class PromptTemplate(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "General",
    val isBuiltin: Boolean = false,
    val isPersona: Boolean = false
)
