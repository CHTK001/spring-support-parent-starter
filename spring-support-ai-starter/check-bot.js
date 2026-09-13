const { chromium } = require('playwright');
async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto("http://localhost:19094/#/bots/users", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(3000);
  const bodyText = await page.locator("body").innerText();
  console.log("页面包含'二维码':", bodyText.includes("二维码"));
  console.log("页面包含'扫码':", bodyText.includes("扫码"));
  console.log("页面包含'绑定':", bodyText.includes("绑定"));
  console.log("页面包含'微信':", bodyText.includes("微信"));
  console.log("页面前300字:", bodyText.substring(0, 300));
  await page.screenshot({ path: "bot-management.png", fullPage: true });
  await browser.close();
}
main().catch(console.error);
