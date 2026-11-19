// La roulette d'Anna (/cf roulette) : la roue tourne, le lot (3 a 10 pommes dorees) arrive ; un second essai est refuse.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let win = null, said = []; bot.on('windowOpen', w => { win = w; });
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode survival'); bot.chat('/clear'); await sleep(800);
  const pommes = () => bot.inventory.items().filter(i => i.name === 'golden_apple').reduce((s, i) => s + i.count, 0);
  win = null; bot.chat('/cf roulette'); await sleep(1200);
  if (!win) { console.log('pas de roulette |', said.slice(-1)[0]); process.exit(1); }
  const rangee = () => [18, 19, 20, 21, 22, 23, 24, 25, 26].map(s => bot.currentWindow && bot.currentWindow.slots[s] ? bot.currentWindow.slots[s].count : '.').join(' ');
  console.log('depart   :', rangee());
  await sleep(2000); console.log('en route :', rangee(), '| pommes', pommes());
  await sleep(7000); console.log('arret    :', rangee(), '| pommes', pommes(), '|', said.filter(t => /Roulette/.test(t)).join(' '));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(800); console.log('menu ferme : pommes dans l inventaire', pommes());
  win = null; said = []; bot.chat('/cf roulette'); await sleep(1200);
  console.log('second essai :', win ? 'roulette ouverte (!)' : 'refuse |', said.join(' ').slice(0, 120));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
