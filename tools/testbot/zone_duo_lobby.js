const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
// Repondre a la demande du pack, comme un joueur.
bot._client.on('add_resource_pack', d => { setTimeout(() => { try { bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }); log('pack : refus envoye'); } catch (e) { log('pack : ' + e.message); } }, 500); });
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const ask = async (cmd, ms = 900) => { msgs = []; bot.chat(cmd); await sleep(ms); return msgs.join(' | '); };
const near = () => { const out = {}; for (const e of Object.values(bot.entities)) { if (e === bot.entity || e.position.distanceTo(bot.entity.position) > 20) continue; out[e.name] = (out[e.name] || 0) + 1; } return JSON.stringify(out); };
const pick = async (re) => { const w = bot.currentWindow; if (!w) return 'pas de menu'; const idx = w.slots.findIndex((s, i) => s && i < w.inventoryStart && re.test(JSON.stringify(s.customName || s.nbt || s.components || ''))); await bot.clickWindow(idx >= 0 ? idx : 0, 0, 0); return idx >= 0 ? 'ok' : 'introuvable'; };
bot.once('spawn', async () => {
  await sleep(4000); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(800);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  if (bot.inventory.slots[43] && bot.inventory.slots[43].name === 'golden_apple') { bot.chat('/cf test'); await sleep(2500); }
  t0 = Date.now(); bot.chat('/cf test duo'); await sleep(4000);
  const w = bot.currentWindow; const n = w ? w.slots.filter((s, k) => s && k < w.inventoryStart).length : 0;
  log('entree depuis le lobby : menu', w ? 'ouvert' : 'aucun', '|', n, 'personnages proposes');
  if (w) { log('choix de Yang :', await pick(/Yang/)); await sleep(2500); }
  log('autour :', near(), '| mannequin duo :', (await ask('/data get entity @e[type=mannequin,limit=1,sort=nearest] CustomName')).replace(/.*has the following entity data: /, '').replace(/color: "[a-z_]+", |extra: |[\[\]{}]/g, '').slice(0, 120));
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
