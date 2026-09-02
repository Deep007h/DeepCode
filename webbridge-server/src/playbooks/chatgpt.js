import { randomDelay, simulateMouseMove, logger } from '../utils.js';

export async function login(page, credentials) {
  if (!credentials) return;

  try {
    await page.goto('https://chatgpt.com/auth/login', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);

    const continueWithGoogle = await page.$('button:has-text("Continue with Google"), div:has-text("Continue with Google") button');
    if (continueWithGoogle) {
      logger.info('ChatGPT: Google SSO detected, using email/password path');
    }

    const emailInput = await page.$('input[name="email"], input[type="email"], input#email-input, input#email');
    if (emailInput) {
      await randomDelay(300, 600);
      await emailInput.fill(credentials.email);
      await randomDelay(400, 800);
      const continueBtn = await page.$('button:has-text("Continue"), button[type="submit"]');
      if (continueBtn) {
        await simulateMouseMove(page, continueBtn);
        await continueBtn.click();
      } else {
        await page.keyboard.press('Enter');
      }
      await randomDelay(2000, 3000);
    }

    const passwordInput = await page.$('input[name="password"], input[type="password"], input#password');
    if (passwordInput) {
      await randomDelay(300, 600);
      await passwordInput.fill(credentials.password);
      await randomDelay(400, 800);
      const loginBtn = await page.$('button[type="submit"]');
      if (loginBtn) {
        await simulateMouseMove(page, loginBtn);
        await loginBtn.click();
      } else {
        await page.keyboard.press('Enter');
      }
      await randomDelay(3000, 5000);
    }

    try {
      await page.waitForFunction(
        () => document.title.includes('ChatGPT') || document.querySelector('[id*="prompt"], [data-testid*="prompt"]'),
        { timeout: 20000 }
      );
      logger.info('ChatGPT: Login successful');
    } catch {
      logger.warn('ChatGPT: Login may have failed, continuing anyway');
    }
  } catch (err) {
    logger.warn(`ChatGPT login: ${err.message}`);
    throw new Error(`AUTH_FAILED: ChatGPT login failed - ${err.message}`);
  }
}

export async function navigate(page, url) {
  try {
    await page.goto('https://chatgpt.com', { waitUntil: 'networkidle', timeout: 20000 });
    await randomDelay(1000, 2000);
  } catch (err) {
    logger.warn(`ChatGPT navigate: ${err.message}, trying alternative...`);
    await page.goto('https://chatgpt.com', { timeout: 30000 });
  }
}

export async function execute(page, userPrompt, opts) {
  const { outputFormat, timeout } = opts;

  const promptSel = 'div[id="prompt-textarea"][contenteditable="true"], textarea[placeholder*="Message"], div[role="textbox"]';
  let promptEl;
  try {
    promptEl = await page.waitForSelector(promptSel, { timeout: 15000 });
  } catch {
    throw new Error('ELEMENT_NOT_FOUND: ChatGPT prompt textarea not found');
  }

  await randomDelay(500, 1000);
  await promptEl.click();
  await randomDelay(300, 600);
  await promptEl.fill(userPrompt);
  await randomDelay(500, 1000);

  const sendBtn = await page.$('button[data-testid="send-button"], button[aria-label*="Send"]');
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

    const stopBtn = await page.$('button[data-testid="stop-button"]');
    if (!stopBtn) break;

    const textContent = await page.evaluate(() => document.body.innerText);
    if (textContent.length > lastTextLength) {
      lastTextLength = textContent.length;
      stableCount = 0;
    } else {
      stableCount++;
      if (stableCount >= 3) break;
    }
  }

  await randomDelay(1000, 2000);

  const text = await page.evaluate(() => {
    const turns = document.querySelectorAll('.group.agent-turn, .group:has(.markdown)');
    const lastTurn = turns[turns.length - 1];
    if (lastTurn) return lastTurn.innerText;
    return document.body.innerText;
  });

  const imageUrls = await page.evaluate(() => {
    const imgs = document.querySelectorAll('img[alt*="Generated"], img.dalle-image, img[src*="oaidalleapiprodscus"]');
    return Array.from(imgs).slice(0, 10).map(img => img.src);
  });

  return {
    type: imageUrls.length > 0 ? 'image' : 'text',
    text,
    imageUrls,
    description: imageUrls.length > 0 ? `Generated image from ChatGPT (DALL·E 3)` : `Response from ChatGPT`
  };
}
