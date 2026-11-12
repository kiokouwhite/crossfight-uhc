const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
const take = () => { const s = sounds.filter(x => !/sfx\.select|_selection/.test(x)).join(', ') || '(rien)'; sounds = []; return s; };
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const use = async slot => { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); };
async function near() { bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(900); const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 2.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1.2, 0), true); }
async function step(label, act, wait) { take(); t0 = Date.now(); msgs = []; await act(); await sleep(wait); const red = msgs.filter(x => /aucun|Aucun|Pas /.test(x)).join(' ').slice(0, 100); log(label + ' ->', take(), red ? '| ' + red : ''); }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change blake'); await sleep(2500); await near();
  await step('Blake, Ombre (case 2)', () => use(1), 1800);
  await step('Blake, Discretion (case 3)', () => use(2), 1500);
  await step('Blake, Discretion, fin', () => use(2), 1200);
  bot.chat('/cf clear'); await sleep(300); await ensureTest(bot);
  bot.chat('/cf change yang'); await sleep(2500); await near();
  await step('Yang, Ember Celica (arme)', () => use(0), 1500);
  await step('Yang, Brulure apres un coup', async () => { bot.chat('/damage @s 4 minecraft:mob_attack'); await sleep(600); sounds = []; await use(1); }, 1500);
  await step('Yang, Incendie Interne', () => use(2), 1500);
  await step('Yang, Incendie Interne, fin', () => use(2), 1000);
  bot.chat('/cf mannequin clear'); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
