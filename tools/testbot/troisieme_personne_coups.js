// Troisieme personne : frapper le mannequin (sans recul, sans se faire frapper), Contre, et l'ordre de frappe accroupi.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const hpOf = async () => { msgs = []; bot.chat('/data get entity @e[tag=cf_mannequin_pilote,limit=1] Health'); await sleep(600); return parseFloat((msgs.join(' ').match(/([0-9.]+)f/) || [0, 'NaN'])[1]); };
const posOf = () => { const m = body(); return m ? m.position.clone() : null; };
const place = async () => { bot.chat('/tp @s 0.5 65 3 180 0'); bot.chat('/cf heal'); await sleep(700); };
bot.once('spawn', async () => {
  await sleep(4000);
  if (!body()) { bot.chat('/cf test third-person marth'); await sleep(5000); } else { bot.chat('/cf change marth'); await sleep(2000); }
  // 1. Coups d'epee sur le mannequin : il encaisse, ne recule pas, et le testeur ne prend rien.
  await place(); bot.setQuickBarSlot(0); await sleep(300);
  let m = body(); const p0 = posOf(); const h0 = await hpOf(); const me0 = bot.health;
  // Deux coups : le troisieme tuerait le mannequin (20 PV), qui se remettrait aussitot a fond.
  for (let i = 0; i < 2; i++) { await bot.lookAt(body().position.offset(0, 1.4, 0), true); bot.attack(body()); await sleep(700); }
  await sleep(500);
  const p1 = posOf(); const h1 = await hpOf();
  console.log('2 coups d\'epee : vie du mannequin', h0, '->', h1, '| deplacement', p0 && p1 ? p0.distanceTo(p1).toFixed(2) : '?', 'bloc(s) | ma vie', me0, '->', bot.health);
  // 2. Contre : le mannequin se met en garde, je frappe, il riposte.
  await sleep(5500); await place(); bot.setQuickBarSlot(1); await sleep(300);
  await bot.lookAt(body().position.offset(0, 1.4, 0), true);
  // La garde ne dure qu'une seconde : on frappe tout de suite apres.
  const h2 = await hpOf(); const me2 = bot.health; const pos2 = bot.entity.position.clone();
  bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(250);
  bot.setQuickBarSlot(0); await sleep(100); bot.attack(body()); await sleep(1200);
  console.log('coup pendant le Contre : vie du mannequin', h2, '->', await hpOf(), '| ma vie', me2, '->', bot.health.toFixed(1), '| je suis projete de', pos2.distanceTo(bot.entity.position).toFixed(1), 'blocs');
  // 3. Accroupi + clic gauche dans le vide avec l'arme : le mannequin frappe.
  await sleep(1500); await place(); bot.setQuickBarSlot(0); await sleep(300);
  await bot.look(Math.PI, 0, true); // dos au mannequin (yaw de mineflayer : 0 = nord, PI = sud)
  let me3 = bot.health; bot.swingArm(); await sleep(900);
  console.log('clic gauche dans le vide (debout) : ma vie', me3, '->', bot.health);
  bot.setControlState('sneak', true); await sleep(400);
  me3 = bot.health; bot.swingArm(); await sleep(900);
  bot.setControlState('sneak', false);
  console.log('accroupi + clic gauche dans le vide : ma vie', me3, '->', bot.health.toFixed(1));
  bot.quit(); process.exit(0);
});
