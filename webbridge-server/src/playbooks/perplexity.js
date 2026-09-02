import { randomDelay, logger } from '../utils.js';

export async function login(page, credentials) {
}

export async function navigate(page, url) {
  try {
    await page.goto('https://perplexity.ai', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://perplexity.ai', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { timeout } = opts;

  const promptSel = 'textarea[placeholder*="Ask"], textarea[placeholder*="question"], textarea, input[type="search"]';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Perplexity prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);
  await page.keyboard.press('Enter');

  const waitEnd = Date.now() + (timeout * 1000);
  let stableCount = 0;
  let lastTextLength = 0;

  while (Date.now() < waitEnd) {
    await randomDelay(1500, 2500);

    const loading = await page.$('.loading, [role="progressbar"], .spinner');
    if (!loading) {
      const text = await page.evaluate(() => document.body.innerText);
      if (text.length > lastTextLength) {
        lastTextLength = text.length;
        stableCount = 0;
      } else {
        stableCount++;
        if (stableCount >= 3) break;
      }
    }
  }

  const text = await page.evaluate(() => {
    const results = document.querySelectorAll('[data-testid="result"], .result, .answer');
    if (results.length) return Array.from(results).map(r => r.innerText).join('\n\n');
    return document.body.innerText;
  });

  const sources = await page.evaluate(() => {
    const links = document.querySelectorAll('a[href*="http"]');
    return Array.from(links).slice(0, 5).map(a => ({ text: a.innerText, url: a.href }));
  });

  return {
    type: 'text',
    text: text + (sources.length ? '\n\n**Sources:**\n' + sources.map(s => `- [${s.text}](${s.url})`).join('\n') : ''),
    description: 'Search results from Perplexity AI'
  };
}
