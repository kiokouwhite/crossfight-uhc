// Le quartier general : Invite paie Anna et prend le lieu, recoit la carte (qui montre Cible), ouvre la fiche de Cible
// et le traque ; Cible voit qu'il est traque ; puis Cible prend le lieu, et la boussole d'Invite disparait.
// node quartier_general.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const joueur = async nom => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: nom, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.dit = []; bot.barres = []; bot.marques = []; bot.fenetre = null;
  bot._client.on('system_chat', m => { if (!m.isActionBar) bot.dit.push(flat(m.content)); });
  bot._client.on('boss_bar', d => { if (d.action === 0) bot.barres.push(flat(d.title)); if (d.action === 1) bot.barres.push('(retiree)'); });
  bot._client.on('map', d => { if (d.icons) { bot.marques = d.icons.map(i => flat(i.displayName)).filter(Boolean); bot.icones = d.icons.map(i => ({ nom: flat(i.displayName), x: i.x, z: i.z })); } });
  bot.on('windowOpen', w => { bot.fenetre = w; });
  bot.barre = ''; bot._client.on('action_bar', d => { bot.barre = flat(d.text); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  return bot;
};
(async () => {
  const a = await joueur('Invite'), b = await joueur('Cible');
  const monde = process.argv[2];
  for (const bot of [a, b]) { bot.chat('/execute in minecraft:' + monde + ' run tp @s 0 200 0'); await sleep(800); bot.chat('/gamemode creative'); }
  await sleep(1500);
  a.chat('/cf qg liberer'); a.chat('/clear'); await sleep(500);
  a.chat('/cf quete tp jouki'); await sleep(5000);
  b.chat('/tp @s 120 200 -80'); await sleep(1000);
  const anna = Object.values(a.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(a.entity.position) < 8);
  a.fenetre = null; a.useOn(anna); await sleep(1500);
  console.log('offre :', a.fenetre ? flat(a.fenetre.title) + ' | cases ' + [11, 13, 15, 22].filter(s => a.fenetre.slots[s]).join(' ') : 'pas de menu');
  await a.clickWindow(11, 0, 0).catch(() => {}); await sleep(800);
  console.log('sans diamants :', a.dit.some(t => /tient maintenant/.test(t)) ? 'lieu pris (!)' : 'refuse');
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  a.chat('/give @s diamond 7'); await sleep(800);
  a.fenetre = null; a.useOn(anna); await sleep(1500);
  await a.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500);
  const compte = (bot, nom) => bot.inventory.items().filter(i => i.name === nom).reduce((s, i) => s + i.count, 0);
  console.log('avec 7 diamants :', (a.dit.find(t => /tient maintenant/.test(t)) || 'pas pris').slice(0, 90), '| diamants restants', compte(a, 'diamond'), '| carte', compte(a, 'filled_map'));
  await sleep(3000);
  console.log('marques nommees sur la carte d Invite :', a.marques.join(', ') || 'aucune');
  // la visee sur la carte : clic droit carte en main, le regard amene la croix sur la marque de Cible, un clic ouvre sa fiche
  const cible = (a.icones || []).find(i => i.nom === 'Cible');
  if (cible) {
    a.chat('/deop Invite'); await sleep(800);           // un admin se teleporterait : la fiche est pour les joueurs ordinaires
    const carte = a.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && i.name === 'filled_map');
    if (carte >= 36) a.setQuickBarSlot(carte - 36);
    await sleep(400); a.fenetre = null; a.activateItem(); await sleep(1200);
    const UNITS = 256 / 43, yaw0 = a.entity.yaw;
    await a.look(yaw0 - (cible.x / UNITS) * Math.PI / 180, -(67.5 + cible.z / UNITS) * Math.PI / 180, true); await sleep(900);
    console.log('  visee ouverte :', a.dit.some(t => /Visee sur la carte/.test(t)), '| barre :', a.barre, '| main :', a.heldItem ? a.heldItem.name : 'vide');
    a.activateItem(); await sleep(1500);
    console.log('visee sur la carte (marque en', cible.x, cible.z, ') :', a.fenetre ? 'fiche ouverte : ' + flat(a.fenetre.title) : 'pas de fiche');
    if (a.currentWindow) a.closeWindow(a.currentWindow); await sleep(500);
  } else console.log('visee : la marque de Cible n est pas sur la carte');
  a.fenetre = null; a.chat('/cf qg voir Cible'); await sleep(1500);
  console.log('fiche :', a.fenetre ? flat(a.fenetre.title) + ' | boussole en case 8 : ' + (a.fenetre.slots[8] ? a.fenetre.slots[8].name : 'non') : 'pas de fiche');
  await a.clickWindow(8, 0, 0).catch(() => {}); await sleep(2500);
  console.log('traque : boussole chez Invite', compte(a, 'compass'), '| Cible :', b.barres.join(' / ') || 'pas de barre', '|', (b.dit.find(t => /traqué/.test(t)) || '').slice(0, 60));
  b.chat('/cf qg prendre'); await sleep(3000);
  console.log('Cible prend le lieu :', (a.dit.find(t => /a été pris/.test(t)) || 'Invite non prevenu').slice(0, 80), '| boussole chez Invite', compte(a, 'compass'), '| barres de Cible :', b.barres.join(' / '));
  a.quit(); b.quit(); setTimeout(() => process.exit(0), 500);
})();
