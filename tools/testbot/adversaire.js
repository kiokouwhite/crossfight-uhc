const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
bot.on('message', m => { const s = m.toString(); if (s.includes('invoque') || s.includes('Erreur')) log('CHAT', s); });
bot._client.on('action_bar', p => { const t = JSON.stringify(p).replace(/[^A-Za-z0-9%:., !]/g, ''); if (t.includes('contre toi') || t.includes('Aucun') || t.includes('Erreur')) log('BAR', t.replace(/.*value:/, '').slice(0, 90)); });
const row2 = () => [...Array(9)].map((_, i) => { const it = bot.inventory.slots[9 + i]; return it ? it.name : '-'; }).join(' ');
async function use(slot, label, waitMs) {
  const y0 = bot.entity.position.y, hp0 = bot.health;
  // deplacer l'item adverse du slot 9+i vers la barre d'action (slot 5) puis l'activer
  await bot.moveSlotItem(9 + slot, 36 + 5); await sleep(300);
  bot.setQuickBarSlot(5); await sleep(200);
  bot.activateItem();
  let maxY = y0, minHp = hp0;
  const start = Date.now();
  while (Date.now() - start < waitMs) { await sleep(100); if (bot.entity.position.y > maxY) maxY = bot.entity.position.y; if (bot.health < minHp) minHp = bot.health; }
  log(label, '-> hauteur max +' + (maxY - y0).toFixed(1), 'vie', hp0.toFixed(1), '->', minHp.toFixed(1), 'pos', bot.entity.position.toString());
  await bot.moveSlotItem(36 + 5, 9 + slot); await sleep(200);
}
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change marth'); await sleep(1500);
  await bot.look(0, 0, true);
  bot.chat('/cf mannequin adversaire byleth'); await sleep(1500);
  log('ligne 2 :', row2());
  await use(0, 'Epee du Createur (fouet)', 3000);
  bot.chat('/cf heal'); bot.chat('/tp @s 0 65 0'); await sleep(800);
  await use(1, 'Dechainement d Epine', 3000);
  bot.chat('/cf heal'); bot.chat('/tp @s 0 65 0'); await sleep(2500);
  bot.chat('/cf mannequin clear'); await sleep(500);
  bot.chat('/cf mannequin adversaire corrin'); await sleep(1500);
  log('ligne 2 :', row2());
  await use(0, 'Vagues Bleues', 5000);
  bot.chat('/cf heal'); bot.chat('/tp @s 0 65 0'); await sleep(1500);
  bot.chat('/cf mannequin clear'); await sleep(500);
  bot.chat('/cf mannequin adversaire takumi'); await sleep(1500);
  log('ligne 2 :', row2());
  await use(0, 'Rayon Demoniaque', 7000);
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
