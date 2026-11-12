const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const count = n => Object.values(bot.entities).filter(e => e.name === n).length;
const armor = () => [5, 6, 7, 8].filter(i => bot.inventory.slots[i]).length;
const use = async slot => { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(6000); bot.chat('/cf mannequin clear'); await sleep(300);
  bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); } });
  // Contre lance par un clic : l'armure doit rester masquee chez le joueur pendant la pose.
  t0 = Date.now(); log('--- Contre'); await use(1);
  for (const t of [300, 400, 600, 500]) { await sleep(t); log('pieces du modele', count('item_display'), '| armure affichee chez le joueur', armor()); }
  // Coup Critique face a un mannequin a 6 blocs.
  bot.chat('/cf mannequin'); await sleep(900);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 6) + ' ' + z.position.y + ' ' + z.position.z); await sleep(600); await bot.lookAt(z.position.offset(0, 1.2, 0), true); await sleep(300);
  const start = bot.entity.position.clone();
  t0 = Date.now(); log('--- Coup Critique'); await use(8);
  for (let i = 0; i < 14; i++) { await sleep(200); log('avance', bot.entity.position.distanceTo(start).toFixed(1).padStart(4), 'blocs | pieces', count('item_display'), '| barre de vie', count('text_display'), '| armure affichee', armor()); }
  bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
