// La visee sur la carte (/cf map) : un admin ouvre la visee d'un clic droit, pousse la croix rouge du regard, et un
// clic le teleporte la ou elle est. Verifie la croix (dans les paquets de carte), la barre d'action, l'arrivee,
// l'annulation en s'accroupissant, et qu'un joueur sans droits n'a rien de tout cela.
//
// Usage : node carte_tp.js <bordure> <x> <z> [<x> <z> ...]      (les endroits a viser, dans le monde de jeu)
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const border = +process.argv[2];
const targets = [];
for (let i = 3; i + 1 < process.argv.length; i += 2) targets.push([+process.argv[i], +process.argv[i + 1]]);

const TOP = 46, BOTTOM = 89, MIDDLE = (TOP + BOTTOM) / 2, UNITS = 256 / (BOTTOM - TOP);
const RED_X = 26;                                    // le numero de la croix rouge parmi les marques de carte
const perBlock = 2 * (128 - 12) / (2 * border);      // positions de marque par bloc

const flat = c => {
  if (c == null) return '';
  if (typeof c === 'string') return c;
  if (c.type === 'compound' || c.type === 'string' || c.type === 'list') return flat(c.value);
  if (Array.isArray(c)) return c.map(flat).join('');
  return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? '');
};

function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.cross = null; bot.bar = ''; bot.said = [];
  bot._client.on('map', m => { if (m.icons) { const c = m.icons.find(i => i.type === RED_X); bot.cross = c ? [c.x, c.z ?? c.y] : null; bot.marks = m.icons.length; } });
  bot._client.on('action_bar', m => { bot.bar = flat(m.text); });
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) bot.bar = t; else bot.said.push(t); });
  return new Promise(res => bot.once('spawn', () => res(bot)));
}

// Le regard du bot, a la facon du jeu (degres, lacet 0 = sud, inclinaison positive vers le bas).
const yawOf = bot => 180 - bot.entity.yaw * 180 / Math.PI;
const pitchOf = bot => -bot.entity.pitch * 180 / Math.PI;
const look = (bot, yaw, pitch) => bot.look(Math.PI - yaw * Math.PI / 180, -pitch * Math.PI / 180, true);
const where = bot => `${bot.game.dimension} ${bot.entity.position.x.toFixed(1)} ${bot.entity.position.y.toFixed(1)} ${bot.entity.position.z.toFixed(1)}`;

(async () => {
  let fails = 0;
  const check = (ok, what) => { console.log((ok ? 'OK   ' : 'ECHEC ') + what); if (!ok) fails++; };

  const admin = await join('Invite');
  await sleep(3000);
  admin.chat('/cf map');
  await sleep(1500);
  const held = admin.heldItem;
  check(held && held.name === 'filled_map', `carte en main : ${held ? held.name : 'rien'}`);
  console.log('depart :', where(admin), '| regard', yawOf(admin).toFixed(1), pitchOf(admin).toFixed(1));

  for (const [x, z] of targets) {
    // Le clic droit ouvre la visee : le regard se baisse sur la carte, la croix est au milieu.
    await look(admin, 30, 5);
    await sleep(300);
    const zero = yawOf(admin);
    admin.activateItem();
    await sleep(700);
    check(Math.abs(pitchOf(admin) - MIDDLE) < 0.6, `visee ouverte : regard baisse a ${pitchOf(admin).toFixed(1)} (attendu ${MIDDLE})`);
    check(admin.cross && Math.abs(admin.cross[0]) <= 1 && Math.abs(admin.cross[1]) <= 1, `croix au milieu : ${JSON.stringify(admin.cross)}`);

    // Le regard pousse la croix sur l'endroit vise.
    const u = Math.round(x * perBlock), v = Math.round(z * perBlock);
    await look(admin, zero + u / UNITS, MIDDLE + v / UNITS);
    await sleep(500);
    check(admin.cross && Math.abs(admin.cross[0] - u) <= 1 && Math.abs(admin.cross[1] - v) <= 1, `croix sur ${x}, ${z} : ${JSON.stringify(admin.cross)} (attendu ${u}, ${v})`);
    console.log('     barre d\'action :', admin.bar);

    // Le clic : teleporte.
    admin.said.length = 0;
    admin.activateItem();
    await sleep(2500);
    const p = admin.entity.position;
    check(Math.abs(p.x - 0.5 - x) <= 12 && Math.abs(p.z - 0.5 - z) <= 12, `arrivee : ${where(admin)} (vise ${x}, ${z})`);
    check(Math.abs(yawOf(admin) - zero) < 0.6 && Math.abs(pitchOf(admin) - 5) < 0.6, `regard rendu : ${yawOf(admin).toFixed(1)} ${pitchOf(admin).toFixed(1)}`);
    check(admin.cross === null, `croix retiree de la carte : ${JSON.stringify(admin.cross)}`);
    console.log('     dit :', admin.said.filter(t => t.includes('Teleporte')).join(' | '));
    await sleep(800);
  }

  // S'accroupir annule : pas de teleportation, le regard d'avant est rendu.
  await look(admin, -60, 0);
  await sleep(300);
  const before = admin.entity.position.clone();
  admin.activateItem();
  await sleep(700);
  check(admin.cross !== null, `nouvelle visee : croix ${JSON.stringify(admin.cross)}`);
  admin.setControlState('sneak', true);
  await sleep(600);
  admin.setControlState('sneak', false);
  await sleep(600);
  check(admin.cross === null && admin.entity.position.distanceTo(before) < 0.5, `accroupi : visee annulee, pas bouge (${where(admin)}), barre "${admin.bar}"`);
  check(Math.abs(yawOf(admin) + 60) < 0.6 && Math.abs(pitchOf(admin)) < 0.6, `regard rendu : ${yawOf(admin).toFixed(1)} ${pitchOf(admin).toFixed(1)}`);

  // Changer d'objet annule aussi.
  admin.activateItem();
  await sleep(700);
  check(admin.cross !== null, 'visee rouverte');
  admin.setQuickBarSlot((admin.quickBarSlot + 1) % 9);
  await sleep(600);
  check(admin.cross === null || admin.heldItem == null, `carte quittee : visee annulee, barre "${admin.bar}"`);
  admin.setQuickBarSlot((admin.quickBarSlot + 8) % 9);
  await sleep(300);

  // Un joueur sans droits : sa carte reste une carte.
  const guest = await join('Passant');
  await sleep(3000);
  guest.chat('/cf map');
  await sleep(1500);
  const start = guest.entity.position.clone(), pitch = pitchOf(guest);
  guest.activateItem();
  await sleep(700);
  check(guest.heldItem && guest.heldItem.name === 'filled_map' && guest.cross === null && Math.abs(pitchOf(guest) - pitch) < 0.1,
    `joueur sans droits : pas de visee (croix ${JSON.stringify(guest.cross)}, regard ${pitchOf(guest).toFixed(1)})`);
  guest.activateItem();
  await sleep(1500);
  check(guest.entity.position.distanceTo(start) < 0.5, `joueur sans droits : pas teleporte (${where(guest)})`);

  console.log(fails ? `${fails} echec(s)` : 'tout est bon');
  admin.quit(); guest.quit();
  setTimeout(() => process.exit(fails ? 1 : 0), 500);
})();
