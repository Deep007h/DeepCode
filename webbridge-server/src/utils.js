const LOG_LEVELS = { error: 0, warn: 1, info: 2, debug: 3 };
const currentLevel = LOG_LEVELS[process.env.LOG_LEVEL] ?? LOG_LEVELS.info;

export const logger = {
  error: (...args) => currentLevel >= 0 && console.error(`[ERROR]`, ...args),
  warn: (...args) => currentLevel >= 1 && console.warn(`[WARN]`, ...args),
  info: (...args) => currentLevel >= 2 && console.log(`[INFO]`, ...args),
  debug: (...args) => currentLevel >= 3 && console.log(`[DEBUG]`, ...args)
};

export function randomDelay(min = 300, max = 900) {
  return new Promise(resolve => setTimeout(resolve, Math.floor(Math.random() * (max - min + 1)) + min));
}

export async function simulateMouseMove(page, element) {
  try {
    const box = await element.boundingBox();
    if (!box) return;

    const startX = box.x + Math.random() * box.width;
    const startY = box.y + Math.random() * box.height;
    const endX = box.x + box.width / 2 + (Math.random() - 0.5) * 10;
    const endY = box.y + box.height / 2 + (Math.random() - 0.5) * 10;
    const steps = 5 + Math.floor(Math.random() * 5);

    for (let i = 1; i <= steps; i++) {
      const t = i / steps;
      const x = startX + (endX - startX) * t + (Math.random() - 0.5) * 3;
      const y = startY + (endY - startY) * t + (Math.random() - 0.5) * 3;
      await page.mouse.move(x, y);
      await randomDelay(20, 50);
    }
  } catch {}
}

export function sanitizeForLogging(str) {
  if (!str) return '';
  return str.replace(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g, '[EMAIL REDACTED]');
}
