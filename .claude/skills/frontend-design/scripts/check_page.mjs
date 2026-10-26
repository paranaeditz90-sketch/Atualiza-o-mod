// Validação prática de uma página no Chromium já instalado no container (Playwright).
// Uso: node .claude/skills/frontend-design/scripts/check_page.mjs <arquivo.html | URL> [pasta_saida]
// Para cada largura (celular 360x780 e PC 1280x800), em tema claro e escuro:
//   - print da página inteira em <saida>/<largura>-<tema>.png
//   - rolagem horizontal (o corpo não pode ser mais largo que a tela)
//   - erros de console e exceções da página
//   - <title>, lang e <meta name="viewport" content="width=device-width...">
//   - imagens sem alt, botões/links sem nome acessível, campos sem rótulo
//   - alvos de toque menores que 24x24 px (WCAG 2.2 AA, 2.5.8) no celular
// Sai com código 1 se achar problema; imprime um resumo em JSON.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
let playwright;
try {
  playwright = require('playwright');
} catch {
  // O Playwright do container é global; o require relativo ao script não o acha.
  playwright = require(require('node:child_process')
    .execSync('npm root -g').toString().trim() + '/playwright');
}

const [, , target, outDir = 'page-check'] = process.argv;
if (!target) {
  console.error('uso: node check_page.mjs <arquivo.html | URL> [pasta_saida]');
  process.exit(2);
}
const url = /^https?:|^file:/.test(target) ? target : pathToFileURL(resolve(target)).href;
mkdirSync(outDir, { recursive: true });

const viewports = [
  { name: 'celular', width: 360, height: 780, mobile: true },
  { name: 'pc', width: 1280, height: 800, mobile: false },
];
const browser = await playwright.chromium.launch();
const report = { url, results: [], problems: 0 };

for (const vp of viewports) {
  for (const scheme of ['light', 'dark']) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      isMobile: vp.mobile, hasTouch: vp.mobile, colorScheme: scheme,
    });
    const page = await context.newPage();
    const errors = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.goto(url, { waitUntil: 'load' });
    await page.waitForTimeout(300);
    const shot = `${outDir}/${vp.name}-${scheme}.png`;
    await page.screenshot({ path: shot, fullPage: true });

    const found = await page.evaluate((isMobile) => {
      const name = (el) => (el.getAttribute('aria-label') || el.getAttribute('title') ||
        el.innerText || el.value || '').trim() ||
        [...el.querySelectorAll('img[alt]')].map((i) => i.alt).join(' ').trim();
      const visible = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
      const label = (el) => el.labels?.length || el.getAttribute('aria-label') || el.getAttribute('aria-labelledby');
      const doc = document.documentElement;
      const out = {
        horizontalOverflowPx: Math.max(0, doc.scrollWidth - doc.clientWidth),
        imagesWithoutAlt: [...document.images].filter((i) => !i.hasAttribute('alt')).map((i) => i.src.slice(-60)),
        unnamedControls: [...document.querySelectorAll('button, a[href], [role=button]')]
          .filter((e) => visible(e) && !name(e)).map((e) => e.outerHTML.slice(0, 80)),
        unlabeledFields: [...document.querySelectorAll('input:not([type=hidden]), select, textarea')]
          .filter((e) => visible(e) && !label(e)).map((e) => e.outerHTML.slice(0, 80)),
        smallTouchTargets: [],
        hasTitle: Boolean(document.title.trim()),
        hasLang: Boolean(doc.getAttribute('lang')),
        // Sem isso o celular desenha numa tela falsa de 980 px e tudo fica minúsculo.
        hasViewportMeta: /width=device-width/.test(
          document.querySelector('meta[name=viewport]')?.getAttribute('content') || ''),
      };
      if (isMobile) {
        out.smallTouchTargets = [...document.querySelectorAll('button, a[href], input, select, [role=button]')]
          .filter(visible)
          .filter((e) => { const r = e.getBoundingClientRect(); return r.width < 24 || r.height < 24; })
          .filter((e) => !(e.tagName === 'A' && getComputedStyle(e).display === 'inline'))
          .map((e) => e.outerHTML.slice(0, 80));
      }
      return out;
    }, vp.mobile);

    const problems = (found.horizontalOverflowPx > 0) + found.imagesWithoutAlt.length +
      found.unnamedControls.length + found.unlabeledFields.length + found.smallTouchTargets.length +
      errors.length + !found.hasTitle + !found.hasLang + !found.hasViewportMeta;
    report.problems += problems;
    report.results.push({ viewport: vp.name, scheme, screenshot: shot, consoleErrors: errors, ...found, problems });
    await context.close();
  }
}
await browser.close();
console.log(JSON.stringify(report, null, 2));
process.exit(report.problems ? 1 : 0);
