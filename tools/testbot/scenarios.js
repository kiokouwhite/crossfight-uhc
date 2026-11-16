// Les scenarios de confort : le menu de l'administrateur, puis chacun essaye pour de bon dans le monde de test
// (CutClean, Vanilla+, Hasty Boys, Fast Smelting, Cat Eyes) et dans la foret de test (Timber).
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const count = name => bot.inventory.items().filter(i => i.name === name).reduce((n, i) => n + i.count, 0);
const hold = async name => { const it = bot.inventory.items().find(i => i.name === name); if (it) await bot.equip(it, 'hand'); return !!it; };
async function mine(block, dx, dz) {
  const at = bot.entity.position.floored().offset(dx, 0, dz);
  bot.chat(`/setblock ${at.x} ${at.y} ${at.z} ${block}`); await sleep(500);
  await bot.dig(bot.blockAt(at)); await sleep(300);
  await bot.lookAt(at.offset(0.5, 0, 0.5)); bot.setControlState('forward', true); await sleep(700); bot.setControlState('forward', false);
  bot.setControlState('back', true); await sleep(700); bot.setControlState('back', false); await sleep(400);
}
bot.once('spawn', async () => {
  await sleep(4000);
  // Le menu, objet en main.
  bot.setQuickBarSlot(4); await sleep(300); bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(800);
  const w = bot.currentWindow; console.log('menu des scenarios :', w ? w.slots.slice(10, 16).map(i => i && i.name).join(' ') : 'pas ouvert');
  for (let s = 10; s <= 15; s++) { await bot.clickWindow(s, 0, 0); await sleep(250); }
  bot.closeWindow(bot.currentWindow); await sleep(800); console.log('annonce :', chat.filter(m => m.includes('Scenarios')).pop());
  bot.chat('/cf scenario'); await sleep(600); console.log('liste :', chat.slice(-6).map(m => m.slice(0, 16)).join(' | '));
  // Le monde de test.
  bot.chat('/cf test'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  bot.chat('/clear @s'); await sleep(600); bot.chat('/give @s iron_pickaxe'); await sleep(600); await hold('iron_pickaxe');
  await mine('iron_ore', 2, 0); await mine('gold_ore', 2, 0); await mine('gravel', 2, 0); await mine('gravel', 2, 0);
  console.log('CutClean : lingots de fer', count('iron_ingot'), '| d\'or', count('gold_ingot'), '| bruts', count('raw_iron') + count('raw_gold'), '| Vanilla+ : silex', count('flint'), 'gravier', count('gravel'));
  console.log('Cat Eyes :', Object.values(bot.entity.effects).map(e => e.id + ':' + e.duration).join(' ') || 'aucun effet');
  // Fast Smelting : la duree de cuisson d'un four qui demarre.
  const f = bot.entity.position.floored().offset(0, 0, 2);
  bot.chat(`/setblock ${f.x} ${f.y} ${f.z} furnace{Items:[{Slot:0b,id:"minecraft:cobblestone",count:4},{Slot:1b,id:"minecraft:coal",count:1}]}`); await sleep(1500);
  chat.length = 0; bot.chat(`/data get block ${f.x} ${f.y} ${f.z} cooking_total_time`); await sleep(700); console.log('Fast Smelting :', chat.pop());
  // Hasty Boys : une pioche fabriquee.
  bot.chat('/give @s oak_planks 3'); bot.chat('/give @s stick 2'); await sleep(700);
  const t = bot.entity.position.floored().offset(-2, 0, 0); bot.chat(`/setblock ${t.x} ${t.y} ${t.z} crafting_table`); await sleep(600);
  const table = bot.blockAt(t); const recipe = bot.recipesFor(bot.registry.itemsByName.wooden_pickaxe.id, null, 1, table)[0];
  if (recipe) { await bot.craft(recipe, 1, table); await sleep(500); const pick = bot.inventory.items().find(i => i.name === 'wooden_pickaxe'); console.log('Hasty Boys : pioche', pick ? JSON.stringify(pick.enchants) : 'absente'); } else console.log('Hasty Boys : pas de recette trouvee');
  bot.chat('/cf test'); await sleep(2500);
  // Timber, dans la foret de test.
  bot.chat('/cf test arbre'); await sleep(6000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  const p = bot.entity.position.floored(); let log = null;
  for (let r = 1; r < 24 && !log; r++) for (let x = -r; x <= r && !log; x++) for (let z = -r; z <= r && !log; z++) { const b = bot.blockAt(p.offset(x, 0, z)); if (b && b.name.endsWith('_log')) log = b; }
  if (!log) { console.log('Timber : pas d\'arbre trouve'); } else {
    const column = () => { let n = 0; for (let y = 0; y < 40; y++) { const b = bot.blockAt(log.position.offset(0, y, 0)); if (b && b.name.endsWith('_log')) n++; } return n; };
    bot.chat(`/tp @s ${log.position.x + 1.5} ${log.position.y} ${log.position.z + 0.5}`); await sleep(1500);
    const before = column(); await bot.dig(bot.blockAt(log.position)); await sleep(1200);
    console.log('Timber : buches du tronc', before, '->', column());
  }
  bot.chat('/cf test'); await sleep(2000);
  for (const s of ['cutclean', 'hastyboys', 'timber', 'fastsmelting', 'vanillaplus', 'cateyes']) { bot.chat('/cf scenario ' + s + ' non'); await sleep(200); }
  await sleep(500); bot.quit(); process.exit(0);
});
