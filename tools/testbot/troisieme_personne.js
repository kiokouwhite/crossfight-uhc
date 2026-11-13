// Troisieme personne, 2e passe : chaque objet de chaque personnage, avec les refus (messages rouges) et les annonces.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
// Texte brut d'un composant (JSON ou NBT), et sa couleur de tete.
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (Array.isArray(c)) return c.map(flat).join(''); if (c.type && c.value !== undefined) return flat(c.value); let s = ''; for (const k of Object.keys(c)) { if (k === 'text' || k === '') s += flat(c[k]); } if (c.extra) s += flat(c.extra); return s; };
const firstColor = c => JSON.stringify(c).match(/"color"[^"]*"(?:value"[^"]*")?([a-z_#0-9]+)"/);
let events = []; // {kind, text, red}
bot._client.on('action_bar', d => { const red = /"color".{0,40}"red"/.test(JSON.stringify(d.text).slice(0, 300)); events.push({ kind: 'bar', text: flat(d.text).slice(0, 110), red }); });
bot._client.on('system_chat', d => { const red = /"color".{0,40}"red"/.test(JSON.stringify(d.content).slice(0, 500)); events.push({ kind: d.isActionBar ? 'bar' : 'chat', text: flat(d.content).slice(0, 140), red }); });
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(11)); } });
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const only = process.argv[2] ? process.argv[2].split(',') : null;
bot.once('spawn', async () => {
  await sleep(3000); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(800);
  let ids = (await bot.tabComplete('/cf change ')).map(x => typeof x === 'string' ? x : x.match);
  if (only) ids = ids.filter(i => only.includes(i));
  bot.chat('/cf test third-person ' + ids[0]); await sleep(5000);
  const results = [];
  for (const id of ids) {
    bot.chat('/cf clear'); await sleep(800);
    bot.chat('/cf change ' + id); await sleep(1500);
    const line = [];
    for (let k = 0; k <= 8; k++) {
      const s = bot.inventory.slots[36 + k]; if (!s || s.name === 'golden_apple') continue;
      if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
      bot.chat('/tp @s 0.5 65 4 180 0'); bot.chat('/cf heal'); await sleep(500);
      const m = body(); if (m) await bot.lookAt(m.position.offset(0, 1.5, 0), true);
      bot.setQuickBarSlot(k); await sleep(200);
      sounds = []; events = []; const hp = bot.health;
      bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(1700);
      let menu = '';
      if (bot.currentWindow) { const w = bot.currentWindow; const idx = w.slots.findIndex((x, i) => x && i < w.inventoryStart); menu = ' [menu]'; if (idx >= 0) await bot.clickWindow(idx, 0, 0); else bot.closeWindow(w); await sleep(1200); }
      const redTxt = events.filter(e => e.red).map(e => e.text).join(' / ');
      const used = events.some(e => / utilise /.test(e.text));
      const ab = [...new Set(sounds.filter(x => x.startsWith('ability.') || x.startsWith('voice.')))].length;
      const all = [...new Set(events.map(e => e.text.trim()).filter(t => t && !/\|/.test(t)))].join(' / ');
      line.push(k + ':' + s.name + (redTxt ? ' REFUS<' + redTxt.slice(0, 90) + '>' : ab || used ? ' ok' : ' ?<' + all.slice(0, 110) + '>') + (bot.health < hp - 0.05 ? ' -' + (hp - bot.health).toFixed(1) : '') + menu);
    }
    // Accroupi + clic gauche dans le vide avec l'arme (dos au mannequin) : il frappe (ou tire).
    bot.chat('/tp @s 0.5 65 3 180 0'); bot.chat('/cf heal'); await sleep(700);
    bot.setQuickBarSlot(0); await sleep(250); await bot.look(Math.PI, 0, true); const hp = bot.health; events = [];
    bot.setControlState('sneak', true); await sleep(300); bot.swingArm(); await sleep(1000); bot.setControlState('sneak', false);
    line.push('frappe:' + (bot.health < hp - 0.05 ? '-' + (hp - bot.health).toFixed(1) : 'rien ' + events.map(e => e.text).join('/').slice(0, 60)));
    results.push(id + ' | ' + line.join(' | '));
    log(results[results.length - 1]);
  }
  console.log('\n=== RESUME ===\n' + results.join('\n'));
  bot.quit(); process.exit(0);
});
