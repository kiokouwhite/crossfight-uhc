const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
const take = () => { const s = sounds.filter(x => !/sfx\.select/.test(x)).join(', ') || '(rien)'; sounds = []; return s; };
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const bar = () => [...Array(9).keys()].map(i => bot.inventory.slots[36 + i] ? bot.inventory.slots[36 + i].name : '-');
const use = async (slot, b = bot, sneak = false) => { b.setQuickBarSlot(slot); await sleep(150); if (sneak) { b.setControlState('sneak', true); await sleep(150); } b.activateItem(); await sleep(60); b.deactivateItem(); if (sneak) { await sleep(100); b.setControlState('sneak', false); } };
async function perso(id, plan) {
  t0 = Date.now(); bot.chat('/cf change ' + id); await sleep(2500); log(id + ' selection ->', take());
  const names = bar(); log('   barre :', names.join(', '));
  for (const [label, act, wait] of plan) { t0 = Date.now(); msgs = []; await act(names); await sleep(wait); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); const red = msgs.filter(x => /Ton|Pas|aucun|Aucun/.test(x)).join(' ').slice(0, 90); log('   ' + label + ' ->', take(), red ? '| ' + red : ''); }
  bot.chat('/cf son voice.' + id + '_kill'); await sleep(700); log('   kill ->', take());
  bot.chat('/cf heal'); bot.chat('/cf clear'); await sleep(300); await ensureTest(bot);
}
const slotOf = (names, item) => names.indexOf(item);
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot); take();
  bot.chat('/cf mannequin clear'); await sleep(300);
  await perso('weiss', [
    ['Myrtenaster (Dust suivante)', n => use(0), 1500],
    ['Glyphs type 1', n => use(1), 1800], ['Glyphs type 2', n => use(2), 1800],
    ['Invocation (aucun ennemi vaincu)', n => use(8), 1500]]);
  await perso('blake', [
    ['Gambol Shroud, Couperet (rien a faire)', n => use(0), 1200],
    ['Gambol Shroud, changement de mode', n => use(0, bot, true), 1200],
    ['Gambol Shroud, katana lance', n => use(0), 2200],
    ['Ombre', n => use(slotOf(n, 'black_dye') >= 0 ? slotOf(n, 'black_dye') : 2), 1800],
    ['Discretion', n => use(3), 1500], ['Discretion (fin)', n => use(3), 1200],
    ['Ultime', n => use(8), 1500], ['Ultime, un bond', n => use(8), 1200], ['Ultime, un 2e bond', n => use(8), 1200]]);
  await perso('yang', [
    ['Ember Celica', n => use(1), 1500],
    ['Brulure (sans degats subis)', n => use(2), 1200],
    ['Brulure (apres un coup)', async n => { bot.chat('/damage @s 4 minecraft:mob_attack'); await sleep(600); sounds = []; await use(2); }, 1500],
    ['Incendie Interne', n => use(3), 1500], ['Incendie Interne (fin)', n => use(3), 1000],
    ['Ultime', n => use(8), 1500]]);
  // Colere Flamboyante : frapper un mannequin sous 5 coeurs.
  bot.chat('/cf change yang'); await sleep(2500); bot.chat('/cf mannequin'); await sleep(900); take();
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 1.4) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400);
  bot.chat('/effect clear @s'); bot.chat('/damage @s 13 minecraft:out_of_world'); await sleep(600); take();
  t0 = Date.now(); bot.setQuickBarSlot(0); for (let i = 0; i < 3; i++) { const zz = Object.values(bot.entities).find(e => e.name === 'zombie'); await bot.lookAt(zz.position.offset(0, 1.2, 0), true); bot.attack(zz); await sleep(700); }
  log('yang, 3 coups sous 5 coeurs (vie ' + bot.health.toFixed(0) + ') ->', take());
  bot.chat('/cf mannequin clear'); bot.chat('/cf heal'); await sleep(400);
  // Echanges de selection.
  for (const [a, b] of [['ruby', 'weiss'], ['blake', 'yang'], ['weiss', 'yang']]) { take(); t0 = Date.now(); bot.chat('/cf dialogue ' + a + ' ' + b); await sleep(9500); log('dialogue ' + a + '/' + b + ' ->', take()); }
  // Ultime en duo : Weiss lance avec Yang.
  const mate = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  let mateSounds = []; mate._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:voice\.[a-z0-9_.]+/); if (x) mateSounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
  await new Promise(r => mate.once('spawn', r)); await sleep(2500); if (mate.health <= 0) { mate._client.write('client_command', { actionId: 0 }); await sleep(800); }
  await ensureTest(mate); mate.chat('/cf change yang'); await sleep(2000);
  bot.chat('/cf change weiss'); await sleep(2000); bot.chat('/tp Tester2 Tester'); await sleep(500); bot.chat('/cf ally Tester2'); await sleep(800); bot.chat('/cf duo'); await sleep(800); take(); mateSounds = [];
  const duo = bar().indexOf('amethyst_cluster'); t0 = Date.now(); await use(duo); await sleep(3000);
  log('Ultime en duo Weiss + Yang (entendu par Weiss) ->', take()); log('   (entendu par Yang) ->', mateSounds.join(', ') || '(rien)');
  t0 = Date.now(); bot.chat('/kill @s'); await sleep(1500); log('mort de Weiss ->', take());
  bot._client.write('client_command', { actionId: 0 }); await sleep(1500);
  bot.chat('/cf clear'); mate.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); mate.chat('/cf test'); await sleep(800); mate.quit(); bot.quit(); process.exit(0);
});
