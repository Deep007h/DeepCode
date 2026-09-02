import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://accounts.google.com/signin', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);

    const emailInput = await page.$('input[type="email"], input[name="identifier"]');
    if (emailInput) {
      await randomDelay(300, 600);
      await emailInput.fill(credentials.email);
      await randomDelay(500, 800);
      const nextBtn = await page.$('button:has-text("Next"), span:has-text("Next")');
      if (nextBtn) {
        await nextBtn.click();
      } else {
        await page.keyboard.press('Enter');
      }
      await randomDelay(2000, 3000);
    }

    const passwordInput = await page.$('input[type="password"], input[name="password"]');
    if (passwordInput) {
      await randomDelay(300, 600);
      await passwordInput.fill(credentials.password);
      await randomDelay(500, 800);
      const nextBtn = await page.$('button:has-text("Next"), span:has-text("Next")');
      if (nextBtn) {
        await nextBtn.click();
      } else {
        await page.keyboard.press('Enter');
      }
      await randomDelay(3000, 5000);
    }

    const otpInput = await page.$('input[type="tel"]');
    if (otpInput) {
      throw new Error('AUTH_FAILED: 2FA required for Google account. User must complete sign-in manually.');
    }

    await randomDelay(1000, 2000);
  } catch (err) {
    if (err.message.includes('2FA')) throw err;
    logger.warn(`Gemini login: ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://gemini.google.com', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    await page.goto('https://gemini.google.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { outputFormat, timeout } = opts;

  const promptSel = 'div[contenteditable="true"][role="textbox"], rich-textarea div[contenteditable="true"], textarea';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: Gemini prompt input not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const sendBtn = await page.$('button[data-mat-icon-name="send"], button[aria-label*="Send"]');
  if (sendBtn) {
    await simulateMouseMove(page, sendBtn);
    await sendBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  const waitEnd = Date.now() + (timeout * 1000);
  let stableCount = 0;
  let lastTextLength = 0;

  while (Date.now() < waitEnd) {
    await randomDelay(1500, 2500);

    const loading = await page.$('.loading-indicator, [role="progressbar"]');
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

  await randomDelay(1000, 2000);

  const text = await page.evaluate(() => {
    const responses = document.querySelectorAll('.model-response-text, .response-content');
    const last = responses[responses.length - 1];
    return last ? last.innerText : document.body.innerText;
  });

  const imageUrls = await page.evaluate(() => {
    const imgs = document.querySelectorAll('img[src*="aiusercontent"], img[src*="googleusercontent"]');
    return Array.from(imgs).slice(0, 10).map(img => img.src);
  });

  const videoSrc = await page.evaluate(() => {
    const video = document.querySelector('video[src]');
    return video ? video.src : null;
  });

  return {
    type: videoSrc ? 'video' : (imageUrls.length > 0 ? 'image' : 'text'),
    text,
    imageUrls,
    videoUrl: videoSrc,
    description: videoSrc ? 'Generated video from Gemini (Veo)' : (imageUrls.length > 0 ? 'Generated image from Gemini' : 'Response from Gemini')
  };
}
