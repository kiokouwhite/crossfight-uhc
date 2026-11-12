const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:voice\.[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(17)); } });
const dim = async () => { msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(600); return (msgs.join(' ').match(/minecraft:[a-z_]+/) || ['?'])[0]; };
const near = () => { const out = {}; for (const e of Object.values(bot.entities)) { if (e === bot.entity) continue; if (e.position.distanceTo(bot.entity.position) > 20) continue; out[e.name] = (out[e.name] || 0) + 1; } return JSON.stringify(out); };
const partner = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 6);
const use = async item => { const i = [...Array(9).keys()].find(k => bot.inventory.slots[36 + k] && bot.inventory.slots[36 + k].name === item); if (i === undefined) return false; bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); return true; };
const LICS = { rwby: ['ruby', 'weiss', 'blake', 'yang'], bloody_snow: ['netsu', 'kaze', 'hannah', 'eirika'], shojas_memories: ['hiro', 'pinkou', 'aida', 'crystal'], gunvolt: ['gunvolt', 'acura', 'kirin', 'asimov'] };
bot.once('spawn', async () => {
  await sleep(2500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(800);
  // 1. Entree dans la zone duo.
  bot.chat('/cf change weiss'); await sleep(1500);
  t0 = Date.now(); msgs = []; bot.chat('/cf test duo'); await sleep(2500);
  log('zone duo : monde', await dim(), '| autour :', near());
  // 2. Clic droit sur le mannequin duo -> menu -> Ruby.
  const m = partner(); log('mannequin duo trouve :', !!m);
  if (m) { sounds = []; await bot.lookAt(m.position.offset(0, 1.5, 0), true); bot.activateEntity(m); await sleep(1200);
    const w = bot.currentWindow; log('menu :', w ? w.title.toString().slice(0, 80) : 'aucun', '|', w ? w.slots.filter((s, i) => s && i < w.inventoryStart).map(s => JSON.stringify(s.customName || s.name).replace(/[^A-Za-z ]/g, '').trim()).join(', ').slice(0, 160) : '');
    if (w) { const idx = w.slots.findIndex((s, i) => s && i < w.inventoryStart && /Ruby/.test(JSON.stringify(s.customName || s.nbt || ''))); await bot.clickWindow(idx >= 0 ? idx : 0, 0, 0); await sleep(7000); }
    log('apres le choix : voix entendues', sounds.join(', ') || '(aucune)'); }
  // 3. Les 24 ultimes en duo, dans les deux roles.
  const results = [];
  for (const [lic, chars] of Object.entries(LICS)) {
    bot.chat('/cf clear'); await sleep(1500);
    for (let i = 0; i < chars.length; i++) for (let j = 0; j < chars.length; j++) {
      if (i === j) continue;
      const a = chars[i], b = chars[j];
      bot.chat('/cf change ' + a); await sleep(1300); bot.chat('/cf test duo ' + b); await sleep(1300);
      msgs = []; const ok = await use(lic === 'rwby' ? 'amethyst_cluster' : 'netherite_scrap'); await sleep(1200);
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      const launched = msgs.find(x => / lancent /.test(x));
      results.push((launched ? 'OK ' : 'ECHEC ') + a + ' + mannequin ' + b + (launched ? ' : ' + launched.replace(/.* lancent /, '').replace(/ !$/, '') : ' | ' + (ok ? msgs.join(' ').slice(0, 120) : 'item introuvable')));
      bot.chat('/cf heal'); await sleep(300);
    }
  }
  console.log(results.join('\n'));
  // 4. Mort dans la zone duo, puis sortie.
  bot.chat('/kill @s'); await sleep(1500); bot._client.write('client_command', { actionId: 0 }); await sleep(2000);
  log('apres une mort : monde', await dim());
  bot.chat('/cf test duo'); await sleep(2000); log('sortie de la zone : monde', await dim(), '| mannequin duo encore la :', !!partner());
  bot.quit(); process.exit(0);
});
