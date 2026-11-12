const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const pieces = () => Object.values(bot.entities).filter(e => e.name === 'item_display').length;
let looks = [], turns = 0;
bot._client.on('entity_metadata', p => { const m = JSON.stringify(p.metadata).match(/crossfight:book_[a-z0-9]+/); if (m) looks.push(m[0].slice(11)); });
bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const j = JSON.stringify(d); const x = j.match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); else if (/page_turn/.test(j) || d.soundId !== undefined && /book/.test(j)) turns++; } });
bot.on('message', m => { const t = m.toString(); if (/Tutorat/.test(t)) log('   message :', t.slice(0, 110)); });
const firstOption = w => w.slots.findIndex((it, i) => it && i < w.inventoryStart && !/glass_pane/.test(it.name));
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change byleth'); await sleep(4000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin allie'); await sleep(1200);
  t0 = Date.now(); log('--- Tutorat'); bot.setQuickBarSlot(2); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(1200); log('menu 1 :', bot.currentWindow ? 'ouvert' : 'ferme', '| pantin', pieces(), 'pieces | images du livre :', looks.join(' ')); looks = [];
  await sleep(2600); log('lecture : pantin', pieces(), '| images du livre :', looks.join(' ')); looks = [];
  let w = bot.currentWindow; if (w) { await bot.clickWindow(firstOption(w), 0, 0); await sleep(900); }
  log('menu 2 :', bot.currentWindow ? 'ouvert' : 'ferme', '| pantin', pieces());
  w = bot.currentWindow; if (w) { await bot.clickWindow(firstOption(w), 0, 0); await sleep(600); }
  log('lecon donnee | pantin', pieces());
  await sleep(1000); log('1 s apres | pantin', pieces()); await sleep(1300); log('2,3 s apres | pantin', pieces(), '| armure affichee chez le joueur', [5, 6, 7, 8].filter(i => bot.inventory.slots[i]).length);
  bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
