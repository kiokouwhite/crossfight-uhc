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
async function zData(k) { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] ' + k); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const hotbar = () => bot.inventory.slots.slice(36, 45).map((s, i) => i + ':' + (s ? s.name : '-')).join(' ');
const slotNamed = n => bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === n);
async function mann(d) { bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(700); const z = zomb(); bot.chat('/tp @s ' + (z.position.x + d) + ' ' + z.position.y + ' ' + z.position.z + ' -90 0'); await sleep(400); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); return z; }
async function equip(...ids) { bot.chat('/cf shoja raz'); await sleep(300); for (const id of ids) { bot.chat('/cf shoja ' + id); await sleep(300); } await sleep(300); }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change shoja'); await sleep(2000); log('SHOJA hotbar', hotbar());
  let z = await mann(2.5);
  await equip('boule_electrifiante', 'panne_dhorloge', 'pistolet_a_eau'); log('hotbar', hotbar());
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('glowstone_dust'), 1500); log('boule ->', raw(/Immobilise|aucun/i), '| zombie speed attr?', await zData('attributes'));
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('glass_bottle'), 1500); log('pistolet ->', await zData('active_effects'));
  await click(slotNamed('clock'), 800); log('panne ->', raw(/gele/i));
  await equip('explosion_infernale', 'rayon_electrique', 'vent_tranchant');
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('end_rod'), 1000); log('rayon ->', raw(/Immobilise|aucun/i), '| vie', await zHealth());
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('iron_sword'), 900); log('vent tranchant -> vie', await zHealth());
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('tnt'), 2500); log('explosion infernale -> vie', await zHealth(), '| Fire', await zData('Fire'));
  await equip('mine_sousmarine', 'soulevement_terrestre', 'boom_temporel'); log('hotbar', hotbar());
  await bot.lookAt(bot.entity.position.offset(5, 1.6, 0), true); await sleep(200);
  await click(slotNamed('heart_of_the_sea'), 500); log('mine ->', raw(/Mine/));
  bot.chat('/tp @e[type=zombie] ' + (bot.entity.position.x + 2) + ' ' + bot.entity.position.y + ' ' + bot.entity.position.z); await sleep(1800); log('mine sur mannequin -> vie', await zHealth(), 'effets', await zData('active_effects'));
  await click(slotNamed('dirt'), 1000);
  const ahead = bot.entity.position.offset(3, 1, 0); log('mur -> bloc a 3 devant :', bot.blockAt(ahead) ? bot.blockAt(ahead).name : '?', '| +9 haut', bot.blockAt(ahead.offset(0, 9, 0)) ? bot.blockAt(ahead.offset(0, 9, 0)).name : '?');
  await click(slotNamed('repeater'), 1000); log('boom temporel ->', raw(/gele/i));
  await equip('nouvelle_dimension', 'rafale_de_vent', 'armure_de_vent'); log('hotbar', hotbar());
  await click(slotNamed('end_stone'), 1500); log('nouvelle dimension ->', lastChat(/Dimension|aucun/), '| mannequin visible ?', !!zomb());
  bot.chat('/damage @s 4'); await sleep(500); log('armure de vent bar', raw(/Armure de Vent/), '| vie', bot.health.toFixed(1));
  await equip('incapacitation', 'ding_dong', 'epine_de_mer'); log('hotbar', hotbar());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(700); z = zomb(); bot.chat('/tp @s ' + (z.position.x + 2) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1.2, 0), true);
  bot.setQuickBarSlot(0); await sleep(100); bot.chat('/kill @e[type=zombie]'); await sleep(800); log('epee de l ego apres kill ->', lastChat(/Ego/));
  bot.chat('/cf mannequin'); await sleep(700); z = zomb(); bot.chat('/tp @s ' + (z.position.x + 2) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1.2, 0), true);
  bot.chat('/damage @s 10'); await sleep(500); const before = bot.health; await click(slotNamed('bell'), 900); log('ding dong : vie', before.toFixed(1), '->', bot.health.toFixed(1), '| mannequin', await zHealth());
  await click(slotNamed('structure_void'), 800); log('incapacitation ->', raw(/Incapacitation|aucun/));
  // ultime et burst selon la vie
  bot.chat('/cf heal'); await sleep(300); bot.chat('/damage @s 12'); await sleep(500); log('vie', bot.health.toFixed(1)); await click(8, 1200); log('ult (<=5 coeurs) ->', raw(/Araignee|Couronne/));
  await sleep(2500); log('2.5 s plus tard vie', bot.health.toFixed(1));
  bot.chat('/cf heal'); await sleep(300); bot.chat('/damage @s 12'); await sleep(500); await click(2, 1200); log('burst (<=5) ->', lastChat(/Dieu des enfers|Burst/), '| max vie bar', bot.health.toFixed(1));
  bot.chat('/cf heal'); await sleep(500); log('apres heal, vie', bot.health.toFixed(1));
  bot.chat('/cf change yuki'); await sleep(1500); bot.chat('/cf change shoja'); await sleep(1500); log('apres changement vie', bot.health.toFixed(1), '| hotbar', hotbar());
  await click(2, 1200); log('burst (>5) ->', lastChat(/Dieu du monde|Burst/), '| bar', raw(/Dieu du monde/));
  await sleep(10500); log('10 s plus tard ->', lastChat(/vie/));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
