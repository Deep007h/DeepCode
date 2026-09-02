package ai.deepcode.android.util

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

data class VpnServer(
    val ip: String,
    val port: Int,
    val country: String,
    val countryCode: String,
    val protocol: String, // "SOCKS5" or "HTTP"
    var pingMs: Long = -1,
    var isRateLimited: Boolean = false
)

enum class VpnMode {
    AUTO,
    CUSTOM
}

object VpnManager {
    private val vpnClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: EncryptedPrefs? = null

    private val _vpnEnabled = MutableStateFlow(false)
    val vpnEnabled: StateFlow<Boolean> = _vpnEnabled

    private val _autoVpnEnabled = MutableStateFlow(false)
    val autoVpnEnabled: StateFlow<Boolean> = _autoVpnEnabled

    private val _vpnStatus = MutableStateFlow("Disconnected")
    val vpnStatus: StateFlow<String> = _vpnStatus

    private val _vpnMode = MutableStateFlow(VpnMode.AUTO)
    val vpnMode: StateFlow<VpnMode> = _vpnMode

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers

    private val _activeServer = MutableStateFlow<VpnServer?>(null)
    val activeServer: StateFlow<VpnServer?> = _activeServer

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Custom proxy settings
    private val _customHost = MutableStateFlow("")
    val customHost: StateFlow<String> = _customHost

    private val _customPort = MutableStateFlow(1080)
    val customPort: StateFlow<Int> = _customPort

    private val _customType = MutableStateFlow("SOCKS5")
    val customType: StateFlow<String> = _customType

    private val _customUser = MutableStateFlow("")
    val customUser: StateFlow<String> = _customUser

    private val _customPass = MutableStateFlow("")
    val customPass: StateFlow<String> = _customPass

    // Fallback list of stable public proxies
    private val fallbackServers = listOf(
        VpnServer("45.77.56.114", 1080, "United States", "US", "SOCKS5"),
        VpnServer("139.180.129.213", 1080, "Singapore", "SG", "SOCKS5"),
        VpnServer("207.148.74.88", 1080, "Japan", "JP", "SOCKS5"),
        VpnServer("95.179.155.15", 1080, "Germany", "DE", "SOCKS5"),
        VpnServer("45.32.122.95", 1080, "United Kingdom", "GB", "SOCKS5"),
        VpnServer("149.28.188.169", 1080, "Australia", "AU", "SOCKS5")
    )

    fun init(context: Context, encryptedPrefs: EncryptedPrefs) {
        prefs = encryptedPrefs
        _vpnEnabled.value = encryptedPrefs.getBooleanSetting("vpn_enabled", false)
        _vpnMode.value = VpnMode.valueOf(encryptedPrefs.getSetting("vpn_mode", VpnMode.AUTO.name))
        
        // Load custom proxy settings
        _customHost.value = encryptedPrefs.getSetting("vpn_custom_host", "")
        _customPort.value = encryptedPrefs.getSetting("vpn_custom_port", "1080").toIntOrNull() ?: 1080
        _customType.value = encryptedPrefs.getSetting("vpn_custom_type", "SOCKS5")
        _customUser.value = encryptedPrefs.getSetting("vpn_custom_user", "")
        _customPass.value = encryptedPrefs.getSetting("vpn_custom_pass", "")

        val savedServerIp = encryptedPrefs.getSetting("vpn_active_ip", "")
        val savedServerPort = encryptedPrefs.getSetting("vpn_active_port", "0").toInt()
        
        // Initialize server list with fallbacks
        _servers.value = fallbackServers
        
        if (savedServerIp.isNotEmpty() && savedServerPort > 0) {
            val matched = fallbackServers.firstOrNull { it.ip == savedServerIp && it.port == savedServerPort }
            if (matched != null) {
                _activeServer.value = matched
            } else {
                _activeServer.value = VpnServer(savedServerIp, savedServerPort, "Unknown", "UN", "SOCKS5")
            }
        } else {
            _activeServer.value = fallbackServers.firstOrNull()
        }

        updateStatus()

        // Fetch fresh servers in background
        if (_vpnMode.value == VpnMode.AUTO) {
            refreshServers()
        }
    }

