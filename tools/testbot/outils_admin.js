// Les outils de l'administrateur de partie : objets dans la barre au lobby, menu des regles au clic droit,
// taille des equipes, lancement en deux clics ; un joueur ordinaire n'a rien, puis les recoit avec /cf admin.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.chatLog = []; bot.on('messagestr', m => bot.chatLog.push(m));
  return new Promise(r => bot.once('spawn', () => r(bot)));
}
const bar = bot => bot.inventory.slots.slice(36, 45).map((it, i) => it ? i + ':' + it.name : null).filter(Boolean).join(' ');
const label = it => it ? (it.customName ? JSON.stringify(it.customName).replace(/[^a-zA-Z0-9éè \-:]+/g, ' ').replace(/\s+/g, ' ').slice(0, 90) : it.name) : 'vide';
async function use(bot, slot) { bot.setQuickBarSlot(slot); await sleep(300); bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(700); }
(async () => {
  const op = await join('Invite'); await sleep(4000);
  const other = await join('Autre'); await sleep(4000);
  console.log('operateur :', bar(op)); console.log('joueur    :', bar(other) || '(rien)');
  // Le menu des regles.
  await use(op, 0);
  const w = op.currentWindow; console.log('menu :', w ? w.slots.slice(0, 45).filter(Boolean).length + ' objets' : 'pas ouvert', '| pvp :', w && label(w.slots[14]));
  await op.clickWindow(14, 0, 0); await sleep(400); console.log('clic gauche  :', label(op.currentWindow.slots[14]));
  await op.clickWindow(14, 1, 0); await op.clickWindow(14, 1, 0); await sleep(400); console.log('2 clics droits :', label(op.currentWindow.slots[14]));
  await op.clickWindow(10, 0, 1); await sleep(400); console.log('maj + clic bordure :', label(op.currentWindow.slots[10]));
  await op.clickWindow(23, 0, 0); await sleep(400); console.log('regeneration :', label(op.currentWindow.slots[23]));
  await op.clickWindow(25, 0, 0); await sleep(400); console.log('equipes :', label(op.currentWindow.slots[25]));
  op.closeWindow(op.currentWindow); await sleep(800); console.log('annonce :', other.chatLog.filter(m => m.includes('Regles changees')).pop());
  // On remet les valeurs.
  for (const c of ['pvp 30', 'bordure 1000', 'regeneration non']) { op.chat('/cf regle ' + c); await sleep(300); }
  // La taille des equipes, objet en main.
  await use(op, 1); console.log('outil equipes :', other.chatLog.filter(m => m.includes('Mode')).pop());
  op.chat('/cf setsize TRIO'); await sleep(500);
  // Le joueur ordinaire devient administrateur.
  op.chat('/cf admin Autre'); await sleep(1000); console.log('Autre, nomme :', bar(other));
  await use(other, 0); console.log('Autre ouvre le menu :', other.currentWindow ? 'oui' : 'non'); if (other.currentWindow) other.closeWindow(other.currentWindow);
  op.chat('/cf admin Autre'); await sleep(1000); console.log('Autre, retire :', bar(other) || '(rien)');
  // Lancer : un clic ne suffit pas, deux lancent.
  op.chatLog.length = 0; await use(op, 8); await sleep(300); console.log('un clic sur Lancer : etat', op.chatLog.filter(m => /lancement|Lancement|selection/i.test(m)).length ? 'lance' : 'pas lance');
  await use(op, 8); await sleep(2500); console.log('deuxieme clic :', op.chatLog.slice(-3).join(' | ').slice(0, 200), '| barre :', bar(op) || '(vide)');
  op.chat('/cf stop'); await sleep(2500); console.log('apres /cf stop :', bar(op));
  op.quit(); other.quit(); process.exit(0);
})();
