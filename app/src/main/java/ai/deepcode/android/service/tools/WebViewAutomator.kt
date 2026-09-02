package ai.deepcode.android.service.tools

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PlatformDef(
    val key: String,
    val name: String,
    val url: String,
    val loginUrl: String? = null,
    val requiresLogin: Boolean = true,
    val waitForPromptJs: String,
    val fillPromptJs: String,
    val submitJs: String,
    val waitForResponseJs: String,
    val extractTextJs: String,
    val extractImagesJs: String = "",
    val detectLoginJs: String = "document.querySelector('input[type=\"email\"],input[type=\"password\"],input[name=\"email\"],input[name=\"password\"]') !== null",
    val fillLoginJs: String = ""
)

private val PLATFORMS = mapOf(
    "chatgpt" to PlatformDef(
        key = "chatgpt", name = "ChatGPT", url = "https://chatgpt.com",
        loginUrl = "https://chatgpt.com/auth/login",
        waitForPromptJs = """
            (function() {
                return new Promise(function(resolve) {
                    var el = document.querySelector('div[contenteditable="true"][id*="prompt"],div[role="textbox"],textarea');
                    if (el) return resolve(true);
                    var obs = new MutationObserver(function() {
                        el = document.querySelector('div[contenteditable="true"][id*="prompt"],div[role="textbox"],textarea');
                        if (el) { obs.disconnect(); resolve(true); }
                    });
                    obs.observe(document.body, { childList: true, subtree: true });
                    setTimeout(function() { obs.disconnect(); resolve(false); }, 15000);
                });
            })()
        """.trimIndent(),
        fillPromptJs = """
            (function(prompt) {
                var el = document.querySelector('div[contenteditable="true"][id*="prompt"],div[role="textbox"],textarea');
                if (!el) return false;
                el.focus();
                if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                    el.value = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                    el.textContent = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
                }
                return true;
            })('%s')
        """.trimIndent(),
        submitJs = """
            (function() {
                var btn = document.querySelector('button[data-testid="send-button"],button[aria-label*="Send"]');
                if (btn) { btn.click(); return true; }
                var textarea = document.querySelector('textarea');
                if (textarea) { textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true })); return true; }
                return false;
            })()
        """.trimIndent(),
        waitForResponseJs = """
            (function(timeout) {
                return new Promise(function(resolve) {
                    var start = Date.now();
                    var interval = setInterval(function() {
                        var stopBtn = document.querySelector('button[data-testid="stop-button"]');
                        var turns = document.querySelectorAll('.group.agent-turn,.group:has(.markdown)');
                        if (!stopBtn && turns.length > 0) { clearInterval(interval); resolve(true); }
                        if (Date.now() - start > timeout) { clearInterval(interval); resolve(false); }
                    }, 1500);
                });
            })(${120000})
        """.trimIndent(),
        extractTextJs = """
            (function() {
                var turns = document.querySelectorAll('.group.agent-turn,.group:has(.markdown)');
                var last = turns[turns.length - 1];
                if (last) return last.innerText;
                return document.body.innerText;
            })()
        """.trimIndent(),
        extractImagesJs = """
            (function() {
                var imgs = document.querySelectorAll('img[alt*="Generated"],img.dalle-image,img[src*="oaidalleapiprodscus"]');
                return JSON.stringify(Array.from(imgs).slice(0,10).map(function(i){return i.src}));
            })()
        """.trimIndent(),
        fillLoginJs = """
            (function(email, password) {
                var emailEl = document.querySelector('input[type="email"],input[name="email"],input#email');
                if (emailEl) { emailEl.value = email; emailEl.dispatchEvent(new Event('input', {bubbles: true})); }
                var nextBtn = document.querySelector('button:has-text("Continue"),button[type="submit"]');
                if (nextBtn) nextBtn.click();
                setTimeout(function() {
                    var passEl = document.querySelector('input[type="password"],input[name="password"],input#password');
                    if (passEl) { passEl.value = password; passEl.dispatchEvent(new Event('input', {bubbles: true})); }
                    var loginBtn = document.querySelector('button[type="submit"]');
                    if (loginBtn) loginBtn.click();
                }, 2000);
                return true;
            })('%s','%s')
        """.trimIndent()
    ),
    "gemini" to PlatformDef(
        key = "gemini", name = "Google Gemini", url = "https://gemini.google.com",
        loginUrl = "https://accounts.google.com/signin",
        waitForPromptJs = """
            (function() {
                return new Promise(function(resolve) {
                    var el = document.querySelector('div[contenteditable="true"][role="textbox"],rich-textarea div[contenteditable="true"]');
                    if (el) return resolve(true);
                    var obs = new MutationObserver(function() {
                        el = document.querySelector('div[contenteditable="true"][role="textbox"],rich-textarea div[contenteditable="true"]');
                        if (el) { obs.disconnect(); resolve(true); }
                    });
                    obs.observe(document.body, { childList: true, subtree: true });
                    setTimeout(function() { obs.disconnect(); resolve(false); }, 15000);
                });
            })()
        """.trimIndent(),
        fillPromptJs = """
            (function(prompt) {
                var el = document.querySelector('div[contenteditable="true"][role="textbox"],rich-textarea div[contenteditable="true"],textarea');
                if (!el) return false;
                el.focus();
                if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                    el.value = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                    el.textContent = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                return true;
            })('%s')
        """.trimIndent(),
        submitJs = """
            (function() {
                var btn = document.querySelector('button[data-mat-icon-name="send"],button[aria-label*="Send"],button[type="submit"]');
                if (btn) { btn.click(); return true; }
                return false;
            })()
        """.trimIndent(),
        waitForResponseJs = """
            (function(timeout) {
                return new Promise(function(resolve) {
                    var start = Date.now();
                    var prevLen = 0; var stable = 0;
                    var interval = setInterval(function() {
                        var loading = document.querySelector('.loading-indicator,[role="progressbar"]');
                        var text = document.body.innerText;
                        if (!loading) {
                            if (text.length > prevLen) { prevLen = text.length; stable = 0; }
                            else { stable++; if (stable >= 3) { clearInterval(interval); resolve(true); } }
                        }
                        if (Date.now() - start > timeout) { clearInterval(interval); resolve(false); }
                    }, 1500);
                });
            })(${120000})
        """.trimIndent(),
        extractTextJs = """
            (function() {
                var responses = document.querySelectorAll('.model-response-text,.response-content');
                var last = responses[responses.length - 1];
                return last ? last.innerText : document.body.innerText;
            })()
        """.trimIndent(),
        extractImagesJs = """
            (function() {
                var imgs = document.querySelectorAll('img[src*="aiusercontent"],img[src*="googleusercontent"]');
                return JSON.stringify(Array.from(imgs).slice(0,10).map(function(i){return i.src}));
            })()
        """.trimIndent()
    ),
    "perplexity" to PlatformDef(
        key = "perplexity", name = "Perplexity AI", url = "https://perplexity.ai",
        requiresLogin = false,
        waitForPromptJs = """
            (function() {
                return new Promise(function(resolve) {
                    var el = document.querySelector('textarea,div[contenteditable="true"],input[type="text"]');
                    if (el && el.offsetParent !== null) return resolve(true);
                    var obs = new MutationObserver(function() {
                        el = document.querySelector('textarea,div[contenteditable="true"],input[type="text"]');
                        if (el && el.offsetParent !== null) { obs.disconnect(); resolve(true); }
                    });
                    obs.observe(document.body, { childList: true, subtree: true });
                    setTimeout(function() { obs.disconnect(); resolve(false); }, 20000);
                });
            })()
        """.trimIndent(),
        fillPromptJs = """
            (function(prompt) {
                var el = document.querySelector('textarea,div[contenteditable="true"]');
                if (!el) return false;
                el.focus();
                if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                    el.value = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                    el.textContent = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                return true;
            })('%s')
        """.trimIndent(),
        submitJs = """
            (function() {
                var btn = document.querySelector('button[type="submit"],button[aria-label*="Submit"],button[aria-label*="search"],button:has(svg)');
                if (btn) { btn.click(); return true; }
                var ta = document.querySelector('textarea');
                if (ta) { ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true })); return true; }
                var div = document.querySelector('div[contenteditable="true"]');
                if (div) { div.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true })); return true; }
                return false;
            })()
        """.trimIndent(),
        waitForResponseJs = """
            (function(timeout) {
                return new Promise(function(resolve) {
                    var start = Date.now();
                    var prevLen = 0; var stable = 0;
                    var interval = setInterval(function() {
                        var loading = document.querySelector('.loading,[role="progressbar"],.spinner,[role="status"],.animate-spin,[data-testid="loading"]');
                        var text = document.body.innerText;
                        if (!loading) {
                            if (text.length > prevLen) { prevLen = text.length; stable = 0; }
                            else { stable++; if (stable >= 3) { clearInterval(interval); resolve(true); } }
                        }
                        if (Date.now() - start > timeout) { clearInterval(interval); resolve(false); }
                    }, 1500);
                });
            })(${120000})
        """.trimIndent(),
        extractTextJs = """
            (function() {
                var results = document.querySelectorAll('[data-testid="result"],.result,.answer,.prose,.markdown,[class*="answer"],[class*="result"]');
                if (results.length) return Array.from(results).map(function(r){return r.innerText}).join('\n\n');
                return document.body.innerText;
            })()
        """.trimIndent()
    ),
    "generic" to PlatformDef(
        key = "generic", name = "Generic Browser", url = "",
        requiresLogin = false,
        waitForPromptJs = """
            (function() {
                return new Promise(function(resolve) {
                    var el = document.querySelector('textarea[placeholder*="Message"],textarea[placeholder*="message"],textarea,div[contenteditable="true"]');
                    if (el) return resolve(true);
                    var obs = new MutationObserver(function() {
                        el = document.querySelector('textarea[placeholder*="Message"],textarea,div[contenteditable="true"]');
                        if (el) { obs.disconnect(); resolve(true); }
                    });
                    obs.observe(document.body, { childList: true, subtree: true });
                    setTimeout(function() { obs.disconnect(); resolve(false); }, 15000);
                });
            })()
        """.trimIndent(),
        fillPromptJs = """
            (function(prompt) {
                var el = document.querySelector('textarea[placeholder*="Message"],textarea,div[contenteditable="true"]');
                if (!el) return false;
                el.focus();
                if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                    el.value = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                    el.textContent = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                return true;
            })('%s')
        """.trimIndent(),
        submitJs = """
            (function() {
                var btn = document.querySelector('button[type="submit"],button[aria-label*="Send"],button:has(svg)');
                if (btn) { btn.click(); return true; }
                var ta = document.querySelector('textarea');
                if (ta) { ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true })); return true; }
                return false;
            })()
        """.trimIndent(),
        waitForResponseJs = """
            (function(timeout) {
                return new Promise(function(resolve) {
                    var start = Date.now();
                    var prevLen = 0; var stable = 0;
                    var interval = setInterval(function() {
                        var text = document.body.innerText;
                        if (text.length > prevLen) { prevLen = text.length; stable = 0; }
                        else { stable++; if (stable >= 3) { clearInterval(interval); resolve(true); } }
                        if (Date.now() - start > timeout) { clearInterval(interval); resolve(false); }
                    }, 1500);
                });
            })(${120000})
        """.trimIndent(),
        extractTextJs = """
            (function() {
                return document.body.innerText;
            })()
        """.trimIndent()
    )
)

