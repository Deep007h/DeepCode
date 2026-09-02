import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://runwayml.com/login', { waitUntil: 'networkidle', timeout: 20000 });
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
    logger.warn(`Runway login: ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://runwayml.com', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://runwayml.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { timeout } = opts;

  const promptSel = 'textarea[placeholder*="Describe"], textarea[placeholder*="prompt"], textarea';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Runway prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const generateBtn = await page.$('button[type="submit"]:has-text("Generate"), button:has-text("Generate")');
  if (generateBtn) {
    await simulateMouseMove(page, generateBtn);
    await generateBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let videoSrc = null;

  while (Date.now() < waitEnd) {
    await randomDelay(3000, 5000);
    videoSrc = await page.evaluate(() => {
      const video = document.querySelector('video[src*="runway"], source[src*="runway"]');
      return video ? (video.src || video.getAttribute('src')) : null;
    });
    if (videoSrc) break;

    const thumbnail = await page.$('img[src*="runway"]');
    if (thumbnail) break;
  }

  return {
    type: 'video',
    text: userPrompt,
    videoUrl: videoSrc,
    description: 'Generated video from Runway ML'
  };
}
