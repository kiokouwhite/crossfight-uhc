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
async function myData(k) { chat = []; bot.chat('/data get entity @s ' + k); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const hotbar = () => bot.inventory.slots.slice(36, 45).map((s, i) => i + ':' + (s ? s.name : '-')).join(' ');
const slotNamed = n => bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === n);
async function mann(d) { bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(700); const z = zomb(); bot.chat('/tp @s ' + (z.position.x + d) + ' ' + z.position.y + ' ' + z.position.z + ' -90 0'); await sleep(400); await bot.lookAt(z.position.offset(0, 1.2, 0), true); await sleep(200); return z; }
async function equip(...ids) { bot.chat('/cf shoja raz'); await sleep(300); for (const id of ids) { bot.chat('/cf shoja ' + id); await sleep(300); } await sleep(300); }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ---- Drifter : Monde en Ruine
  bot.chat('/cf change drifter'); await sleep(2000); let z = await mann(2.5);
  await click(5, 1500); log('burst drifter ->', lastChat(/Burst/), '| Dimension', await myData('Dimension'), '| pos', await myData('Pos'));
  bot.chat('/kill @e[type=zombie]'); await sleep(2500); log('apres kill ->', lastChat(/Monde en Ruine/), '| Dimension', await myData('Dimension'));
  // ---- Shoja
  bot.chat('/cf change shoja'); await sleep(2000); z = await mann(2.5);
  await equip('vent_tranchant', 'cogito_ergo_sum', 'souffle_destructeur'); log('hotbar', hotbar());
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await click(slotNamed('golden_sword'), 900); log('vent tranchant -> vie', await zHealth());
  await click(slotNamed('armor_stand'), 900); log('souffle destructeur ->', raw(/Souffle/), '| armure mannequin', (chat = [], bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] equipment'), await sleep(300), lastChat(/entity data/).slice(0, 120)));
  const hp = bot.health; await click(slotNamed('sculk_sensor'), 1200); log('cogito -> vie', hp.toFixed(1), '->', bot.health.toFixed(1), '|', raw(/gele/), '| mannequin', await zHealth());
  // Epee de l'Ego : tuer un mannequin a 1 PV
  bot.chat('/data modify entity @e[type=zombie,limit=1,sort=nearest] Health set value 1.0f'); await sleep(300);
  bot.setQuickBarSlot(0); await sleep(100); bot.attack(z); await sleep(900); log('kill -> ego :', lastChat(/Ego/), '| zombie restant ?', !!zomb());
  // ult <= 5 coeurs
  bot.chat('/cf heal'); await sleep(300); bot.chat('/damage @s 18'); await sleep(500); log('vie', bot.health.toFixed(1)); await click(8, 1200); log('ult ->', raw(/Araignee|Couronne/));
  await sleep(2500); log('2.5 s plus tard vie', bot.health.toFixed(1));
  bot.chat('/cf heal'); await sleep(300); bot.chat('/damage @s 18'); await sleep(500); await click(2, 1200); log('burst (<=5) ->', lastChat(/Dieu des enfers/), '| max vie', await myData('attributes'));
  bot.chat('/cf heal'); await sleep(500); log('apres heal vie', bot.health.toFixed(1));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
