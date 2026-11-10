const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|gold|lightpurple|darkred|darkpurple|extra|text|color|value|type|,/g, ' ').replace(/\s+/g, ' ')));
const raw = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.slice(-120) : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 170) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const hotbar = () => bot.inventory.slots.slice(36, 45).map((s, i) => i + ':' + (s ? s.name : '-')).join(' ');
async function mann() { bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(700); const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 2) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); return z; }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();

  // ----------------------------------------------------------------- YUKI
  bot.chat('/cf change yuki'); await sleep(2000); log('YUKI hotbar', hotbar());
  let z = await mann();
  const hp0 = bot.health; await click(0, 700); log('bleeding snow : vie', hp0.toFixed(1), '->', bot.health.toFixed(1), '|', raw(/Bleeding/));
  await click(3, 400); log('insaisissable ->', raw(/Insaisissable/), '| pos', bot.entity.position.floored().toString());
  bot.chat('/damage @s 12'); await sleep(600); log('vie', bot.health.toFixed(1), '| souffrante bar', raw(/Souffrante/));
  await click(4, 600); log('desesperee ->', raw(/Desesperee/));
  bot.chat('/damage @s 40'); await sleep(700); log('coup fatal -> vie', bot.health.toFixed(1), '|', lastChat(/Insatiable/), '| bar', raw(/INSATIABLE/));
  bot.chat('/heal'); bot.chat('/cf heal'); await sleep(500);
  bot.chat('/kill @e[type=zombie]'); await sleep(300); z = await mann();
  await click(8, 800); log('ult yuki ->', lastChat(/utilise/), '| max vie', bot.health.toFixed(1));
  log('burst bar', raw(/Burst|BURST/));
  await click(5, 1200); log('burst Monstre ->', lastChat(/Burst|Monstre/), '| mannequin vie', await zHealth(), '| yuki vie', bot.health.toFixed(1));
  bot.chat('/cf heal'); await sleep(300);

  // -------------------------------------------------------------- DRIFTER
  bot.chat('/cf change drifter'); await sleep(2000); log('DRIFTER hotbar', hotbar());
  z = await mann();
  await click(1, 900); log('pistolet tir -> bar', raw(/Pistol|Zeliska|balle|Munitions/), '| mannequin', await zHealth());
  bot.setQuickBarSlot(1); await sleep(100); bot.setControlState('sneak', true); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(200); bot.setControlState('sneak', false); await sleep(300); log('switch arme ->', raw(/Pistol|Zeliska|Shotgun|Blunder|Railgun/));
  await click(2, 300); await click(2, 800); log('chain dash ->', raw(/Dash|dash/), '| pos', bot.entity.position.floored().toString());
  await click(3, 1800); log('grenade ->', raw(/Grenade/), '| mannequin', await zHealth());
  bot.chat('/cf sante 30'); await sleep(400); log('sante 30 ->', lastChat(/Sante/), '| bar', raw(/Sante/));
  bot.chat('/cf sante 100'); await sleep(300);
  await click(8, 1500); log('ult drifter ->', lastChat(/utilise/), '| mannequin', await zHealth());
  await click(5, 1500); log('burst Monde en Ruine ->', lastChat(/Monde en Ruine|Burst/), '| monde', bot.game.dimension, '| pos', bot.entity.position.floored().toString());
  await sleep(500); bot.chat('/kill @e[type=zombie]'); await sleep(2500); log('apres kill des ennemis ->', lastChat(/Monde en Ruine/), '| monde', bot.game.dimension);

  // ---------------------------------------------------------------- SHOJA
  bot.chat('/cf change shoja'); await sleep(2000); log('SHOJA hotbar', hotbar());
  bot.chat('/cf shoja liste'); await sleep(500); log('liste ->', lastChat(/Feu/));
  bot.chat('/cf shoja inflammation'); await sleep(500); bot.chat('/cf shoja boule_electrifiante'); await sleep(500); bot.chat('/cf shoja panne_dhorloge'); await sleep(500);
  log('equip ->', lastChat(/equipee/), '| hotbar', hotbar(), '| bar', raw(/Arbre/));
  bot.chat('/cf shoja lame_ardente'); await sleep(500); log('4e (Feu remplace) ->', lastChat(/equipee|retiree|Trois/), '| hotbar', hotbar());
  z = await mann();
  // Lame ardente : chaque coup enflamme
  bot.setQuickBarSlot(0); for (let i = 0; i < 2; i++) { bot.attack(z); await sleep(600); } await sleep(300);
  chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Fire'); await sleep(300); log('lame ardente -> Fire', lastChat(/entity data/).replace(/.*data: /, ''));
  const slotOf = id => { const i = bot.inventory.slots.slice(36, 45).findIndex(s => s && (s.nbt ? JSON.stringify(s.nbt) : '').includes(id)); return i; };
  const iBoule = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'glowstone_dust');
  const iPanne = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'clock');
  log('slots boule', iBoule, 'panne', iPanne);
  await bot.lookAt(z.position.offset(0, 1, 0), true); await click(iBoule, 1200); log('boule electrifiante ->', lastChat(/Immobilise|immobilis/i), '| bars', raw(/Immobilise/));
  await click(iPanne, 800); log('panne d horloge ->', raw(/gele/i));
  bot.chat('/cf shoja raz'); await sleep(400); bot.chat('/cf shoja division_du_territoire'); await sleep(300); bot.chat('/cf shoja rafale_de_vent'); await sleep(300); bot.chat('/cf shoja back_to_back'); await sleep(500);
  log('hotbar', hotbar());
  const iDiv = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'iron_shovel');
  const iRaf = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'feather');
  const iBtb = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'recovery_compass');
  const zp = z.position.clone(); await bot.lookAt(z.position.offset(0, 1, 0), true);
  await click(iRaf, 900); log('rafale -> mannequin bouge de', zomb() ? zomb().position.distanceTo(zp).toFixed(1) : '?', 'blocs');
  await bot.look(bot.entity.yaw, 0, true); await click(iDiv, 1200);
  const ahead = bot.entity.position.offset(-Math.sin(bot.entity.yaw) * 3, -1, -Math.cos(bot.entity.yaw) * 3);
  log('division -> bloc devant a y-1 :', bot.blockAt(ahead) ? bot.blockAt(ahead).name : '?', '| y-5 :', bot.blockAt(ahead.offset(0, -4, 0)) ? bot.blockAt(ahead.offset(0, -4, 0)).name : '?');
  bot.chat('/damage @s 10'); await sleep(600); const before = bot.health; await click(iBtb, 800); log('back to back : vie', before.toFixed(1), '->', bot.health.toFixed(1));
  bot.chat('/cf heal'); await sleep(300);
  await click(8, 1200); log('ult (>5 coeurs) ->', lastChat(/utilise/), '| bar', raw(/Couronne/), '| allowFlight?', bot.entity.position.y.toFixed(1));
  bot.chat('/damage @s 12'); await sleep(500); await click(8, 1200); log('ult (<=5 coeurs) ->', raw(/Araignee/));
  bot.chat('/cf heal'); await sleep(500);
  await click(5, 1200); log('burst (>5) ->', lastChat(/Burst|Dieu/));
  await sleep(10500); log('10 s plus tard ->', lastChat(/Dieu du monde/));
  bot.chat('/damage @s 12'); await sleep(500); await click(5, 1200); log('burst (<=5) ->', lastChat(/Dieu des enfers/));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
