// Les structures : export d'une construction d'essai (batie en l'air par des commandes de la console), puis, une
// fois posee ailleurs par "cf structure placer", controle de ce qui est arrive : blocs, coffre vide, decor garde,
// villageois ecarte, terrain degage au-dessus, dessous comble, et protection.
// Usage : node structure.js exporter | node structure.js controler [monde]
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execSync } = require('child_process');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(...a);
const consoleCmd = c => execSync(`timeout 5 sh -c "echo '${c}' > /home/user/testserver/console.in"`);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const last = re => (chat.filter(m => re.test(m)).pop() || '-').slice(0, 200);
const SRC = [-410, 145, -410];
bot.once('spawn', async () => {
  await sleep(4000);
  if (process.argv[2] === 'exporter') {
    // La construction d'essai est dans le monde principal du serveur : on s'y rend pour l'exporter.
    consoleCmd('execute in minecraft:overworld run tp Invite -390 176 -390'); await sleep(2500);
    bot.chat('/cf structure exporter essai -410 145 -410 -371 172 -371 151 Auteur Essai');
    for (let i = 0; i < 60 && !chat.some(m => /exportee/.test(m)); i++) await sleep(500);
    log(last(/Export de/)); log(last(/exportee/));
    bot.chat('/cf structure liste'); await sleep(1200); log(last(/essai/));
    bot.quit(); process.exit(0);
  }
  const cfg = fs.readFileSync('/home/user/testserver/plugins/CrossfightUHC/config.yml', 'utf8');
  const world = process.argv[3] || 'world', dim = world === 'world' ? 'minecraft:overworld' : 'minecraft:' + world;
  const m = cfg.slice(cfg.indexOf('\n    ' + world + ':', cfg.indexOf('posees:'))).match(/essai:\s*\n\s*boite:\s*\n((?:\s*- -?\d+\n){6})/);
  const box = m[1].match(/-?\d+/g).map(Number);
  log('monde', world, '| boite :', box.join(' '));
  const at = (x, y, z) => [box[0] + (x - SRC[0]), box[1] + (y - SRC[1]), box[2] + (z - SRC[2])];
  const mid = at(-390, 152, -372);
  consoleCmd(`execute in ${dim} run tp Invite ${mid[0]} ${mid[1] + 12} ${mid[2]}`);
  consoleCmd('gamemode spectator Invite');
  await sleep(5000);
  const { Vec3 } = require('vec3');
  const name = p => { const b = bot.blockAt(new Vec3(p[0], p[1], p[2])); return b ? b.name : '?'; };
  log('sol (coin)          :', name(at(-410, 150, -410)), '| attendu stone_bricks');
  log('sol (autre coin)    :', name(at(-371, 150, -371)), '| attendu stone_bricks');
  log('mur                 :', name(at(-410, 155, -400)), '| attendu bricks');
  log('haut de la tour     :', name(at(-395, 170, -395)), '| attendu quartz_block');
  log('bloc d\'or           :', name(at(-375, 152, -375)), '| attendu gold_block');
  log('coffre              :', name(at(-385, 151, -385)), '| attendu chest');
  log('mur de la cave      :', name(at(-405, 147, -400)), '| attendu deepslate_bricks');
  log('interieur de la cave:', name(at(-400, 147, -400)), '| attendu : le terrain (pas d\'air), car le sous-sol est garde');
  log('interieur du batiment, au-dessus du sol :', name(at(-380, 153, -380)), name(at(-380, 156, -390)), '| attendu air (terrain degage)');
  let holes = 0, cols = 0;
  for (let x = -410; x <= -371; x += 3) for (let z = -410; z <= -371; z += 3) { cols++; const p = at(x, 149, z); if (name(p) === 'air' || name(p) === 'water') holes++; }
  log('sous le sol : colonnes vides', holes, 'sur', cols, '| attendu 0');
  const ents = Object.values(bot.entities).filter(e => e.position.distanceTo(new Vec3(mid[0], mid[1], mid[2])) < 40);
  const count = n => ents.filter(e => e.name === n).length;
  log('decor : porte-armures', count('armor_stand'), '| cadres', count('item_frame'), '| villageois', count('villager'), '| attendu 1, 1, 0');
  const chest = at(-385, 151, -385);
  consoleCmd(`execute in ${dim} run data get block ${chest[0]} ${chest[1]} ${chest[2]} Items`);
  await sleep(800);
  const out = fs.readFileSync('/home/user/testserver/start.out', 'utf8').split('\n').filter(l => /has the following block data|Found no elements|no elements matching/.test(l)).pop() || '-';
  log('contenu du coffre pose :', out.replace(/\x1b\[[0-9;]*m/g, '').slice(17, 140));
  // La protection : en survie, on ne casse pas et on ne pose pas dans la structure.
  const floor = at(-380, 150, -380);
  consoleCmd('gamemode survival Invite');
  consoleCmd(`execute in ${dim} run tp Invite ${floor[0] + 0.5} ${floor[1] + 1} ${floor[2] + 0.5}`);
  await sleep(2500);
  const target = bot.blockAt(new Vec3(floor[0] + 1, floor[1], floor[2]));
  try { await bot.dig(target, true); } catch (e) { log('creuser : ' + e.message); }
  await sleep(1200);
  log('apres avoir creuse le sol :', name([floor[0] + 1, floor[1], floor[2]]), '| attendu stone_bricks |', last(/indestructible/));
  bot.quit(); process.exit(0);
});
