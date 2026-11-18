// Le marchand chez Anna (Central Town) : avant la quete elle ne vend rien ; la quete finie, lui parler ouvre l'etalage.
// node marchand_anna.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let win = null, said = []; bot.on('windowOpen', w => { win = w; });
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier central_town'); await sleep(500);
  bot.chat('/cf quete tp central_town'); await sleep(5000);
  console.log('bornes pres d\'Anna :', Object.values(bot.entities).filter(e => e.name === 'interaction' && e.position.distanceTo(bot.entity.position) < 30).length);
  const anna = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
  win = null; bot.useOn(anna); await sleep(1500);
  console.log('avant la quete :', win ? 'etalage ouvert (!)' : 'pas d\'etalage');
  bot.chat('/cf quete finir central_town'); await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('remerciement :', (said.filter(t => /boutique/.test(t)).pop() || '').slice(-140));
  win = null; bot.useOn(anna); await sleep(1500);
  console.log('apres la quete :', win ? 'etalage "' + flat(win.title) + '", objets aux cases ' + [14, 16, 32, 34].filter(s => win.slots[s]).join(' ') : 'pas d\'etalage');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
