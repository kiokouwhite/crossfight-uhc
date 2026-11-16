// La fabrication : Hasty Boys enchante les outils, personne ne fabrique d'epee, la hache se fabrique mais ne
// blesse pas un joueur (ici un mannequin), alors que le poing le blesse.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
// Pose la recette case par case dans l'etabli ouvert : { case de la grille : objet }.
async function lay(win, pattern) {
  for (const name of new Set(Object.values(pattern))) {
    const from = win.slots.findIndex((it, i) => i >= 10 && it && it.name === name);
    await bot.clickWindow(from, 0, 0);
    for (const [slot, n] of Object.entries(pattern)) if (n === name) await bot.clickWindow(+slot, 1, 0);
    await bot.clickWindow(from, 0, 0);
  }
  await sleep(500);
  const out = win.slots[0];
  return out ? out.name + ' ' + JSON.stringify(out.enchants || []) : 'rien';
}
async function clear(win) { for (let s = 1; s <= 9; s++) if (win.slots[s]) await bot.clickWindow(s, 0, 1); await sleep(300); }
const health = async () => { chat.length = 0; bot.chat('/data get entity @e[type=!player,type=!item,distance=..8,limit=1,sort=nearest] Health'); await sleep(600); return (chat.pop() || '').replace(/.*data: /, ''); };
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf scenario hastyboys oui'); await sleep(400);
  bot.chat('/cf test'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  bot.chat('/clear @s'); await sleep(600); bot.chat('/give @s oak_planks 16'); bot.chat('/give @s stick 8'); await sleep(900);
  const t = bot.entity.position.floored().offset(-2, 0, 0); bot.chat(`/setblock ${t.x} ${t.y} ${t.z} crafting_table`); await sleep(600);
  const win = await bot.openBlock(bot.blockAt(t)); await sleep(500);
  console.log('pioche (Hasty Boys) :', await lay(win, { 1: 'oak_planks', 2: 'oak_planks', 3: 'oak_planks', 5: 'stick', 8: 'stick' })); await clear(win);
  console.log('epee :', await lay(win, { 2: 'oak_planks', 5: 'oak_planks', 8: 'stick' })); await clear(win);
  console.log('hache :', await lay(win, { 1: 'oak_planks', 2: 'oak_planks', 4: 'oak_planks', 5: 'stick', 8: 'stick' }));
  await bot.clickWindow(0, 0, 1); await sleep(500); bot.closeWindow(win); await sleep(500);
  const axe = bot.inventory.items().find(i => i.name === 'wooden_axe'); console.log('hache prise :', !!axe);
  // Un mannequin, frappe a la hache puis au poing.
  bot.chat('/cf mannequin 1'); await sleep(1500);
  const dummy = bot.nearestEntity(e => e !== bot.entity && e.type !== 'player' && e.name !== 'item' && e.position.distanceTo(bot.entity.position) < 8);
  if (!dummy) { console.log('pas de mannequin'); } else {
    bot.chat(`/tp @s ${dummy.position.x + 2} ${dummy.position.y} ${dummy.position.z}`); await sleep(1000);
    await bot.lookAt(dummy.position.offset(0, 1.2, 0)); console.log('mannequin', dummy.name, 'vie :', await health());
    await bot.equip(axe, 'hand'); await sleep(300); bot.attack(dummy); await sleep(900); console.log('apres un coup de hache :', await health(), '|', chat.length);
    await bot.unequip('hand'); await sleep(300); bot.attack(dummy); await sleep(900); console.log('apres un coup de poing :', await health());
  }
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(1500); bot.chat('/cf scenario hastyboys non'); await sleep(400); bot.quit(); process.exit(0);
});
