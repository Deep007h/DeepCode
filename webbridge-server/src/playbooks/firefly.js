import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://firefly.adobe.com', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);

    const signInBtn = await page.$('button:has-text("Sign in"), a:has-text("Sign in")');
    if (signInBtn) {
      await simulateMouseMove(page, signInBtn);
      await signInBtn.click();
      await randomDelay(2000, 3000);
    }

    const emailInput = await page.$('input[type="email"]');
    if (emailInput) {
      await randomDelay(300, 600);
      await emailInput.fill(credentials.email);
      await page.keyboard.press('Enter');
      await randomDelay(2000, 3000);
    }

    const passwordInput = await page.$('input[type="password"]');
    if (passwordInput) {
      await randomDelay(300, 600);
      await passwordInput.fill(credentials.password);
      await page.keyboard.press('Enter');
      await randomDelay(3000, 5000);
    }
  } catch (err) {
    logger.warn(`Firefly login: ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://firefly.adobe.com/generate/images', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://firefly.adobe.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { timeout } = opts;

  const promptSel = 'textarea[placeholder*="Describe"], textarea[placeholder*="prompt"], input[placeholder*="Describe"], textarea';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Firefly prompt input not found');
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
      const imgs = document.querySelectorAll('img[src*="firefly"], img[src*="adobe"]');
      return Array.from(imgs).filter(img => img.naturalWidth > 200).slice(0, 4).map(img => img.src);
    });
    if (imageUrls.length > 0) break;
  }

  return {
    type: 'image',
    imageUrls,
    text: userPrompt,
    description: 'Generated image from Adobe Firefly'
  };
}
