const ROUTING_TABLE = {
  IMAGE_GENERATION: [
    { key: 'chatgpt', name: 'ChatGPT (DALL·E 3)', url: 'https://chatgpt.com', requires_login: true },
    { key: 'gemini', name: 'Google Gemini', url: 'https://gemini.google.com', requires_login: true },
    { key: 'copilot', name: 'Microsoft Copilot', url: 'https://copilot.microsoft.com', requires_login: false },
    { key: 'firefly', name: 'Adobe Firefly', url: 'https://firefly.adobe.com', requires_login: true },
    { key: 'ideogram', name: 'Ideogram', url: 'https://ideogram.ai', requires_login: true }
  ],
  VIDEO_GENERATION: [
    { key: 'gemini', name: 'Google Gemini (Veo)', url: 'https://gemini.google.com', requires_login: true },
    { key: 'runway', name: 'Runway ML', url: 'https://runwayml.com', requires_login: true },
    { key: 'kling', name: 'Kling AI', url: 'https://klingai.com', requires_login: true },
    { key: 'pika', name: 'Pika Labs', url: 'https://pika.art', requires_login: true }
  ],
  AUDIO_GENERATION: [
    { key: 'elevenlabs', name: 'ElevenLabs', url: 'https://elevenlabs.io', requires_login: true },
    { key: 'suno', name: 'Suno AI', url: 'https://suno.com', requires_login: true },
    { key: 'gemini', name: 'Google NotebookLM', url: 'https://notebooklm.google.com', requires_login: true }
  ],
  CODE_GENERATION: [
    { key: 'chatgpt', name: 'ChatGPT', url: 'https://chatgpt.com', requires_login: true },
    { key: 'gemini', name: 'Google Gemini', url: 'https://gemini.google.com', requires_login: true }
  ],
  WEB_SEARCH: [
    { key: 'perplexity', name: 'Perplexity AI', url: 'https://perplexity.ai', requires_login: false },
    { key: 'chatgpt', name: 'ChatGPT (web search)', url: 'https://chatgpt.com', requires_login: true },
    { key: 'gemini', name: 'Google Gemini', url: 'https://gemini.google.com', requires_login: true }
  ],
  GENERAL_TEXT: [
    { key: 'chatgpt', name: 'ChatGPT', url: 'https://chatgpt.com', requires_login: true },
    { key: 'gemini', name: 'Google Gemini', url: 'https://gemini.google.com', requires_login: true },
    { key: 'perplexity', name: 'Perplexity AI', url: 'https://perplexity.ai', requires_login: false }
  ]
};

export function selectPlatform(taskType, preferredSite = 'auto', vault) {
  const normalizedType = taskType?.toUpperCase() || 'GENERAL_TEXT';
  const routes = ROUTING_TABLE[normalizedType];
  if (!routes) {
    return { key: 'generic', name: 'Generic Browser', url: '', requires_login: false };
  }

  if (preferredSite && preferredSite !== 'auto') {
    const preferred = routes.find(r => r.key === preferredSite);
    if (preferred && (!preferred.requires_login || vault?.has(preferred.key))) {
      return preferred;
    }
  }

  for (const route of routes) {
    if (!route.requires_login || vault?.has(route.key)) {
      return route;
    }
  }

  return routes[0];
}

export function verifyRoute(taskType, platformKey) {
  const routes = ROUTING_TABLE[taskType];
  if (!routes) return false;
  return routes.some(r => r.key === platformKey);
}

export function getSupportedTaskTypes() {
  return Object.keys(ROUTING_TABLE);
}
