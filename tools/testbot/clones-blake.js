const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
const V = require('vec3').Vec3;
const clones = () => Object.values(bot.entities).filter(e => e.name === 'mannequin');
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  await bot.look(0, 0, true); bot.chat('/cf change blake'); await sleep(2000);
  bot.chat('/cf mannequin'); await sleep(500); bot.chat('/tp @e[type=zombie] 0 65 -12'); await sleep(800);
  bot.setQuickBarSlot(1); await sleep(200); bot.activateItem();
  for (let i = 1; i <= 6; i++) { await sleep(500); const c = clones(); log('t+' + (i * 0.5) + 's clones :', c.length, c.map(e => 'z=' + e.position.z.toFixed(1)).join(' ')); }
  // le mannequin (armure cachee ?) : on regarde l'equipement vu par le client pour le bot lui-meme n'est pas possible ; on verifie juste les clones
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
