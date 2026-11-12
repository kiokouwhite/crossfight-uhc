const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const slot = i => { const it = bot.inventory.slots[i]; if (!it) return 'vide'; const m = JSON.stringify(it.components || it.nbt || '').match(/crossfight:hidden/); return it.name + (m ? ' (invisible)' : ''); };
const view = () => 'case 36 : ' + slot(36) + ' | case 37 : ' + slot(37) + ' | armure : ' + [5, 6, 7, 8].filter(i => bot.inventory.slots[i]).length + ' | modele : ' + Object.values(bot.entities).filter(e => e.name === 'item_display').length;
let healed = 0;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(5000); bot.chat('/damage @s 8'); await sleep(700);
  t0 = Date.now(); log('avant            |', view());
  bot.setQuickBarSlot(0); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(700);
  log('Falchion         |', view(), '| vie', bot.health.toFixed(1));
  // Pendant la pose, il passe sur le Contre : l'epee revient dans sa case, le Contre devient invisible, et le clic part quand meme.
  let contre = false; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name) && /ability\.marth_contre/.test(JSON.stringify(d))) contre = true; });
  bot.setQuickBarSlot(1); await sleep(350); log('passe en case 2  |', view());
  bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(500); log('clic sur Contre  |', view(), '| Contre declenche :', contre);
  await sleep(3500); log('apres            |', view());
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
