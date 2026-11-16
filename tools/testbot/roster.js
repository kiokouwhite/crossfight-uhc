// Lancement bloque tant qu'un joueur n'a pas choisi ; formats, licences et personnages desactives ; doublons
// interdits (un solitaire par personnage, une equipe par licence) ; equipes tirees au sort.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.chatLog = []; bot.on('messagestr', m => bot.chatLog.push(m));
  return new Promise(r => bot.once('spawn', () => r(bot)));
}
async function open(bot, item) {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  const slot = bot.inventory.slots.slice(36, 45).findIndex(it => it && it.name === item);
  bot.setQuickBarSlot(slot); await sleep(300); bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(800);
  return bot.currentWindow;
}
const top = w => [1, 3, 5, 7, 8].map(s => w.slots[s] ? w.slots[s].name : '-').join(' ');
const say = async (bot, cmd, re, wait = 700) => { bot.chatLog.length = 0; bot.chat(cmd); await sleep(wait); return (re ? bot.chatLog.filter(m => re.test(m)) : bot.chatLog).join(' | ').slice(0, 330); };
(async () => {
  const a = await join('Invite'); await sleep(3500);
  const b = await join('Bleu'); await sleep(3500);
  const c = await join('Carmin'); await sleep(3500);
  console.log('barre de l\'administrateur :', a.inventory.slots.slice(36, 45).map((it, i) => it ? i + ':' + it.name : null).filter(Boolean).join(' '));
  console.log('1. lancement sans equipes :', await say(a, '/cf start', /Impossible/), '| Bleu lit :', b.chatLog.filter(m => /attend/.test(m)).pop());
  // Bleu et Carmin solitaires, Invite regarde ; doublons interdits : un solitaire par personnage.
  let w = await open(b, 'white_banner'); await b.clickWindow(1, 0, 0); await sleep(400);
  w = await open(c, 'white_banner'); await c.clickWindow(1, 0, 0); await sleep(400);
  console.log('2. encore un sans equipe :', await say(a, '/cf start', /Impossible/));
  w = await open(a, 'white_banner'); await a.clickWindow(8, 0, 0); await sleep(400); console.log('   Invite regarde :', a.chatLog.filter(m => /spectateur/.test(m)).pop());
  for (const bot of [a, b, c]) if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await say(a, '/cf regle doublons non');
  console.log('3. lancement :', await say(a, '/cf start', /Lancement|Impossible/, 3000));
  console.log('   Bleu prend Yuki :', await say(b, '/cf perso yuki', /incarnes|indisponible/, 1200), '| Carmin veut Yuki :', await say(c, '/cf perso yuki', /incarnes|indisponible/, 1200), '| puis Shoja :', await say(c, '/cf perso shoja', /incarnes|indisponible/, 1200));
  await say(a, '/cf stop', null, 3000);
  // Formats et licences desactives.
  await say(a, '/cf desactiver duo'); for (const l of ['valorant', 'fire_emblem', 'earth_delta', 'unordinary', 'snk']) await say(a, '/cf desactiver ' + l, null, 250);
  await say(a, '/cf desactiver weiss', null, 300);
  w = await open(b, 'white_banner'); console.log('4. menu de Bleu, duo desactive :', top(w)); await b.clickWindow(5, 0, 0); await sleep(500);
  w = await open(c, 'white_banner'); console.log('   menu de Carmin, une seule licence trio sans doublons :', top(w)); await c.clickWindow(5, 0, 0); await sleep(500);
  console.log('   Carmin :', c.chatLog.filter(m => /cree|solitaire/.test(m)).pop() || 'refuse', '|', await say(a, '/cf team list'));
  await c.clickWindow(18, 0, 0); await sleep(500); for (const bot of [b, c]) if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('   Carmin rejoint Bleu, lancement :', await say(a, '/cf start', /Lancement|Impossible/, 3000), '|', await say(a, '/cf team list'));
  console.log('   Bleu veut Weiss (desactivee) :', await say(b, '/cf perso weiss', /incarnes|indisponible/, 1200), '| Ruby :', await say(b, '/cf perso ruby', /incarnes|indisponible/, 1200));
  await say(a, '/cf stop', null, 3000);
  // Le menu de l'administrateur.
  w = await open(a, 'armor_stand'); console.log('5. menu des licences :', w.slots.slice(0, 45).map((it, i) => it && it.name !== 'gray_stained_glass_pane' ? i + ':' + it.name : null).filter(Boolean).join(' '));
  await a.clickWindow(19, 1, 0); await sleep(700); console.log('   clic droit sur la premiere licence :', a.currentWindow.slots.slice(0, 27).map((it, i) => it && it.name !== 'gray_stained_glass_pane' ? i + ':' + it.name : null).filter(Boolean).join(' '));
  await a.clickWindow(0, 0, 0); await sleep(400); await a.clickWindow(22, 0, 0); await sleep(600); await a.clickWindow(7, 0, 0); await sleep(400); await a.clickWindow(8, 0, 0); await sleep(400);
  a.chatLog.length = 0; a.closeWindow(a.currentWindow); await sleep(900); console.log('   annonce :', a.chatLog.filter(m => /reglee/.test(m)).pop());
  // Equipes aleatoires : trois joueurs, format duo -> un duo et un solitaire.
  w = await open(a, 'white_banner'); console.log('6. banniere en mode aleatoire :', [4, 8].map(s => w.slots[s] && w.slots[s].name).join(' ')); await a.clickWindow(8, 0, 0); await sleep(400); a.closeWindow(a.currentWindow);
  await say(a, '/cf activer duo'); await say(a, '/cf setsize DUO');
  console.log('   lancement :', await say(a, '/cf start', /Lancement|Impossible/, 3000), '|', await say(a, '/cf team list'));
  await say(a, '/cf stop', null, 3000);
  // On remet tout.
  for (const x of ['valorant', 'fire_emblem', 'earth_delta', 'unordinary', 'snk', 'weiss', 'marth', 'solo', 'trio']) await say(a, '/cf activer ' + x, null, 200);
  await say(a, '/cf regle doublons oui'); await say(a, '/cf regle equipes-aleatoires non'); await say(a, '/cf setsize TRIO');
  console.log('fin :', await say(a, '/cf desactiver'));
  for (const bot of [a, b, c]) bot.quit(); process.exit(0);
})();
