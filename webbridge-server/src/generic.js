import { randomDelay, simulateMouseMove, logger } from './utils.js';

const PROMPT_SELECTORS = [
  'textarea[placeholder*="Message"]',
  'textarea[placeholder*="message"]',
  'textarea[placeholder*="Ask"]',
  'textarea[placeholder*="ask"]',
  'div[contenteditable="true"]',
  'div[role="textbox"]',
  'textarea:not([type="hidden"])',
  'input[type="text"]'
];

const SUBMIT_SELECTORS = [
  'button[type="submit"]',
  'button:has(svg)',
  'button[aria-label*="Send"]',
  'button[aria-label*="send"]',
  'button:has-text("Send")',
  'button:has-text("Submit")',
  'button:has-text("Generate")'
];

const LOGIN_EMAIL_SELECTORS = [
  'input[type="email"]',
  'input[name="email"]',
  'input[name="identifier"]',
  'input[placeholder*="Email"]',
  'input[placeholder*="email"]',
  'input[autocomplete="email"]'
];

const LOGIN_PASSWORD_SELECTORS = [
  'input[type="password"]',
  'input[name="password"]',
  'input[placeholder*="Password"]',
  'input[placeholder*="password"]'
];

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.waitForSelector('input[type="email"], input[name="email"], input[name="identifier"]', { timeout: 5000 });
  } catch {
    const signInButton = await page.$('button:has-text("Sign in"), button:has-text("Log in"), a:has-text("Sign in"), a:has-text("Log in")');
    if (signInButton) {
      await simulateMouseMove(page, signInButton);
      await signInButton.click();
      await randomDelay(500, 1000);
    }
  }

  for (const sel of LOGIN_EMAIL_SELECTORS) {
    const el = await page.$(sel);
    if (el) {
      await randomDelay(200, 500);
      await el.fill(credentials.email);
      await page.keyboard.press('Enter');
      await randomDelay(1000, 2000);
      break;
    }
  }

  for (const sel of LOGIN_PASSWORD_SELECTORS) {
    const el = await page.$(sel);
    if (el) {
      await randomDelay(200, 500);
      await el.fill(credentials.password);
      await randomDelay(300, 600);
      await page.keyboard.press('Enter');
      await randomDelay(2000, 3000);
      break;
    }
  }

  try {
    await page.waitForNavigation({ timeout: 15000 });
  } catch {}
}

export async function navigate(page, url) {
  logger.info(`Navigating to ${url}`);
  await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
  await randomDelay(1000, 2000);
}

export async function execute(page, userPrompt, opts) {
  const { outputFormat, timeout } = opts;

  let promptEl = null;
  for (const sel of PROMPT_SELECTORS) {
    try {
      promptEl = await page.waitForSelector(sel, { timeout: 5000 });
      if (promptEl) break;
    } catch {}
  }

  if (!promptEl) {
    throw new Error('ELEMENT_NOT_FOUND: Could not locate prompt input on page');
  }

  await randomDelay(300, 700);
  await promptEl.click();
  await randomDelay(200, 400);
  await promptEl.fill(userPrompt);
  await randomDelay(300, 600);

  let submitBtn = null;
  for (const sel of SUBMIT_SELECTORS) {
    try {
      submitBtn = await page.$(sel);
      if (submitBtn) {
        await simulateMouseMove(page, submitBtn);
        await randomDelay(200, 400);
        await submitBtn.click();
        break;
      }
    } catch {}
  }

  if (!submitBtn) {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let lastTextLength = 0;
  let stableCount = 0;

  while (Date.now() < waitEnd) {
    await randomDelay(1000, 2000);

    try {
      const bodyText = await page.evaluate(() => document.body.innerText);
      if (bodyText.length > lastTextLength) {
        lastTextLength = bodyText.length;
        stableCount = 0;
      } else {
        stableCount++;
        if (stableCount >= 3) break;
      }
    } catch {}

    const stopBtn = await page.$('button:has-text("Stop"), button[aria-label*="Stop"]');
    if (!stopBtn) break;
  }

  await randomDelay(500, 1000);

  const text = await page.evaluate(() => document.body.innerText);
  const title = await page.title();

  let imageUrls = [];
  try {
    imageUrls = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img[src*="http"]'))
        .filter(img => img.naturalWidth > 100 && img.naturalHeight > 100)
        .slice(0, 10)
        .map(img => img.src)
    );
  } catch {}

  return {
    type: 'text',
    text,
    imageUrls,
    html: await page.content(),
    title,
    pageUrl: page.url()
  };
}
