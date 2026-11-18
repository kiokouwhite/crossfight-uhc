// Le casse-briques d'Arcadia (/cf enigme briques) : le bot lance la balle, garde la raquette sous la balle (ou sous
// une pomme qui tombe), et compte les pommes rattrapees jusqu'a quinze.
// node briques.js [secondes]
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  const fond = {}, objet = {}; let said = [], compte = '';
  bot._client.on('entity_metadata', d => { for (const m of d.metadata) { if (m.key === 25 && typeof m.value === 'number') fond[d.entityId] = m.value | 0; if (m.value && typeof m.value === 'object' && 'itemId' in m.value) objet[d.entityId] = m.value.itemCount > 0 || m.value.itemId > 0; if (m.key === 23) { const t = flat(m.value); if (/Pommes/.test(t)) compte = t; } } });
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode creative'); bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  const p = bot.entity.position.clone();
  bot.chat('/cf enigme briques'); await sleep(2000);
  const uv = e => [-(e.position.x - p.x), e.position.y - (p.y + 2)];
  const briques = Object.values(bot.entities).filter(e => e.name === 'text_display' && fond[e.id] && uv(e)[1] > 0.2 && uv(e)[1] < 1.2 && (fond[e.id] | 0) !== -1).length;
  console.log('briques visibles :', briques, '|', compte);
  const fin = Date.now() + Number(process.argv[2] || 150) * 1000; let dernierClic = 0, vu = '', dernieres = null, motifs = 0; const debut = Date.now();
  while (Date.now() < fin && !said.some(t => /Quinze pommes/.test(t))) {
    const balle = Object.values(bot.entities).find(e => e.name === 'text_display' && (fond[e.id] | 0) === -1 && Math.abs(uv(e)[0]) < 1.7 && uv(e)[1] < 1.2);
    const pommes = Object.values(bot.entities).filter(e => e.name === 'item_display' && objet[e.id] && uv(e)[1] < 0.25).sort((a, b) => a.position.y - b.position.y);
    // une pomme pres de la raquette passe avant la balle, sauf si la balle va toucher le sol
    // un peu de biais, pour que la balle parte en diagonale et balaie tout le mur (au milieu de la raquette elle repart droit)
    let u = balle ? uv(balle)[0] + 0.28 * Math.sin(Date.now() / 900) : 0;
    if (pommes.length && (!balle || uv(balle)[1] > -0.3 || uv(pommes[0])[1] < -0.8)) u = uv(pommes[0])[0];
    const dx = -u, dy = 2.0 - 1.2 - 1.62, dz = 3.5;
    bot.look(Math.atan2(-dx, -dz) + Math.PI, Math.atan2(dy, Math.hypot(dx, dz)), true);
    if (Date.now() - dernierClic > 1500) { bot.swingArm('right'); dernierClic = Date.now(); }
    if (compte !== vu) { vu = compte; }
    // les pommes posees sur les briques : quand leur nombre remonte, le mur est passe au motif suivant
    const posees = Object.values(bot.entities).filter(e => e.name === 'item_display' && objet[e.id] && uv(e)[1] >= 0.25).length;
    if (dernieres != null && posees > dernieres + 2) { motifs++; console.log('  motif suivant apres', Math.round((Date.now() - debut) / 1000), 's :', posees, 'briques a pomme |', compte); }
    dernieres = posees;
    await sleep(50);
  }
  console.log('duree :', Math.round((Date.now() - (fin - Number(process.argv[2] || 150) * 1000)) / 1000), 's');
  console.log('motifs passes :', motifs);
  console.log('fin :', compte, '|', said.filter(t => /pommes/.test(t)).join(' | ') || 'pas encore quinze');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
