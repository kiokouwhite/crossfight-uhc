const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
let sounds = [];
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') { const m = JSON.stringify(data).match(/crossfight:[a-z0-9_.]+/); if (m) sounds.push(m[0].replace('crossfight:', '')); }
});
const take = () => { const s = {}; for (const x of sounds) s[x] = (s[x] || 0) + 1; sounds = []; const k = Object.keys(s); return k.length ? k.map(x => x + (s[x] > 1 ? ' x' + s[x] : '')).join(', ') : '(rien)'; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function setup(c, kind) { bot.chat('/cf change ' + c); await sleep(1300); bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(250); bot.chat('/cf mannequin' + (kind ? ' ' + kind : '')); await sleep(700); const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(350); await bot.lookAt(z.position.offset(0, 1.2, 0), true); take(); return z; }
async function hit(n) { bot.setQuickBarSlot(0); for (let i = 0; i < n; i++) { const z = zomb(); if (z) { await bot.lookAt(z.position.offset(0, 1.2, 0), true); bot.attack(z); } await sleep(620); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  await setup('eirika'); await hit(6); log('eirika, 6 coups ->', take());
  await setup('seraphina'); await hit(7); log('seraphina, 7 coups ->', take());
  await setup('yuki'); bot.chat('/damage @s 40'); await sleep(800); log('yuki, coup fatal ->', take());
  await setup('hannah', 'attaque'); await sleep(13000); log('hannah, 13 s sous les coups ->', take()); bot.chat('/cf heal');
  await setup('kishi', 'attaque'); await sleep(9000); log('kishi, 9 s sous les coups ->', take()); bot.chat('/cf heal');
  await setup('drifter'); await click(1, 700); await click(1, 700); log('drifter, 2 tirs ->', take());
  await setup('ruby'); await click(0, 800); log('ruby, crescent rose ->', take());
  await setup('gunvolt'); await click(0, 800); log('gunvolt, cerbere ->', take());
  await setup('netsu'); await click(2, 500); await click(2, 500); log('netsu, 2 dashs ->', take());
  await setup('kaze'); await sleep(1500); log('kaze, seul face a un ennemi ->', take()); await click(2, 800); log('kaze, prise en traitre ->', take());
  await setup('neon'); await click(4, 600); log('neon, boutique (menu, doit rester muet) ->', take()); bot.closeWindow && bot.currentWindow && bot.closeWindow(bot.currentWindow);
  await setup('shoja'); await click(1, 600); log('shoja, arbre (menu, doit rester muet) ->', take());
  bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
