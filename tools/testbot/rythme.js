// Le concert d'Anna : le bot termine la quete de Shiganshina, ouvre le menu, joue des musiques en frappant chaque note
// quand elle touche la ligne (il lit les affichages qui apparaissent), et note ce qu'il gagne.
// node rythme.js <monde> <case du menu>:<part des notes jouees> ...
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], win = null, apparus = [];
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  bot.on('windowOpen', w => { win = w; });
  bot.on('entitySpawn', e => { if (e.name === 'text_display') apparus.push({ x: e.position.x, z: e.position.z, t: Date.now() }); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/clear'); bot.chat('/cf quete oublier shiganshina'); await sleep(500); bot.chat('/cf tp quete shiganshina'); await sleep(5000);
  bot.chat('/cf quete finir shiganshina'); await sleep(1500);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  const stock = () => ['iron_ingot', 'gold_ingot', 'diamond'].map(n => n.split('_')[0] + ' ' + bot.inventory.items().filter(i => i.name === n).reduce((s, i) => s + i.count, 0)).join(', ');
  for (const essai of process.argv.slice(3)) {
    const [caseMenu, part] = essai.split(':').map(Number), chute = [40, 32, 26][Math.floor(caseMenu / 9)];
    win = null; bot.chat('/cf quete parler shiganshina'); await sleep(1200);
    if (!win) { console.log('pas de menu |', said.slice(-2).join(' | ').slice(0, 160)); break; }
    const nom = win.slots[caseMenu] ? flat(win.slots[caseMenu].customName || '') || win.slots[caseMenu].name : '?';
    apparus = []; said = []; await bot.clickWindow(caseMenu, 0, 0).catch(() => {}); await sleep(1500);
    // le tableau vient d'apparaitre : le fond, puis pour chaque colonne sa zone et son chiffre
    const colonnes = [1, 3, 5, 7].map(i => apparus[i]).filter(Boolean);   // sans le pack : le fond, puis (zone, chiffre) par colonne
    if (colonnes.length < 4) { console.log(nom, ': tableau incomplet (' + apparus.length + ' affichages)'); continue; }
    let vu = colonnes.length * 2 + 1, frappes = 0, n = 0, dernier = null;
    const fin = Date.now() + Number(process.env.DUREE || 70) * 1000;
    while (Date.now() < fin && !said.some(t => /%/.test(t))) {
      while (vu < apparus.length) {
        const a = apparus[vu++];
        let voie = 0; for (let k = 1; k < 4; k++) if (Math.hypot(a.x - colonnes[k].x, a.z - colonnes[k].z) < Math.hypot(a.x - colonnes[voie].x, a.z - colonnes[voie].z)) voie = k;
        // UNE=1 : comme le vrai jeu, qui n'envoie qu'une touche quand deux sont enfoncees au meme instant (la plus haute)
        if (process.env.UNE && dernier && a.t - dernier.t < 30) { n++; if (voie > dernier.voie) { clearTimeout(dernier.minuterie); dernier.voie = voie; dernier.minuterie = setTimeout(() => bot._client.write('held_item_slot', { slotId: voie }), Math.max(0, a.t + chute * 50 - Date.now())); } continue; }
        if ((n++ * 37 % 100) / 100 < part) { frappes++; dernier = { t: a.t, voie, minuterie: setTimeout(() => bot._client.write('held_item_slot', { slotId: voie }), Math.max(0, a.t + chute * 50 - Date.now())) }; }
      }
      await sleep(10);
    }
    await sleep(1500);
    console.log(nom, '(' + frappes + ' frappes sur ' + n + ' notes) :', said.filter(t => /%|Note|déjà|Raté/.test(t)).join(' | ').slice(0, 330), '=>', stock());
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
