import { chromium } from 'playwright';
import { existsSync } from 'fs';
import { execSync } from 'child_process';
import { randomDelay, simulateMouseMove, logger } from './utils.js';

const CANDIDATE_PATHS = [
  // Linux & Termux candidates
  '/usr/bin/google-chrome',
  '/usr/bin/google-chrome-stable',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
  '/data/data/com.termux/files/usr/bin/chromium',
  '/snap/bin/chromium',
  '/usr/bin/brave-browser',
  '/usr/bin/microsoft-edge',
  '/usr/bin/microsoft-edge-stable',
  // macOS candidates
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Chromium.app/Contents/MacOS/Chromium',
  '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser',
  '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  // Windows candidates
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
];

export function discoverBrowserPath() {
  if (process.env.CHROMIUM_PATH && existsSync(process.env.CHROMIUM_PATH)) {
    return process.env.CHROMIUM_PATH;
  }
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH && existsSync(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH)) {
    return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  }
  for (const candidate of CANDIDATE_PATHS) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  // Try 'which' lookup on unix-like systems
  if (process.platform !== 'win32') {
    const bins = ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser', 'brave-browser', 'microsoft-edge'];
    for (const bin of bins) {
      try {
        const found = execSync(`which ${bin} 2>/dev/null`, { encoding: 'utf-8' }).trim();
        if (found && existsSync(found)) {
          return found;
        }
      } catch {}
    }
  }
  return null;
}

export class BrowserManager {
  constructor() {
    this.browser = null;
    this.context = null;
    this.pages = [];
    this.maxConcurrent = parseInt(process.env.MAX_CONCURRENT_SESSIONS || '3');
  }

  async initialize() {
    if (this.browser) return;

    const launchOptions = {
      headless: process.env.NODE_ENV === 'production' || !process.env.DEBUG,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',
        '--disable-infobars',
        '--window-size=1920,1080'
      ]
    };

    const discoveredPath = discoverBrowserPath();
    if (discoveredPath) {
      launchOptions.executablePath = discoveredPath;
      logger.info(`Using discovered browser at: ${discoveredPath}`);
    }

    try {
      this.browser = await chromium.launch(launchOptions);
    } catch (err) {
      logger.warn(`Primary browser launch failed (${err.message}). Attempting fallback channels...`);
      // Fallback 1: try channel 'chrome'
      try {
        const { executablePath, ...optsWithoutPath } = launchOptions;
        this.browser = await chromium.launch({ ...optsWithoutPath, channel: 'chrome' });
        logger.info('Browser launched using channel: chrome');
      } catch (channelErr) {
        // Fallback 2: try channel 'chromium'
        try {
          const { executablePath, ...optsWithoutPath } = launchOptions;
          this.browser = await chromium.launch({ ...optsWithoutPath, channel: 'chromium' });
          logger.info('Browser launched using channel: chromium');
        } catch (chromiumErr) {
          logger.error(`All browser launch methods failed: ${err.message}`);
          throw new Error(`Failed to launch browser. Please install Chromium or set CHROMIUM_PATH: ${err.message}`);
        }
      }
    }

    logger.info('Browser launched');

    this.context = await this.browser.newContext({
      viewport: { width: 1920, height: 1080 },
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
      locale: 'en-US',
      permissions: [],
      bypassCSP: true
    });

    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => false });
      Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
      Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
      window.chrome = { runtime: {} };
    });
  }

  async newPage(freshSession = true) {
    if (!this.context) await this.initialize();

    if (freshSession) {
      const page = await this.context.newPage();
      this.pages.push(page);
      return page;
    }

    if (this.pages.length === 0) {
      const page = await this.context.newPage();
      this.pages.push(page);
      return page;
    }

    return this.pages[0];
  }

  async shutdown() {
    for (const page of this.pages) {
      try { await page.close(); } catch {}
    }
    this.pages = [];
    if (this.context) {
      try { await this.context.close(); } catch {}
    }
    if (this.browser) {
      try { await this.browser.close(); } catch {}
    }
    this.browser = null;
    this.context = null;
    logger.info('Browser shut down');
  }
}
