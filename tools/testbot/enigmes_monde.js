// L'etabli et le puzzle des piliers (/cf enigme etabli, /cf enigme puzzle) : le bot lit le tableau sur les affichages,
// refait la recette ou reforme l'image en visant et en cliquant, et attend « Énigme résolue ».
// node enigmes_monde.js <etabli|puzzle>
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const RECETTES = { iron_pickaxe: ['III', ' S ', ' S '], diamond_sword: ['D', 'D', 'S'], iron_axe: ['II', 'IS', ' S'], bow: [' SW', 'S W', ' SW'], arrow: ['F', 'S', 'P'], fishing_rod: ['  S', ' SW', 'S W'], bucket: ['I I', ' I '],
  iron_helmet: ['III', 'I I'], diamond_chestplate: ['D D', 'DDD', 'DDD'], iron_boots: ['I I', 'I I'], shield: ['PIP', 'PPP', ' P '], chest: ['PPP', 'P P', 'PPP'], furnace: ['CCC', 'C C', 'CCC'], ladder: ['S S', 'SSS', 'S S'],
  torch: ['C', 'S'], bookshelf: ['PPP', 'BBB', 'PPP'], enchanting_table: [' B ', 'DOD', 'OOO'], anvil: ['KKK', ' I ', 'III'], golden_apple: ['GGG', 'GAG', 'GGG'], compass: [' I ', 'IRI', ' I '], clock: [' G ', 'GRG', ' G '],
  tnt: ['PSP', 'SPS', 'PSP'], bread: ['WWW'], cake: ['MMM', 'SES', 'WWW'] };
const CLES = { iron_pickaxe: { I: 'iron_ingot', S: 'stick' }, diamond_sword: { D: 'diamond', S: 'stick' }, iron_axe: { I: 'iron_ingot', S: 'stick' }, bow: { S: 'stick', W: 'string' }, arrow: { F: 'flint', S: 'stick', P: 'feather' },
  fishing_rod: { S: 'stick', W: 'string' }, bucket: { I: 'iron_ingot' }, iron_helmet: { I: 'iron_ingot' }, diamond_chestplate: { D: 'diamond' }, iron_boots: { I: 'iron_ingot' }, shield: { P: 'oak_planks', I: 'iron_ingot' },
  chest: { P: 'oak_planks' }, furnace: { C: 'cobblestone' }, ladder: { S: 'stick' }, torch: { C: 'coal', S: 'stick' }, bookshelf: { P: 'oak_planks', B: 'book' }, enchanting_table: { B: 'book', D: 'diamond', O: 'obsidian' },
  anvil: { K: 'iron_block', I: 'iron_ingot' }, golden_apple: { G: 'gold_ingot', A: 'apple' }, compass: { I: 'iron_ingot', R: 'redstone' }, clock: { G: 'gold_ingot', R: 'redstone' }, tnt: { P: 'gunpowder', S: 'sand' },
  bread: { W: 'wheat' }, cake: { M: 'milk_bucket', S: 'sugar', E: 'egg', W: 'wheat' } };
