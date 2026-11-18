// L'evenement des lieux : le bot impose Pearl renforce et Shiganshina affaibli, lit l'annonce, prend plusieurs potions
// chez Anna (une de chaque, pas deux fois la meme), puis inverse et verifie le delai, puis tire au hasard.
// node evenement_lieux.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], bar = '', win = null;
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) bar = t; else said.push(t); });
  bot.on('windowOpen', w => { win = w; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/clear'); await sleep(500);
  const potions = () => bot.inventory.items().filter(i => /potion/.test(i.name)).length;
  said = []; bot.chat('/cf evenement'); await sleep(600); console.log('au depart :', said.join(' | '));
  said = []; bot.chat('/cf evenement pearl shiganshina'); await sleep(800); console.log('annonce :', said.join(' | '));
  bot.chat('/cf quete finir pearl'); await sleep(800); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  win = null; said = []; bot.chat('/cf quete parler pearl'); await sleep(1200);
  if (!win) { console.log('pas de boutique |', said.join(' | ')); } else {
    console.log('boutique, avis :', said.join(' | '));
    for (const c of [5, 14, 23, 5]) { bar = ''; await bot.clickWindow(c, 0, 0).catch(() => {}); await sleep(700); console.log('  clic case', c, '-> potions :', potions(), '|', bar); }
    bot.closeWindow(win);
  }
  said = []; bot.chat('/cf evenement shiganshina pearl'); await sleep(800); console.log('inverse :', said.join(' | '));
  win = null; bot.chat('/cf quete parler pearl'); await sleep(1200);
  if (win) { bar = ''; await bot.clickWindow(32, 0, 0).catch(() => {}); await sleep(700); console.log('  affaibli, clic -> potions :', potions(), '|', bar); }
  bot.chat('/clear'); await sleep(300);
  said = []; bot.chat('/cf rythme'); await sleep(1000); console.log('forge renforcee, avis :', said.join(' | ')); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  said = []; bot.chat('/cf evenement tirer'); await sleep(800); console.log('tirage :', said.join(' | '));
  said = []; bot.chat('/cf evenement arreter'); await sleep(500); bot.chat('/cf evenement'); await sleep(600); console.log('arret :', said.join(' | '));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