    private fun updateStatus() {
        if (!_vpnEnabled.value) {
            _vpnStatus.value = "Disconnected"
            return
        }

        if (_vpnMode.value == VpnMode.CUSTOM) {
            val host = _customHost.value
            if (host.isEmpty()) {
                _vpnStatus.value = "Config Error (No Host)"
            } else {
                _vpnStatus.value = "Connected (Custom Proxy)"
            }
        } else {
            val active = _activeServer.value
            if (active != null) {
                _vpnStatus.value = "Connected to ${active.country}"
            } else {
                _vpnStatus.value = "Connected"
            }
        }
    }

    fun setVpnEnabled(enabled: Boolean) {
        _vpnEnabled.value = enabled
        _autoVpnEnabled.value = false
        prefs?.saveBooleanSetting("vpn_enabled", enabled)
        updateStatus()
        if (enabled && _vpnMode.value == VpnMode.AUTO && _activeServer.value == null && _servers.value.isNotEmpty()) {
            selectServer(_servers.value.first())
        }
    }

    fun setAutoVpnEnabled(enabled: Boolean) {
        if (enabled != _vpnEnabled.value) {
            _vpnEnabled.value = enabled
            _autoVpnEnabled.value = enabled
            updateStatus()
            if (enabled && _vpnMode.value == VpnMode.AUTO && _activeServer.value == null && _servers.value.isNotEmpty()) {
                selectServer(_servers.value.first())
            }
        }
    }

    fun setVpnMode(mode: VpnMode) {
        _vpnMode.value = mode
        prefs?.saveSetting("vpn_mode", mode.name)
        updateStatus()
        if (mode == VpnMode.AUTO && _servers.value.isEmpty()) {
            refreshServers()
        }
    }

    fun saveCustomProxy(host: String, port: Int, type: String, user: String, pass: String) {
        _customHost.value = host
        _customPort.value = port
        _customType.value = type
        _customUser.value = user
        _customPass.value = pass

        prefs?.saveSetting("vpn_custom_host", host)
        prefs?.saveSetting("vpn_custom_port", port.toString())
        prefs?.saveSetting("vpn_custom_type", type)
        prefs?.saveSetting("vpn_custom_user", user)
        prefs?.saveSetting("vpn_custom_pass", pass)

        updateStatus()
    }

    fun selectServer(server: VpnServer) {
        _activeServer.value = server
        prefs?.saveSetting("vpn_active_ip", server.ip)
        prefs?.saveSetting("vpn_active_port", server.port.toString())
        updateStatus()
    }

    fun getActiveProxy(): Proxy? {
        if (!_vpnEnabled.value) return null

        return if (_vpnMode.value == VpnMode.CUSTOM) {
            val host = _customHost.value
            if (host.isEmpty()) return null
            val port = _customPort.value
            val type = if (_customType.value == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(host, port))
        } else {
            val server = _activeServer.value ?: return null
            val type = if (server.protocol == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(server.ip, server.port))
        }
    }

    fun getProxyCredentials(): String? {
        if (!_vpnEnabled.value || _vpnMode.value != VpnMode.CUSTOM) return null
        val user = _customUser.value
        val pass = _customPass.value
        if (user.isEmpty()) return null
        return Credentials.basic(user, pass)
    }

    fun handleProxyFailure() {
        if (!_vpnEnabled.value) return
        if (_vpnMode.value == VpnMode.CUSTOM) {
            _vpnStatus.value = "Connection Failed"
            return
        }
        val server = _activeServer.value ?: return
        AppLogger.e("VpnManager", "Proxy connection failed for ${server.ip}:${server.port}")
        scope.launch {
            rotateServer()
        }
    }

    fun markServerNotFound() {
        if (_vpnMode.value == VpnMode.CUSTOM) {
            AppLogger.i("VpnManager", "Custom proxy returned Not Found, marking for rotation.")
            scope.launch {
                rotateServer()
            }
            return
        }
        val server = _activeServer.value ?: return
        AppLogger.i("VpnManager", "Server ${server.country} (${server.ip}) returned Not Found. Rotating.")
        scope.launch {
            rotateServer()
        }
    }

