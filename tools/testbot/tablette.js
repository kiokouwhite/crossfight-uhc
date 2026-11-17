// La quete des quatre piliers (Central Town) : le bot ouvre chaque tablette, lit la figure et les pieces, cherche
// une solution (toutes les poses possibles) et la joue. Les quatre piliers resolus, la quete est terminee.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const turn = s => { const h = Math.max(...s.map(c => c[1])) + 1; return s.map(c => [h - 1 - c[1], c[0]]); };
const W = s => Math.max(...s.map(c => c[0])) + 1, H = s => Math.max(...s.map(c => c[1])) + 1;
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], win = null;
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  bot.on('windowOpen', w => { win = w; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier central_town'); await sleep(500);
  bot.chat('/cf quete tp central_town'); await sleep(5000);
  bot.chat('/cf quete lancer central_town'); await sleep(2000);
  const lore = it => { const c = (it.components || []).find(c => c.type === 'lore'); return c ? c.data.map(flat) : []; };
  for (let n = 0; n < 4; n++) {
    const lit = Object.values(bot.entities).filter(e => e.name === 'block_display');
    const anna = Object.values(bot.entities).find(e => e.name === 'mannequin');
    if (n === 0) console.log('piliers :', lit.length, '| Anna', anna ? `${anna.position}` : 'absente');
    const pillar = Object.values(bot.entities).filter(e => e.name === 'interaction' && anna && Math.hypot(e.position.x - anna.position.x, e.position.z - anna.position.z) > 4.2 && e.position.distanceTo(anna.position) < 12)[0];
    if (!pillar) { console.log('plus de pilier a portee'); break; }
    bot.chat(`/tp @s ${pillar.position.x + 1.2} ${pillar.position.y} ${pillar.position.z}`); await sleep(1200);
    win = null; bot.useOn(pillar); await sleep(1500);
    if (!win) { console.log('la tablette ne s\'ouvre pas'); break; }
    const target = []; for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) target.push(win.slots[y * 9 + x].name === 'yellow_concrete');
    let pieces = [0, 1, 2].map(i => { const rows = lore(win.slots[45 + i * 2]).filter(l => /[■□]/.test(l)); const s = []; rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '■') s.push([x, y]); })); return s; });
    // toutes les poses : par piece, 4 sens x positions
    const options = pieces.map(p => { const out = []; let s = p; for (let k = 0; k < 4; k++) { for (let y = 0; y + H(s) <= 4; y++) for (let x = 0; x + W(s) <= 4; x++) out.push({ k, x, y, cells: s.map(c => (y + c[1]) * 4 + x + c[0]) }); s = turn(s); } return out; });
    let found = null;
    for (const a of options[0]) { for (const b of options[1]) { for (const c of options[2]) { const g = new Array(16).fill(false); for (const o of [a, b, c]) for (const i of o.cells) g[i] = !g[i]; if (g.every((v, i) => v === target[i])) { found = [a, b, c]; break; } } if (found) break; } if (found) break; }
    if (!found) { console.log('pas de solution trouvee (!)', JSON.stringify(pieces)); break; }
    for (let i = 0; i < 3; i++) {
      await bot.clickWindow(45 + i * 2, 0, 0).catch(() => {}); await sleep(250);
      for (let k = 0; k < found[i].k; k++) { await bot.clickWindow(52, 0, 0).catch(() => {}); await sleep(250); }
      await bot.clickWindow(found[i].y * 9 + 5 + found[i].x, 0, 0).catch(() => {}); await sleep(350);
    }
    await sleep(1200);
    said = []; bot.chat('/cf quete liste'); await sleep(700);
    console.log('pilier', n + 1, ':', (said.join(' | ').split(' | ').find(t => t.includes('central_town')) || '?'));
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
