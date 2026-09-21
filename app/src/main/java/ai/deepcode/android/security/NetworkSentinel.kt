package ai.deepcode.android.security

import ai.deepcode.android.util.AppLogger
import java.net.InetAddress
import java.net.URI

/**
 * Network Sentinel & Anti-SSRF Defense Filter inspired by Meta Muse's Sentinel proxy.
 * Prevents Server-Side Request Forgery (SSRF) and data exfiltration through untrusted web responses.
 */
object NetworkSentinel {
    private const val TAG = "NetworkSentinel"

    // Cloud metadata and loopback hostnames
    private val BLOCKED_HOSTS = setOf(
        "localhost",
        "metadata.google.internal",
        "169.254.169.254", // AWS / GCP / Azure metadata
        "instance-data"
    )

    // Regex to strip markdown image exfiltration payloads like ![leak](https://c2.com/?k=...)
    private val RE_MARKDOWN_EXFIL_IMG = Regex("""!\[.*?\]\((https?://[^\s)]+)\)""")

    // Regex to strip zero-font or hidden CSS text injection
    private val RE_ZERO_FONT_HIDDEN = Regex("""<[^>]*style\s*=\s*["'][^"']*(font-size\s*:\s*0|display\s*:\s*none|visibility\s*:\s*hidden|opacity\s*:\s*0)[^"']*["'][^>]*>.*?</[^>]+>""", RegexOption.IGNORE_CASE)

    /**
     * Validates whether a destination URL is safe to fetch or if it represents an SSRF attack.
     * Blocks cloud metadata endpoints and loopback addresses.
     * Allows LAN addresses (192.168.x.x, 10.x.x.x) if allowLan=true (for local dev servers).
     */
    fun validateUrl(urlString: String, allowLan: Boolean = true): Boolean {
        return try {
            val uri = URI(urlString.trim())
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false

            val host = uri.host?.lowercase() ?: return false
            if (BLOCKED_HOSTS.contains(host)) {
                AppLogger.w(TAG, "Blocked SSRF target host: $host")
                return false
            }

            // Resolve IP and check for cloud metadata / loopback
            val address = InetAddress.getByName(host)
            if (address.isLoopbackAddress || address.isAnyLocalAddress) {
                AppLogger.w(TAG, "Blocked loopback target IP address: ${address.hostAddress} for host $host")
                return false
            }

            val ip = address.hostAddress ?: return false
            if (ip.startsWith("169.254.") || host == "169.254.169.254") {
                AppLogger.w(TAG, "Blocked cloud metadata IP: $ip")
                return false
            }

            if (!allowLan && (address.isSiteLocalAddress || address.isLinkLocalAddress ||
                ip.startsWith("10.") || ip.startsWith("192.168.") ||
                (ip.startsWith("172.") && (ip.split(".").getOrNull(1)?.toIntOrNull() in 16..31)))) {
                AppLogger.w(TAG, "Blocked private network IP: $ip")
                return false
            }

            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "URL validation failed for '$urlString': ${e.message}")
            false
        }
    }

    /**
     * Sanitizes untrusted web scrape output:
     * Removes invisible zero-font CSS text, hidden DOM nodes, and suspicious image exfiltration tags.
     */
    fun sanitizeWebOutput(rawHtmlOrText: String): String {
        if (rawHtmlOrText.isEmpty()) return rawHtmlOrText
        var cleaned = RE_ZERO_FONT_HIDDEN.replace(rawHtmlOrText, "")
        cleaned = RE_MARKDOWN_EXFIL_IMG.replace(cleaned) { match ->
            val url = match.groupValues[1]
            // If URL is suspicious with long query parameters, neutralize it
            if (url.contains("?") && url.length > 80) {
                "[Image removed by NetworkSentinel security filter]"
            } else {
                match.value
            }
        }
        return cleaned
    }
}
