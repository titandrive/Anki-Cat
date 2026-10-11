// SPDX-License-Identifier: GPL-3.0-or-later
// Run with Node.js and Playwright Chromium installed. No phone is used.
const {chromium} = require('playwright');
const fs = require('fs');
const assert = require('node:assert/strict');
const root = require('node:path').resolve(__dirname, '../AnkiDroid/src/main');
(async () => {
 const browser = await chromium.launch({headless:true, args:['--no-sandbox']});
 try {
  const page = await browser.newPage();
  const font = fs.readFileSync(root+'/assets/fonts/JetBrainsMono-Regular.ttf');
  await page.route('http://ankicat.test/_ankicat/font.ttf*',route=>route.fulfill({contentType:'font/ttf',body:font}));
  const face="@font-face{font-family:AnkiCatCustom;src:url('/_ankicat/font.ttf')}";
  const original='.card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }';
  const markup='<body class="card"><div id="plain">Example</div><span id="explicit" style="font-family:Georgia!important;font-weight:700;font-style:italic">Explicit</span></body>';
  let html=`<style>${face}:where(body){font-family:AnkiCatCustom}${original}</style>${markup}`;
  await page.route('http://ankicat.test/',route=>route.fulfill({contentType:'text/html',body:html}));
  await page.goto('http://ankicat.test/');
  assert.equal(await page.locator('#plain').evaluate(n=>getComputedStyle(n).fontFamily),'arial');
  console.log('Original fallback reproduced: card remains Arial.');
  const source=fs.readFileSync(root+'/java/com/ichi2/anki/appearance/AppFonts.kt','utf8');
  const override=source.match(/body\.card, body\.card \* \{[^}]+\}/)[0];
  const script=fs.readFileSync(root+'/assets/scripts/ankicat-font.js','utf8');
  html=`<style>${face}${override}${original}</style><script>${script}</script>${markup}`;
  await page.reload();
  await page.evaluate(()=>document.fonts.ready);
  for (const id of ['plain','explicit']) assert.equal(await page.locator('#'+id).evaluate(n=>getComputedStyle(n).fontFamily),'AnkiCatCustom');
  assert.equal(await page.locator('#explicit').evaluate(n=>getComputedStyle(n).fontWeight),'700');
  assert.equal(await page.locator('#explicit').evaluate(n=>getComputedStyle(n).fontStyle),'italic');
  assert.equal(await page.evaluate(()=>[...document.fonts].find(f=>f.family==='AnkiCatCustom').status),'loaded');
  await page.evaluate(()=>{const n=document.createElement('div');n.id='dynamic';n.style.setProperty('font-family','Georgia','important');n.textContent='Dynamic';document.body.append(n);});
  await page.waitForFunction(()=>getComputedStyle(document.querySelector('#dynamic')).fontFamily==='AnkiCatCustom');
  await page.locator('#explicit').evaluate(n=>n.style.setProperty('font-family','Arial','important'));
  await page.waitForFunction(()=>getComputedStyle(document.querySelector('#explicit')).fontFamily==='AnkiCatCustom');
  console.log('Custom TTF loaded; card-wide, inline-important, and dynamic fonts overridden; bold and italic preserved.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
