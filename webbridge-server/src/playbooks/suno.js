import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://suno.com/login', { waitUntil: 'networkidle', timeout: 20000 });
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
    logger.warn(`Suno login: ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://suno.com/create', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://suno.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { timeout } = opts;

  const promptSel = 'textarea[placeholder*="song"], textarea[placeholder*="describe"], textarea, input[placeholder*="song"]';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Suno prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const createBtn = await page.$('button:has-text("Create"), button[type="submit"]');
  if (createBtn) {
    await simulateMouseMove(page, createBtn);
    await createBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let audioUrl = null;

  while (Date.now() < waitEnd) {
    await randomDelay(3000, 5000);
    audioUrl = await page.evaluate(() => {
      const audio = document.querySelector('audio[src], audio source[src]');
      return audio ? (audio.src || audio.querySelector('source')?.src) : null;
    });
    if (audioUrl) break;
  }

  return {
    type: 'audio',
    audioUrl,
    text: userPrompt,
    description: 'Generated audio from Suno AI'
  };
}
