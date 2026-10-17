// Optional developer check. Set NODE_PATH to a directory containing Playwright.
const { chromium } = require('playwright');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  const page = await browser.newPage({ viewport: { width: 1500, height: 1100 }, deviceScaleFactor: 1 });
  await page.goto(pathToFileURL(path.resolve(__dirname, '../docs/ezra-screens.html')).href);
  const previewNames = ['home','settings-menu','profile','voice','birthdays','medications','calendar','sources','installation'];
  for (let i=0; i<previewNames.length; i++) {
    await page.locator('.phone').nth(i).screenshot({path:path.resolve(__dirname, `../docs/preview-${previewNames[i]}.png`)});
  }
  await page.screenshot({ path: path.resolve(__dirname, '../docs/screens-light.png'), fullPage: true });
  await page.getByRole('button', { name: 'בהיר / כהה', exact: true }).click();
  await page.screenshot({ path: path.resolve(__dirname, '../docs/screens-dark.png'), fullPage: true });
  const failures = await page.locator('.phone').evaluateAll(phones => phones.filter(p => p.scrollWidth > p.clientWidth).length);
  if (failures) throw new Error('Horizontal overflow in phone previews');
  console.log(`${await page.locator('.phone').count()} screen previews captured in light and dark; no horizontal overflow.`);
  await browser.close();
})().catch(error => { console.error(error); process.exitCode = 1; });
