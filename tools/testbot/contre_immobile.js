const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const walk = async ms => { const a = bot.entity.position.clone(); bot.setControlState('forward', true); bot.setControlState('jump', true); await sleep(ms); bot.setControlState('forward', false); bot.setControlState('jump', false); return bot.entity.position.distanceTo(a).toFixed(2); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(2500); bot.chat('/cf mannequin clear'); await sleep(300);
  log('sans Contre, 0,8 s de marche + saut : ', await walk(800), 'bloc(s)'); await sleep(600);
  await click(1, 50); log('pendant le Contre, 0,8 s : ', await walk(800), 'bloc(s)');
  await sleep(500); log('apres le Contre, 0,8 s : ', await walk(800), 'bloc(s)');
  // Contre reussi : Marth doit etre libre tout de suite.
  bot.chat('/cf mannequin attaque'); await sleep(900);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(500);
  let hit = false; bot._client.on('packet', (d, m) => { if (/action_bar|system_chat/.test(m.name) && /Contre reussi/.test(JSON.stringify(d))) hit = true; });
  for (let i = 0; i < 8 && !hit; i++) { bot.setQuickBarSlot(1); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); for (let k = 0; k < 20 && !hit; k++) await sleep(80); }
  bot.chat('/cf mannequin clear'); log('contre reussi :', hit, '| juste apres, 0,5 s de marche : ', await walk(500), 'bloc(s)');
  await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
