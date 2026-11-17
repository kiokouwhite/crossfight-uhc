// La boutique de potions d'Anna a Pearl : verrouillee par la quete, puis une potion au choix, puis le delai.
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
  bot.chat('/gamemode survival'); bot.chat('/clear'); bot.chat('/cf quete oublier pearl'); await sleep(500);
  bot.chat('/cf quete tp pearl'); await sleep(5000);
  const anna = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
  console.log('borne de potions :', Object.values(bot.entities).some(e => e.name === 'interaction' && e.position.distanceTo(bot.entity.position) < 10) ? 'encore la' : 'retiree');
  said = []; bot.useOn(anna()); await sleep(1500);
  console.log('avant la quete :', win ? 'boutique ouverte (!)' : 'pas de boutique |', said.join(' | ').slice(0, 90));
  bot.chat('/cf quete finir pearl'); await sleep(1000);
  win = null; bot.useOn(anna()); await sleep(1500);
  console.log('apres la quete :', win ? 'boutique "' + flat(win.title) + '" : ' + win.slots.slice(0, 9).map(s => s ? s.name : '-').join(' ') : 'pas de boutique');
  if (win) { await bot.clickWindow(4, 0, 0).catch(() => {}); await sleep(1200); }
  console.log('recu :', bot.inventory.items().map(i => i.name + ' x' + i.count).join(', ') || 'rien', '|', bar);
  win = null; bot.useOn(anna()); await sleep(1500);
  if (win) { bar = ''; await bot.clickWindow(0, 0, 0).catch(() => {}); await sleep(1200); }
  console.log('deuxieme essai :', bot.inventory.items().map(i => i.name + ' x' + i.count).join(', '), '|', bar);
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
