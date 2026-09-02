package ai.deepcode.android.ui.settings

data class Persona(
    val id: String,
    val name: String,
    val content: String,
    val isSystem: Boolean = false
)
