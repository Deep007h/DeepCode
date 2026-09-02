import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;
  logger.info('Copilot: No login required for basic usage');
}

export async function navigate(page, url) {
  try {
    await page.goto('https://copilot.microsoft.com', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://copilot.microsoft.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { outputFormat, timeout } = opts;

  const promptSel = 'textarea[id="searchbox"], div[role="textbox"], textarea[placeholder*="Ask"], textarea';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Copilot prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const submitBtn = await page.$('button[aria-label="Submit"], button[type="submit"]');
  if (submitBtn) {
    await simulateMouseMove(page, submitBtn);
    await submitBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let stableCount = 0;
  let lastTextLength = 0;

  while (Date.now() < waitEnd) {
    await randomDelay(1500, 2500);

    const spinner = await page.$('.ac-container .loading, [role="progressbar"]');
    if (!spinner) {
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

  await randomDelay(1000, 2000);

  const text = await page.evaluate(() => document.body.innerText);

  const imageUrls = await page.evaluate(() => {
    const imgs = document.querySelectorAll('img[class*="ac-image"], img[src*="bing"], img[src*="copilot"]');
    return Array.from(imgs).slice(0, 10).map(img => img.src);
  });

  return {
    type: imageUrls.length > 0 ? 'image' : 'text',
    text,
    imageUrls,
    description: 'Response from Microsoft Copilot'
  };
}
