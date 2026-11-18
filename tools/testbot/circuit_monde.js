// Le circuit pose dans le monde (/cf circuit) : le bot lit les fils sur les affichages, cherche des rotations qui
// amenent le courant aux deux lampes, clique les tuiles autant de fois qu'il faut, et attend « Circuit résolu ».
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const argb = (a, r, g, b) => ((a << 24) | (r << 16) | (g << 8) | b) | 0;
const WIRE = argb(255, 120, 130, 165), LIVE = argb(255, 255, 176, 56), SOURCE = argb(255, 255, 140, 30), LAMP = argb(255, 200, 50, 60), LAMP_ON = argb(255, 110, 240, 120);
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  const couleur = {}; let said = [], recus = 0;
  bot._client.on('entity_metadata', d => { recus++; for (const m of d.metadata) if (typeof m.value === 'number' && [WIRE, LIVE, SOURCE, LAMP, LAMP_ON, 0].includes(m.value | 0) && m.key >= 20) couleur[d.entityId] = m.value | 0; });
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode creative'); bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  const p = bot.entity.position.clone(), N = 4, CELL = 0.6, LEFT = -1.2, TOP = 1.05;
  bot.chat('/cf enigme circuit'); await sleep(2000);
  const DX = [0, 1, 0, -1], DY = [-1, 0, 1, 0];
  const mask = new Array(16).fill(0); let source = -1; const lamps = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'text_display' || !couleur[e.id]) continue;
    const u = -(e.position.x - p.x), dz = e.position.z - p.z, c = couleur[e.id];
    // un bras : 0,3 de long, a un quart de case du milieu ; l'affichage est pose par son bas
    for (let t = 0; t < 16; t++) {
      const tu = LEFT + (t % N + 0.5) * CELL, tv = TOP - (Math.floor(t / N) + 0.5) * CELL;
      if (c === SOURCE || c === LAMP || c === LAMP_ON) { if (Math.abs(u - tu) < 0.05 && Math.abs(e.position.y - (p.y + 2 + tv - 0.13)) < 0.05) { if (c === SOURCE) source = t; else if (!lamps.includes(t)) lamps.push(t); } continue; }
      for (let d = 0; d < 4; d++) {
        const b = d % 2 === 1 ? 0.1 : 0.3, au = tu + DX[d] * CELL / 4, av = tv - DY[d] * CELL / 4 - b / 2;
        if (Math.abs(u - au) < 0.03 && Math.abs(e.position.y - (p.y + 2 + av)) < 0.03) mask[t] |= 1 << d;
      }
    }
  }
  console.log('fils lus :', mask.join(' '), '| source', source, '| lampes', lamps.join(','));
  const turn = m => ((m << 1) & 15) | (m >> 3);
  const gagne = m => { const vu = new Set([source]), file = [source]; while (file.length) { const c = file.pop(); for (let d = 0; d < 4; d++) { if (!(m[c] & (1 << d))) continue; const x = c % N + DX[d], y = Math.floor(c / N) + DY[d]; if (x < 0 || x >= N || y < 0 || y >= N) continue; const o = y * N + x; if (vu.has(o) || !(m[o] & (1 << ((d + 2) % 4)))) continue; vu.add(o); file.push(o); } } return lamps.every(l => vu.has(l)); };
  // recherche complete : tuile par tuile, en abandonnant des que les lampes sont hors d'atteinte meme si les tuiles restantes s'ouvraient partout
  let tours = null;
  const essai = mask.slice(), choix = new Array(16).fill(0);
  const possible = k => gagne(essai.map((v, i) => i < k ? v : (mask[i] ? 15 : 0)));
  const cherche = k => {
    if (k === 16) { if (gagne(essai)) tours = choix.slice(); return; }
    let m = mask[k];
    for (let j = 0; j < (mask[k] ? 4 : 1) && !tours; j++) { essai[k] = m; choix[k] = j; if (possible(k + 1)) cherche(k + 1); m = turn(m); }
    essai[k] = mask[k];
  };
  cherche(0);
  if (!tours) { console.log('pas de solution trouvee'); bot.quit(); return process.exit(1); }
  let clics = 0;
  for (let t = 0; t < 16; t++) for (let j = 0; j < tours[t] && mask[t]; j++) {
    const tu = LEFT + (t % N + 0.5) * CELL, tv = TOP - (Math.floor(t / N) + 0.5) * CELL;
    await bot.lookAt(new Vec3(p.x - tu, p.y + 2.0 + tv, p.z + 3.5), true); await sleep(300); await bot.look(bot.entity.yaw, bot.entity.pitch, true); await sleep(300);
    const avant = Object.keys(couleur).length; let n0 = recus; bot.swingArm('right'); clics++; await sleep(300); if (recus === n0) console.log('  clic sans effet sur la tuile', t);
  }
  await sleep(1500);
  const attendu = mask.map((v, i) => { let x = v; for (let j = 0; j < tours[i]; j++) x = turn(x); return x; });
  console.log('attendu :', attendu.join(' '), '| tours', tours.map((k, i) => mask[i] ? k : 0).join(''));
  await sleep(1500);
  console.log('clics :', clics, '|', said.filter(t => /nigme/.test(t)).join(' | ') || 'pas de message de victoire');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