(async () => {
  const quoi = process.argv[2];
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  const fond = {}, objet = {}; let said = [];
  bot._client.on('entity_metadata', d => { for (const m of d.metadata) { if (m.key === 25 && typeof m.value === 'number') fond[d.entityId] = m.value | 0; if (m.type === 'item_stack' || (m.value && typeof m.value === 'object' && 'itemId' in m.value)) objet[d.entityId] = m.value.itemId; } });
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/gamemode creative'); bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  const p = bot.entity.position.clone();
  bot.chat('/cf enigme ' + quoi); await sleep(2500);
  const uv = e => [-(e.position.x - p.x), e.position.y - (p.y + 2)];
  // le serveur oriente lui-meme le bot (son propre regard arrive parfois en retard) : yeux a 1,62 au-dessus des pieds
  const clic = async (u, v) => { const dx = -u, dy = 2.0 + v - 1.62, dz = 3.5; bot.chat('/tp @s ' + p.x + ' ' + p.y + ' ' + p.z + ' ' + (-Math.atan2(dx, dz) * 180 / Math.PI).toFixed(3) + ' ' + (-Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI).toFixed(3)); await sleep(450); bot.swingArm('right'); await sleep(350); };
  let clics = 0;
  if (quoi === 'etabli') {
    const vus = Object.values(bot.entities).filter(e => e.name === 'item_display' && objet[e.id]).map(e => ({ nom: bot.registry.items[objet[e.id]].name, u: uv(e)[0], v: uv(e)[1] }));
    const cible = vus.find(o => Math.abs(o.u + 1.35) < 0.1 && Math.abs(o.v - 0.55) < 0.1), tas = vus.filter(o => Math.abs(o.v + 0.95) < 0.1);
    console.log('objet a refaire :', cible && cible.nom, '| ingredients :', tas.map(o => o.nom).join(', '));
    const rangees = RECETTES[cible.nom], cle = CLES[cible.nom];
    for (const [lettre, nom] of Object.entries(cle)) {
      const t = tas.find(o => o.nom === nom); if (!t) { console.log('ingredient absent :', nom); break; }
      await clic(t.u, t.v); clics++;
      for (let y = 0; y < rangees.length; y++) for (let x = 0; x < rangees[y].length; x++) if (rangees[y][x] === lettre) { await clic(-0.05 + (x + 0.5) * 0.5, 1.2 - (y + 0.5) * 0.5); clics++; }
    }
  } else {
    const PX = 0.14, PIECE = 0.56, MINI = 0.045, FL = 0.5, FT = 1.2, HL = -2.2, HT = 1.3, HP = 0.68, ML = FL + (3 * PIECE - 12 * MINI) / 2, MT = -0.78;
    const points = Object.values(bot.entities).filter(e => e.name === 'text_display' && fond[e.id]).map(e => ({ c: fond[e.id], u: uv(e)[0], v: uv(e)[1] }));
    // le modele : 12 x 12 points ; chaque affichage est pose par son bas
    const modele = []; for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) { const u = ML + (x + 0.5) * MINI, v = MT - (y + 0.5) * MINI - (MINI + 0.004) / 2; const q = points.find(o => Math.abs(o.u - u) < 0.012 && Math.abs(o.v - v) < 0.012); modele.push(q ? q.c : null); }
    const tas = []; for (let h = 0; h < 9; h++) { const m = []; for (let k = 0; k < 16; k++) { const u = HL + (h % 3) * HP + (k % 4 + 0.5) * PX, v = HT - Math.floor(h / 3) * HP - (Math.floor(k / 4) + 0.5) * PX - (PX + 0.004) / 2; const q = points.find(o => Math.abs(o.u - u) < 0.03 && Math.abs(o.v - v) < 0.03); m.push(q ? q.c : null); } tas.push(m); }
    console.log('points du modele lus :', modele.filter(c => c != null).length, '/ 144 | pieces lues :', tas.filter(m => m.every(c => c != null)).length, '/ 9');
    const pris = new Set();
    for (let s = 0; s < 9; s++) {
      const voulu = []; for (let k = 0; k < 16; k++) voulu.push(modele[(Math.floor(s / 3) * 4 + Math.floor(k / 4)) * 12 + (s % 3) * 4 + k % 4]);
      const h = tas.findIndex((m, i) => !pris.has(i) && m.every((c, k) => c === voulu[k]));
      if (h < 0) { console.log('aucune piece pour la case', s); continue; }
      pris.add(h);
      await clic(HL + (h % 3) * HP + PIECE / 2, HT - Math.floor(h / 3) * HP - PIECE / 2); await clic(FL + (s % 3 + 0.5) * PIECE, FT - (Math.floor(s / 3) + 0.5) * PIECE); clics += 2;
    }
  }
  await sleep(3000);
  console.log('clics :', clics, '|', said.filter(t => /nigme/.test(t)).join(' | ') || 'pas de message de victoire');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
