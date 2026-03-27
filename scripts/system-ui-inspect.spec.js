const { test } = require('playwright/test');

test('inspect sync data system ui', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto('http://127.0.0.1:18080/', { waitUntil: 'networkidle' });
  console.log('TITLE=' + await page.title());
  console.log('URL=' + page.url());
  const bodyText = await page.locator('body').innerText();
  console.log('BODY_START');
  console.log(bodyText.slice(0, 4000));
  console.log('BODY_END');
  await page.screenshot({
    path: 'h:/workspace/2/spring-support-parent-starter/system-ui-inspect.png',
    fullPage: true
  });
});