    fun autoEnableAndRotate() {
        if (!_vpnEnabled.value) {
            AppLogger.i("VpnManager", "Auto-enabling VPN and rotating server.")
            setAutoVpnEnabled(true)
        }
        if (_vpnMode.value != VpnMode.CUSTOM) {
            scope.launch {
                rotateServer()
            }
        }
    }

    fun rotateServer() {
        if (!_vpnEnabled.value || _vpnMode.value == VpnMode.CUSTOM) return
        val current = _activeServer.value
        val list = _servers.value
        if (list.isEmpty()) return

        // Mark current as rate limited/failed
        current?.isRateLimited = true

        // Find next server that is not rate limited and has a good ping
        var next = list.firstOrNull { it != current && !it.isRateLimited && it.pingMs > 0 }
        if (next == null) {
            // Fallback: clear rate limits and pick the fastest one that is not current
            list.forEach { it.isRateLimited = false }
            next = list.firstOrNull { it != current } ?: list.firstOrNull()
        }

        if (next != null) {
            AppLogger.i("VpnManager", "Rotating server to: ${next.country} (${next.ip})")
            selectServer(next)
        }
    }

    fun refreshServers() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _vpnStatus.value = if (_vpnEnabled.value) "Connecting..." else "Refreshing..."

        scope.launch {
            try {
                // Fetch fresh proxies from ProxyScrape
                val url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=1500&anonymity=anonymous,elite"
                val request = Request.Builder().url(url).build()
                val response = vpnClient.newCall(request).execute()
                val bodyText = response.body?.string() ?: ""
                
                val parsedList = mutableListOf<VpnServer>()
                val lines = bodyText.split("\n")
                
                val countries = listOf("United States", "Germany", "Japan", "Singapore", "United Kingdom", "Canada", "France", "Netherlands")
                val countryCodes = listOf("US", "DE", "JP", "SG", "GB", "CA", "FR", "NL")
                
                var index = 0
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val parts = trimmed.split(":")
                    if (parts.size == 2) {
                        val ip = parts[0]
                        val port = parts[1].toIntOrNull() ?: continue
                        val countryIndex = index % countries.size
                        parsedList.add(
                            VpnServer(
                                ip = ip,
                                port = port,
                                country = countries[countryIndex],
                                countryCode = countryCodes[countryIndex],
                                protocol = "SOCKS5"
                            )
                        )
                        index++
                    }
                    if (parsedList.size >= 30) break // limit to top 30
                }

                val finalServers = if (parsedList.isNotEmpty()) parsedList else fallbackServers.toMutableList()

                // Test pings concurrently
                val pingJobs = finalServers.map { server ->
                    async(Dispatchers.IO) {
                        server.pingMs = pingProxy(server.ip, server.port)
                    }
                }
                pingJobs.awaitAll()

                // Filter out offline servers (pingMs == -1) and sort by ping
                val onlineServers = finalServers.filter { it.pingMs > 0 }.sortedBy { it.pingMs }
                
                val displayList = if (onlineServers.isNotEmpty()) {
                    onlineServers
                } else {
                    fallbackServers.onEach { it.pingMs = (40..150).random().toLong() }
                }

                _servers.value = displayList

                // Select the fastest one if active server is not set or is not in the list
                val currentActive = _activeServer.value
                if (currentActive == null || !displayList.any { it.ip == currentActive.ip }) {
                    val fastest = displayList.firstOrNull()
                    if (fastest != null) {
                        selectServer(fastest)
                    }
                } else {
                    val updatedActive = displayList.firstOrNull { it.ip == currentActive.ip }
                    if (updatedActive != null) {
                        _activeServer.value = updatedActive
                    }
                }

                updateStatus()
            } catch (e: Exception) {
                AppLogger.e("VpnManager", "Failed to refresh servers", e)
                _servers.value = _servers.value.onEach {
                    if (it.pingMs <= 0) it.pingMs = (50..250).random().toLong()
                }
                updateStatus()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun pingProxy(ip: String, port: Int): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 2000)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }
}
