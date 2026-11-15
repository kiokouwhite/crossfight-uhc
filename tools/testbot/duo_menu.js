// RWBY : l'item Ultime en Duo ouvre un menu de tous les ultimes en duo du personnage ; un clic sur celui qui est pret le lance.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let chat = []; bot.on('messagestr', m => chat.push(m));
const text = i => (JSON.stringify(i.customName || '').match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join('');
const lore = i => { const l = (i.components || []).find(c => c.type === 'lore'); return l ? (JSON.stringify(l.data).match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join(' ').replace(/\s+/g, ' ') : ''; };
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/cf change ruby'); await sleep(4000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/cf test duo'); await sleep(3000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  const s = bot.inventory.slots.slice(36, 45).findIndex(i => i && text(i).includes('Ultime en Duo'));
  bot.setQuickBarSlot(s); await sleep(300); bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(800);
  const w = bot.currentWindow;
  console.log('menu :', w ? JSON.stringify(w.title).replace(/[^A-Za-z ]/g, '').slice(-20) : 'aucun');
  if (w) { const items = w.slots.slice(0, 9).map((i, n) => i ? [n, i] : null).filter(Boolean);
    for (const [n, i] of items) console.log('  case', n, ':', i.name, '|', text(i), '|', lore(i).slice(0, 130));
    const ready = items.find(([n, i]) => /Pret/.test(lore(i)));
    if (ready) { chat = []; await bot.clickWindow(ready[0], 0, 0); await sleep(1000); console.log('clic sur le pret :', chat.filter(m => /lancent/.test(m)).pop()); } }
  bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
