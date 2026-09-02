package ai.deepcode.android.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppState : ViewModel() {
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _showLogViewer = MutableStateFlow(false)
    val showLogViewer = _showLogViewer.asStateFlow()

    private val _selectedFilePath = MutableStateFlow("")
    val selectedFilePath = _selectedFilePath.asStateFlow()

    private val _showFileExplorer = MutableStateFlow(false)
    val showFileExplorer = _showFileExplorer.asStateFlow()

    private val _showAgents = MutableStateFlow(false)
    val showAgents = _showAgents.asStateFlow()

    private val _selectedAgentId = MutableStateFlow("")
    val selectedAgentId = _selectedAgentId.asStateFlow()

    private val _showLogin = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    private val _showTokenUsage = MutableStateFlow(false)
    val showTokenUsage = _showTokenUsage.asStateFlow()

    private val _profileRefreshKey = MutableStateFlow(0)
    val profileRefreshKey = _profileRefreshKey.asStateFlow()

    private val _showPersonas = MutableStateFlow(false)
    val showPersonas = _showPersonas.asStateFlow()

    private val _selectedPersona = MutableStateFlow<ai.deepcode.android.ui.settings.Persona?>(null)
    val selectedPersona = _selectedPersona.asStateFlow()

    private val _showManageTemplates = MutableStateFlow(false)
    val showManageTemplates = _showManageTemplates.asStateFlow()

    private val _selectedTemplateId = MutableStateFlow("")
    val selectedTemplateId = _selectedTemplateId.asStateFlow()

    private val _showVpnSettings = MutableStateFlow(false)
    val showVpnSettings = _showVpnSettings.asStateFlow()

    private val _showApiKeys = MutableStateFlow(false)
    val showApiKeys = _showApiKeys.asStateFlow()

    private val _showCloudflare = MutableStateFlow(false)
    val showCloudflare = _showCloudflare.asStateFlow()

    private val _showPlugins = MutableStateFlow(false)
    val showPlugins = _showPlugins.asStateFlow()

    private val _showThemesAndWallpapers = MutableStateFlow(false)
    val showThemesAndWallpapers = _showThemesAndWallpapers.asStateFlow()

    fun setShowThemesAndWallpapers(show: Boolean) {
        _showThemesAndWallpapers.value = show
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _showThemesAndWallpapers.value = false
        _showPlugins.value = false
        _showVpnSettings.value = false
        _showApiKeys.value = false
        _showCloudflare.value = false
        _showPersonas.value = false
        _selectedPersona.value = null
        _showManageTemplates.value = false
        _selectedTemplateId.value = ""
        _showLogViewer.value = false
        _showAgents.value = false
        _selectedAgentId.value = ""
        _showTokenUsage.value = false
        _selectedFilePath.value = ""
        _showFileExplorer.value = false
    }

    fun setShowLogViewer(show: Boolean) {
        _showLogViewer.value = show
    }

    fun setSelectedFilePath(path: String) {
        _selectedFilePath.value = path
    }

    fun setShowFileExplorer(show: Boolean) {
        _showFileExplorer.value = show
    }

    fun setShowAgents(show: Boolean) {
        _showAgents.value = show
    }

    fun setSelectedAgentId(agentId: String) {
        _selectedAgentId.value = agentId
    }

    fun setShowTokenUsage(show: Boolean) {
        _showTokenUsage.value = show
    }

    fun setShowLogin(show: Boolean) {
        _showLogin.value = show
    }

    fun setShowPersonas(show: Boolean) {
        _showPersonas.value = show
    }

    fun setSelectedPersona(persona: ai.deepcode.android.ui.settings.Persona?) {
        _selectedPersona.value = persona
    }

    fun setShowManageTemplates(show: Boolean) {
        _showManageTemplates.value = show
    }

    fun setSelectedTemplateId(id: String) {
        _selectedTemplateId.value = id
    }

    fun setShowVpnSettings(show: Boolean) {
        _showVpnSettings.value = show
    }

    fun setShowApiKeys(show: Boolean) {
        _showApiKeys.value = show
    }

    fun setShowCloudflare(show: Boolean) {
        _showCloudflare.value = show
    }

    fun setShowPlugins(show: Boolean) {
        _showPlugins.value = show
    }

    fun refreshProfiles() {
        _profileRefreshKey.value++
    }
}
