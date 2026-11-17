// Le menu d'enchantement d'Anna a Garreg Mach : on clique l'objet, puis on choisit le niveau (1, 2 ou 5 niveaux).
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
  // un essai : cliquer l'objet (case de la fenetre), puis un niveau (29 : I, 31 : II, 33 : III)
  const essai = async (objet, niveau, nom) => {
    win = null; bot.useOn(anna()); await sleep(1200);
    if (!win) { console.log(nom, ': pas de menu'); return; }
    const premier = flat(win.title), w1 = win;
    win = null; await bot.clickWindow(objet, 0, 0).catch(() => {}); await sleep(1200);
    if (!win) { console.log(nom, ': "' + premier + '" -> pas de second menu |', bar); bot.closeWindow(w1); await sleep(300); return; }
    const choix = [13, 29, 31, 33].map(i => win.slots[i] ? win.slots[i].name : '-').join(' ');
    bar = ''; await bot.clickWindow(niveau, 0, 0).catch(() => {}); await sleep(900);
    console.log(nom, ': "' + premier + '" -> "' + flat(win.title) + '" [' + choix + '] | niveaux', bot.experience.level, '|', bar);
    if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  };
  await essai(81, 33, 'plastron de fer, III');
  await essai(82, 33, 'casque de diamant, III');
  await essai(82, 31, 'casque de diamant, II');
  await essai(83, 29, 'epee de fer, I');
  await essai(86, 29, 'pain');
  console.log(ench());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