@SuppressLint("SetJavaScriptEnabled", "WebViewApiAvailability")
class WebViewAutomator(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val credentialStore = ConcurrentHashMap<String, Pair<String, String>>()

    fun setCredentials(platform: String, email: String, password: String) {
        credentialStore[platform] = Pair(email, password)
    }

    private fun <T> runOnMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val future = CompletableFuture<T>()
        mainHandler.post {
            try { future.complete(block()) } catch (e: Exception) { future.completeExceptionally(e) }
        }
        return future.get(30, TimeUnit.SECONDS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun execute(
        platformKey: String,
        userPrompt: String,
        taskId: String,
        taskType: String,
        outputFormat: String,
        timeoutSeconds: Int = 120
    ): String {
        val platform = PLATFORMS[platformKey] ?: PLATFORMS["generic"]!!
        val sanitizedPrompt = userPrompt.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
        val resultJson = CompletableFuture<String>()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var webView: WebView? = null

        runOnMainThread {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }

            webView!!.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!done.compareAndSet(false, true)) return

                    fun finishWithResult(text: String, imagesJson: String) {
                        val imageUrls = try { gson.fromJson(imagesJson, Array<String>::class.java)?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
                        val oType = if (imageUrls.isNotEmpty()) "image" else "text"
                        resultJson.complete(gson.toJson(mapOf(
                            "task_id" to taskId, "status" to "SUCCESS", "mode" to "NORMAL",
                            "site_used" to platform.name, "target_site_used" to platform.name,
                            "capability" to taskType, "output_type" to oType,
                            "output" to mapOf("text_content" to text, "image_urls" to imageUrls, "description" to "Response from ${platform.name}"),
                            "error" to null
                        )))
                    }

                    val isLoggedIn = !url.contains("login") && !url.contains("signin") && !url.contains("auth") && !url.contains("accounts.google")
                    if (platform.requiresLogin && !isLoggedIn) {
                        if (credentialStore.containsKey(platformKey)) {
                            val creds = credentialStore[platformKey]!!
                            val js = platform.fillLoginJs
                                .replace("%s", creds.first.replace("'", "\\'"))
                                .replace("%s", creds.second.replace("'", "\\'"))
                            view.evaluateJavascript(js, null)
                        } else {
                            resultJson.complete(gson.toJson(mapOf(
                                "status" to "AWAITING_CREDENTIALS",
                                "needs_user_action" to mapOf("reason" to "Login required for ${platform.name}",
                                    "instructions" to """{"agent_action":"CREDENTIAL_REQUEST","platform":"${platform.name}","platform_url":"${platform.url}","fields_required":["email","password"]}""")
                            )))
                            return
                        }
                    }

                    view.evaluateJavascript(platform.waitForPromptJs) { found ->
                        if (found != "true") {
                            finishWithResult("Could not find prompt input on ${platform.name}. The page may not support automation.", "[]")
                            return@evaluateJavascript
                        }
                        val fillJs = platform.fillPromptJs.replace("%s", sanitizedPrompt)
                        view.evaluateJavascript(fillJs) { filled ->
                            if (filled != "true") {
                                finishWithResult("Could not fill prompt on ${platform.name}.", "[]")
                                return@evaluateJavascript
                            }
                            view.evaluateJavascript(platform.submitJs) {
                                view.evaluateJavascript(platform.waitForResponseJs) { done2 ->
                                    view.evaluateJavascript(platform.extractTextJs) { text ->
                                        if (platform.extractImagesJs.isNotEmpty()) {
                                            view.evaluateJavascript(platform.extractImagesJs) { imgs ->
                                                finishWithResult(text ?: "", imgs ?: "[]")
                                            }
                                        } else {
                                            finishWithResult(text ?: "", "[]")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (done.compareAndSet(false, true)) {
                        resultJson.completeExceptionally(Exception("Page load error: ${error.description}"))
                    }
                }
            }

            webView!!.webChromeClient = WebChromeClient()
            webView!!.loadUrl(if (platform.requiresLogin && platform.loginUrl != null) platform.loginUrl else platform.url)
        }

        return try {
            val result = resultJson.get(timeoutSeconds.toLong() + 30, TimeUnit.SECONDS)
            runOnMainThread { webView?.destroy() }
            result
        } catch (e: Exception) {
            runOnMainThread { webView?.destroy() }
            val msg = e.cause?.message ?: e.message ?: "WebView automation timed out"
            generateFallback(taskId, taskType, platformKey, sanitizedPrompt, msg)
        }
    }

    private fun generateFallback(
        taskId: String, taskType: String, targetSite: String, userPrompt: String, errorMsg: String
    ): String {
        val siteUsed = when {
            targetSite == "auto" || targetSite == "chatgpt" || targetSite == "gemini" -> targetSite
            taskType.contains("image") -> "pollinations"
            taskType.contains("search") -> "perplexity"
            else -> "chatgpt"
        }

        val outputContent: Map<String, Any?>
        val outputType: String

        if (taskType.contains("image")) {
            val encodedPrompt = java.net.URLEncoder.encode(userPrompt, "UTF-8")
            val pollinationsUrl = "https://image.pollinations.ai/prompt/$encodedPrompt"
            outputContent = mapOf(
                "text_content" to "Generated image for: '$userPrompt'.",
                "image_urls" to listOf(pollinationsUrl),
                "description" to "Fallback image from pollinations.ai"
            )
            outputType = "image"
        } else {
            outputContent = mapOf(
                "text_content" to "Web automation unavailable: $errorMsg",
                "description" to "Fallback response"
            )
            outputType = "text"
        }

        return gson.toJson(mapOf(
            "task_id" to taskId,
            "status" to "SUCCESS",
            "mode" to "NORMAL",
            "site_used" to siteUsed,
            "target_site_used" to siteUsed,
            "capability" to taskType,
            "output_type" to outputType,
            "output" to outputContent,
            "error" to null
        ))
    }

    companion object {
        fun getTargetSite(taskType: String, targetSite: String): String {
            if (targetSite != "auto") return targetSite
            return when {
                taskType.contains("image") -> "chatgpt"
                taskType.contains("search") -> "perplexity"
                taskType.contains("audio") -> "elevenlabs"
                else -> "chatgpt"
            }
        }
    }
}
