// La tablette posee dans le monde (/cf tablette) : le bot vise une piece en attente, clique (elle est prise), vise la
// grille, clique (elle est posee), la reprend, la tourne, puis s'accroupit (le tableau se ferme).
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let meta = 0, bouge = 0, sons = [];
  bot._client.on('entity_metadata', () => meta++);
  for (const n of ['entity_teleport', 'sync_entity_position', 'entity_move_look', 'rel_entity_move']) bot._client.on(n, () => bouge++);
  bot._client.on('sound_effect', d => sons.push(typeof d.soundId === 'number' ? d.soundId : (d.soundEvent && d.soundEvent.resource) || '?'));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode creative'); bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  const p = bot.entity.position.clone();
  const affichages = () => Object.values(bot.entities).filter(e => e.name === 'text_display' || e.name === 'interaction').length;
  const avant = affichages();
  bot.chat('/cf enigme tablette'); await sleep(1500);
  console.log('affichages apparus :', affichages() - avant);
  // regard vers +Z : le tableau est a 3,5 blocs, son milieu 2 blocs au-dessus des pieds ; la droite du joueur est -X
  const point = (u, v) => new Vec3(p.x - u, p.y + 2.0 + v, p.z + 3.5);
  const etape = async (nom, u, v, action) => {
    meta = 0; bouge = 0; sons = [];
    await bot.lookAt(point(u, v), true); await sleep(400);
    if (action === 'clic') bot.swingArm('right'); else if (action === 'f') bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
    await sleep(700);
    console.log(nom, '-> changements de couleur :', meta, '| deplacements :', bouge, '| sons :', sons.length);
  };
  await etape('prendre la piece 1', -2.0, -0.85, 'clic');
  await etape('la promener sur la grille', 1.15, 0.1, null);
  await etape('la tourner', 1.15, 0.1, 'f');
  await etape('la poser', 1.15, 0.1, 'clic');
  await etape('la reprendre sur la grille', 1.15, 0.1, 'clic');
  await etape('la lacher hors de la grille', -1.3, 0.3, 'clic');
  bot.setControlState('sneak', true); await sleep(500); bot.setControlState('sneak', false); await sleep(800);
  console.log('apres accroupi, affichages restants :', affichages() - avant);
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
