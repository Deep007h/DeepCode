# 🤖 Free-Tier & Local Model Configuration Guide

DeepCode for Android is designed to run entirely with free-tier AI endpoints and local LLMs out of the box, ensuring that you do not need expensive subscriptions to get started.

---

## 1. 🌟 DeepCode Zen AI (Free & Paid)

Zen AI models run via the Zen API endpoint (no key required for free tier, or custom user key):

- **DeepSeek V4 Flash Free** (`deepseek-v4-flash-free` - 64k context, Default)
- **MiMo V2.5 Free** (`mimo-v2.5-free` - 32k context)
- **Nemotron 3 Ultra Free** (`nemotron-3-ultra-free` - 128k context)
- **Muse Spark 1.2 Free** (`muse-spark-1.2-contributor-free` - 32k context)
- **Ling 3.0 Flash Free** (`ling-3.0-flash-fin-free` - 32k context)
- **Open Coder Flash Free** (`open-coder-flash-free` - 64k context)
- **DeepSeek R1 Distill LLaMA 70B Free** (`deepseek-r1-distill-llama-70b-free` - 64k context)

### Zen API Endpoint Details
```
Base URL : https://opencode.ai/zen/v1
Auth     : Bearer <ZEN_API_KEY> (Optional for free tier) ← Set in Settings → API Keys
Headers  : X-OpenCode-Client: android/1.0.0
```

---

## 2. 🦙 Local Ollama Configuration (Offline & Private)

Ollama allows you to run open-source models (like Llama 3, Qwen 2.5 Coder, Mistral) locally on your PC or developer workstation and use them on your Android device.

### Workstation Setup (Host PC)
1. Install Ollama on your workstation (Linux, macOS, or Windows):
   ```bash
   curl -fsSL https://ollama.com/install.sh | sh
   ```
2. Configure Ollama to accept external connections from your local network.
   - **Linux/Systemd**: Edit the service configuration or set the environment variable:
     ```bash
     sudo systemctl edit ollama.service
     # Add the environment variables:
     [Service]
     Environment="OLLAMA_HOST=0.0.0.0"
     ```
     Restart Ollama:
     ```bash
     sudo systemctl daemon-reload
     sudo systemctl restart ollama
     ```
   - **macOS/Windows**: Set the environment variable `OLLAMA_HOST=0.0.0.0` before launching Ollama.
3. Download a coder model:
   ```bash
   ollama pull qwen2.5-coder:7b
   ```

### Android App Setup
1. In DeepCode for Android, go to **Settings** (Gear Icon).
2. Set the **Ollama Custom Base URL** to your host workstation's local IP address (e.g., `http://192.168.1.100:11434`).
3. In the Chat screen, open the Model Picker, select **Ollama**, and pick **ollama-local**.

---

## 3. 🌌 Google Gemini Free Tier

Google offers generous free tiers for its Gemini API keys via Google AI Studio.

### Credentials Setup
1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Click **Create API Key**.
3. Copy the generated key.

### Android App Setup
1. Open the **Settings** screen in the app.
2. Paste the key under **Google Gemini API Key**.
3. In the Chat screen, select **Google Gemini** -> **gemini-2.5-flash** or **gemini-2.5-pro**.

---

## 4. ⚡ Groq Cloud API (Ultra-Fast Coding)

Groq offers extremely fast inference with free development tiers for models like Llama 3.3.

### Credentials Setup
1. Go to the [Groq Console](https://console.groq.com/).
2. Create an account and navigate to **API Keys**.
3. Click **Create API Key** and copy the token.

### Android App Setup
1. Open **Settings** in the app.
2. Paste your token under **Groq API Key**.
3. Select models like **llama-3.3-70b-versatile** in the chat interface.

---

## 5. 🔗 OpenRouter API (Aggregation Hub)

OpenRouter aggregates hundreds of models and offers free models.

### Credentials Setup
1. Sign up at [OpenRouter](https://openrouter.ai/).
2. Go to **API Keys** -> **Create Key**.
3. Copy your key.

### Android App Setup
1. Open **Settings** in the app.
2. Paste your token under **OpenRouter API Key**.
3. Select any free-tier model (e.g., Gemma 2 9B (Free)) in the Model Picker.

---

## 6. ☁️ GMI Cloud API (High-Performance GPU Cluster)

GMI Cloud provides high-throughput OpenAI-compatible inference with cutting-edge models like Qwen 3.8 Flash, DeepSeek V4 Flash, Gemini 3.8 Flash, Kimi K3, and Nemotron 3.5.

### Credentials Setup
1. Sign up or log into the [GMI Cloud Console](https://console.gmicloud.ai).
2. Generate an API Key under **API Keys**.
3. Ensure account balance/credits are active for inference.

### Android App Setup
1. In DeepCode for Android, navigate to **Settings** -> **API Keys**.
2. Locate **GMI Cloud** and paste your API key.
3. (Optional) Custom endpoint defaults to `https://api.gmi-serving.com/v1`.
4. In the Chat screen model picker, select **GMI Cloud** and choose from the fetched catalog (e.g. `Qwen/Qwen3.8-Flash`, `deepseek-ai/DeepSeek-V4-Flash`).

