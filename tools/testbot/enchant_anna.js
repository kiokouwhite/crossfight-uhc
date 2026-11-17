// Le menu d'enchantement d'Anna a Garreg Mach : verrouille par la quete, puis un clic par piece, 5 niveaux chacune.
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
  bot.chat('/gamemode creative'); bot.chat('/clear'); bot.chat('/cf quete oublier garreg_mach'); await sleep(500);
  bot.chat('/cf quete tp garreg_mach'); await sleep(5000);
  for (const it of ['iron_chestplate', 'diamond_helmet', 'iron_sword', 'diamond_sword', 'bow', 'bread']) bot.chat('/give @s ' + it);
  bot.chat('/xp set @s 17 levels'); await sleep(1000); bot.chat('/cf quete liste'); await sleep(600); console.log(said.filter(t => /garreg/.test(t)).join(' ')); said = [];
  const anna = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
  console.log('borne d\'enchantement :', Object.values(bot.entities).some(e => e.name === 'interaction' && e.position.distanceTo(bot.entity.position) < 12) ? 'encore la' : 'retiree');
  bot.useOn(anna()); await sleep(1500);
  console.log('avant la quete :', win ? 'menu ouvert (!) ' + flat(win.title) : 'pas de menu', '|', said.slice(-3).join(' / ').slice(0, 300)); win = null;
  bot.chat('/cf quete finir garreg_mach'); await sleep(1000);
  const ench = () => bot.inventory.items().map(i => i.name + ' ' + JSON.stringify((i.components || []).filter(c => /enchant/.test(c.type)).map(c => c.data))).join(', ');
  for (let k = 0; k < 4; k++) {
    win = null; bot.useOn(anna()); await sleep(1200);
    if (!win) { console.log('pas de menu'); break; }
    if (k === 0) console.log('menu "' + flat(win.title) + '" :', win.slots.slice(0, 54).map((s, i) => s ? i + ':' + s.name : null).filter(Boolean).join(' '));
    bar = ''; await bot.clickWindow(k, 0, 0).catch(() => {}); await sleep(800);
    console.log('clic', k, '| niveaux', bot.experience.level, '|', bar);
    bot.closeWindow(win); await sleep(400);
  }
  console.log(ench());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
