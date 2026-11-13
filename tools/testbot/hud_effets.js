// Bonus/malus sur les bords de l'ecran : objets invisibles (camera_overlay) dans les cases 5 et 7, et sons.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let slots = []; let full = 0; let sounds = [];
bot._client.on('packet', (d, m) => {
  if (m.name === 'set_slot' && d.windowId === 0 && (d.slot === 5 || d.slot === 7)) { const j = JSON.stringify(d.item); const o = j.match(/overlay\/[a-z_]+/); slots.push(d.slot + ':' + (o ? o[0] : (d.item && d.item.itemCount ? 'objet' : 'vide'))); }
  if (m.name === 'window_items' && d.windowId === 0) full++;
  if (m.name === 'sound_effect') sounds.push(JSON.stringify(d.sound || d.soundId).slice(0, 40));
});
const uniq = a => [...new Set(a)].join(' ');
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person weiss'); await sleep(5000);
  bot.chat('/effect clear Tester'); await sleep(1500);
  slots = []; sounds = [];
  bot.chat('/effect give Tester minecraft:speed 20 1'); await sleep(1200);
  console.log('vitesse :', uniq(slots), '| sons', sounds.length);
  slots = []; sounds = [];
  bot.chat('/effect give Tester minecraft:strength 20 0'); await sleep(1200);
  console.log('+ force :', uniq(slots), '| sons', sounds.length);
  slots = []; sounds = [];
  bot.chat('/effect give Tester minecraft:slowness 20 0'); await sleep(1200);
  console.log('+ lenteur :', uniq(slots), '| sons', sounds.length);
  slots = []; sounds = [];
  bot.chat('/effect give Tester minecraft:poison 20 0'); await sleep(1200);
  console.log('+ poison :', uniq(slots), '| sons', sounds.length);
  slots = []; full = 0;
  bot.chat('/effect clear Tester'); await sleep(1500);
  console.log('tout enleve :', uniq(slots) || '(plus rien)', '| inventaire renvoye', full, 'fois');
  slots = []; await sleep(1500);
  console.log('ensuite :', uniq(slots) || '(rien)');
  bot.chat('/cf test third-person'); await sleep(800); bot.quit(); process.exit(0);
});
