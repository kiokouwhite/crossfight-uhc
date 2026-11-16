// Le choix des equipes dans le lobby : la banniere de chaque joueur ouvre le menu ; l'un cree un duo, l'autre le
// rejoint, un troisieme le trouve complet et joue en solitaire ; au lancement, chaque equipe recoit une licence
// de son format.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.chatLog = []; bot.on('messagestr', m => bot.chatLog.push(m));
  return new Promise(r => bot.once('spawn', () => r(bot)));
}
const bar = bot => bot.inventory.slots.slice(36, 45).map((it, i) => it ? i + ':' + it.name : null).filter(Boolean).join(' ');
async function open(bot) {
  const slot = bot.inventory.slots.slice(36, 45).findIndex(it => it && it.name === 'white_banner');
  bot.setQuickBarSlot(slot); await sleep(300); bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(800);
  return bot.currentWindow;
}
const teams = w => w.slots.slice(18, 54).filter(Boolean).map(i => i.name + 'x' + i.count).join(' ') || '(aucune)';
const last = (bot, re) => bot.chatLog.filter(m => re.test(m)).pop();
(async () => {
  const a = await join('Invite'); await sleep(3500);
  const b = await join('Bleu'); await sleep(3500);
  const c = await join('Carmin'); await sleep(3500);
  console.log('administrateur :', bar(a)); console.log('joueur :', bar(b));
  let w = await open(b); console.log('menu de Bleu : haut', [1, 3, 5, 7].map(s => w.slots[s] && w.slots[s].name + 'x' + w.slots[s].count).join(' '), '| equipes', teams(w));
  await b.clickWindow(3, 0, 0); await sleep(600); console.log('Bleu cree un duo :', last(b, /cree|solitaire/), '| equipes', teams(b.currentWindow));
  w = await open(c); console.log('Carmin voit :', teams(w));
  await c.clickWindow(18, 0, 0); await sleep(600); console.log('Carmin rejoint :', last(c, /rejoint/), '| Bleu lit :', last(b, /rejoint/), '| menu de Bleu :', teams(b.currentWindow));
  w = await open(a); await a.clickWindow(18, 0, 0); await sleep(500); console.log('Invite clique le duo complet :', last(a, /rejoint/) || 'refuse');
  await a.clickWindow(1, 0, 0); await sleep(600); console.log('Invite en solitaire :', last(a, /solitaire/), '| equipes', teams(a.currentWindow));
  for (const bot of [a, b, c]) if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(500); a.chatLog.length = 0; a.chat('/cf team list'); await sleep(700); console.log('liste :', a.chatLog.join(' | '));
  // Carmin change d'avis : il quitte le duo, cree un trio ; puis la partie est lancee.
  w = await open(c); await c.clickWindow(5, 0, 0); await sleep(600); console.log('Carmin cree un trio :', teams(c.currentWindow), '| Bleu lit :', last(b, /quitte/)); c.closeWindow(c.currentWindow);
  a.chat('/cf start'); await sleep(3000); a.chatLog.length = 0; a.chat('/cf team list'); await sleep(700); console.log('apres le lancement :', a.chatLog.join(' | '));
  a.chat('/cf stop'); await sleep(2500); console.log('retour au lobby :', bar(b));
  for (const bot of [a, b, c]) bot.quit(); process.exit(0);
})();
