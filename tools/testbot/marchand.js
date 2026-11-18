// Le marchand de Central Town (/cf marchand) : quatre objets de lootbox differents, leur prix en diamants selon le tier,
// un achat (les diamants partent, l'objet arrive), un second achat du meme refuse, un nouvel etalage.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let win = null; bot.on('windowOpen', w => { win = w; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode survival'); bot.chat('/clear'); await sleep(500); bot.chat('/give @s diamond 40'); await sleep(800);
  const lore = it => { const c = (it.components || []).find(c => c.type === 'lore'); return c ? c.data.map(flat) : []; };
  const nom = it => { const c = (it.components || []).find(c => c.type === 'custom_name'); return c ? flat(c.data) : it.name; };
  const diamants = () => bot.inventory.items().filter(i => i.name === 'diamond').reduce((s, i) => s + i.count, 0);
  const etalage = async (cmd) => { win = null; bot.chat(cmd); await sleep(1200); if (!win) return []; return [10, 12, 14, 16].map(s => win.slots[s]).filter(Boolean).map(it => ({ nom: nom(it), tier: (lore(it).find(l => /^Tier/.test(l)) || ''), prix: (lore(it).find(l => /Prix|Déjà/.test(l)) || '') })); };
  let e = await etalage('/cf marchand');
  console.log('etalage :'); e.forEach(o => console.log('  ', o.nom, '|', o.tier, '|', o.prix));
  console.log('quatre differents :', new Set(e.map(o => o.nom)).size === 4, '| horloge :', win && win.slots[22] ? nom(win.slots[22]) : 'absente');
  const avant = diamants(); await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000);
  console.log('achat du premier : diamants', avant, '->', diamants(), '| objets :', bot.inventory.items().filter(i => i.name !== 'diamond').map(nom).join(', '));
  const mid = diamants(); await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000);
  console.log('second achat du meme : diamants', mid, '->', diamants(), '|', bot.currentWindow && bot.currentWindow.slots[10] ? lore(bot.currentWindow.slots[10]).find(l => /Prix|Déjà/.test(l)) : '');
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const e2 = await etalage('/cf marchand nouveau');
  console.log('nouvel etalage :', e2.map(o => o.nom + ' (' + o.tier + ')').join(', '));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  // les chances : vingt etalages de plus, on compte les tiers
  const compte = { 1: 0, 2: 0, 3: 0, 4: 0 };
  for (let k = 0; k < 20; k++) { const x = await etalage('/cf marchand nouveau'); x.forEach(o => compte[o.tier.replace(/\D/g, '')]++); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }
  console.log('tiers sur 80 objets :', JSON.stringify(compte));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
