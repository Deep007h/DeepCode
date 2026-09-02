import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://ideogram.ai/login', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);

    const emailInput = await page.$('input[type="email"], input[name="email"]');
    if (emailInput) {
      await randomDelay(300, 600);
      await emailInput.fill(credentials.email);
      await randomDelay(400, 700);
    }

    const passwordInput = await page.$('input[type="password"], input[name="password"]');
    if (passwordInput) {
      await randomDelay(300, 600);
      await passwordInput.fill(credentials.password);
      await randomDelay(400, 700);
    }

    const submitBtn = await page.$('button[type="submit"]');
    if (submitBtn) {
      await simulateMouseMove(page, submitBtn);
      await submitBtn.click();
      await randomDelay(3000, 5000);
    }
  } catch (err) {
    logger.warn(`Ideogram login: ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://ideogram.ai', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://ideogram.ai', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { timeout } = opts;

  const promptSel = 'textarea[placeholder*="prompt"], textarea[placeholder*="Describe"], textarea, input[placeholder*="prompt"]';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Ideogram prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const generateBtn = await page.$('button:has-text("Generate"), button[type="submit"]');
  if (generateBtn) {
    await simulateMouseMove(page, generateBtn);
    await generateBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let imageUrls = [];

  while (Date.now() < waitEnd) {
    await randomDelay(2000, 3000);
    imageUrls = await page.evaluate(() => {
      const imgs = document.querySelectorAll('img[src*="ideogram"], img[alt*="generated"]');
      return Array.from(imgs).filter(img => img.naturalWidth > 200).slice(0, 4).map(img => img.src);
    });
    if (imageUrls.length > 0) break;
  }

  return {
    type: 'image',
    imageUrls,
    text: userPrompt,
    description: 'Generated image from Ideogram'
  };
}
