const { chromium } = require(process.env.PW);
(async () => {
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 1920, height: 960 } });
  await pg.goto('file://' + __dirname + '/art.html');
  await pg.waitForTimeout(1500);
  await (await pg.$('#icon')).screenshot({ path: 'icon-512.png', omitBackground: true });
  await (await pg.$('#thumb')).screenshot({ path: 'thumbnail.png' });
  for (const s of ['s1', 's2', 's3']) await (await pg.$('#' + s)).screenshot({ path: `gallery-${s.slice(1)}.png` });
  await b.close();
})();
