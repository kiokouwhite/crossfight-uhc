const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:voice\.[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(17)); } });
const ask = async (cmd, ms = 900) => { msgs = []; bot.chat(cmd); await sleep(ms); return msgs.join(' | '); };
const near = () => { const out = {}; for (const e of Object.values(bot.entities)) { if (e === bot.entity || e.position.distanceTo(bot.entity.position) > 20) continue; out[e.name] = (out[e.name] || 0) + 1; } return JSON.stringify(out); };
const partner = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 6);
const isTester = () => bot.inventory.slots[43] && bot.inventory.slots[43].name === 'golden_apple';
const pick = async (re) => { const w = bot.currentWindow; if (!w) return 'pas de menu'; const idx = w.slots.findIndex((s, i) => s && i < w.inventoryStart && re.test(JSON.stringify(s.customName || s.nbt || s.components || ''))); await bot.clickWindow(idx >= 0 ? idx : 0, 0, 0); return idx >= 0 ? 'ok' : 'introuvable'; };
bot.once('spawn', async () => {
  await sleep(2500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(800);
  if (!isTester()) { bot.chat('/cf test'); await sleep(3000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }
  bot.chat('/cf change weiss'); await sleep(1500);
  // Entree dans la zone duo avec Weiss.
  t0 = Date.now(); bot.chat('/cf test duo'); await sleep(4000);
  log('monde :', (await ask('/data get entity @s Dimension')).match(/minecraft:[a-z_]+/)?.[0], '| autour :', near());
  const prof = await ask('/data get entity @e[type=mannequin,limit=1,sort=nearest] profile');
  log('profil du mannequin duo :', /textures/.test(prof) ? 'skin present (' + (prof.match(/name: "([^"]+)"/) || [])[1] + ')' : prof.slice(0, 160));
  log('nom :', (await ask('/data get entity @e[type=mannequin,limit=1,sort=nearest] CustomName')).replace(/.*has the following entity data: /, '').slice(0, 140));
  // Clic droit sur le mannequin -> Ruby.
  const m = partner(); if (!m) { log('mannequin introuvable'); bot.quit(); process.exit(1); }
  await bot.lookAt(m.position.offset(0, 1.5, 0), true); bot.activateEntity(m); await sleep(1200);
  log('menu ouvert :', bot.currentWindow ? bot.currentWindow.title.toString().replace(/[{}"\\]/g, '').slice(0, 90) : 'aucun');
  sounds = []; t0 = Date.now(); log('choix de Ruby :', await pick(/Ruby/)); await sleep(8000);
  log('voix apres le choix :', sounds.join(', ') || '(aucune)');
  log('nom :', (await ask('/data get entity @e[type=mannequin,limit=1,sort=nearest] CustomName')).replace(/.*has the following entity data: /, '').slice(0, 140));
  // Ultime en duo Weiss + mannequin Ruby.
  sounds = []; t0 = Date.now(); msgs = [];
  const i = [...Array(9).keys()].find(k => bot.inventory.slots[36 + k] && bot.inventory.slots[36 + k].name === 'amethyst_cluster'); bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(3000);
  log('Ultime en duo :', msgs.filter(x => /lancent/.test(x)).join(' '), '| voix :', sounds.join(', '));
  // Entree depuis l'exterieur du mode test : menu des personnages a ultime en duo.
  bot.chat('/cf test'); await sleep(2500);
  t0 = Date.now(); bot.chat('/cf test duo'); await sleep(4000);
  const w = bot.currentWindow; const names = w ? w.slots.filter((s, k) => s && k < w.inventoryStart).length : 0;
  log('entree depuis le lobby : menu', w ? w.title.toString().replace(/[{}"\\]/g, '').slice(0, 90) : 'aucun', '|', names, 'personnages proposes');
  if (w) { log('choix de Yang :', await pick(/Yang/)); await sleep(2500); }
  log('autour apres le choix :', near(), '| mannequin duo :', (await ask('/data get entity @e[type=mannequin,limit=1,sort=nearest] CustomName')).replace(/.*has the following entity data: /, '').slice(0, 120));
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
