// Animations RWBY en troisieme personne : Yang (Ember Celica), Ruby (tir, ruee, Yeux d'Argent), Weiss (Glyphs 1 et 2, six Dusts).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const only = process.argv[2] || 'all';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let bars = []; bot._client.on('action_bar', d => bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
bot.on('death', () => { console.log('  (le testeur est mort)'); setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const models = {}; const transforms = {};
bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const x = JSON.stringify(m.value || '').match(/crossfight:[a-z_\/]+/); if (x) models[d.entityId] = x[0]; } transforms[d.entityId] = (transforms[d.entityId] || 0) + 1; });
let particles = {}; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).match(/type":"?([a-z_]+)/); const n = k ? k[1] : '?'; particles[n] = (particles[n] || 0) + 1; } });
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const displays = (re) => Object.values(bot.entities).filter(e => e.name === 'item_display' && re.test(models[e.id] || ''));
const summary = (re) => { const c = {}; for (const e of displays(re)) { const k = models[e.id].replace('crossfight:', ''); c[k] = (c[k] || 0) + 1; } return Object.entries(c).map(([k, v]) => v > 1 ? k + 'x' + v : k).join(',') || '(rien)'; };
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
async function use(txt) {
  const s = slotOf(txt); if (s < 0) { console.log('  objet introuvable :', txt); return false; }
  bot.setQuickBarSlot(s); await sleep(250); bars = [];
  bot.activateItem(); await sleep(80); bot.deactivateItem(); return true;
}
async function face() { const b = body(); if (b) await bot.lookAt(b.position.offset(0, 1.5, 0), true); }
async function become(perso, z) {
  bot.chat('/cf test third-person ' + perso); await sleep(5000);
  bot.chat(`/tp @s 0.5 65 ${z} 180 0`); bot.chat('/cf heal'); await sleep(800); await face();
}
const hp = () => bot.health;
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  if (only === 'all' || only === 'yang') {
    console.log('== Yang');
    await become('yang', 3.5);
    const h0 = hp(); await use('Ember Celica'); await sleep(350);
    console.log('  pantin :', summary(/pose\/yang/));
    await sleep(900);
    console.log('  sante', h0, '->', hp(), '| feu ?', bot.entity.metadata?.[0], '| barre :', bars.filter(Boolean).join(' / ').slice(0, 90));
    console.log('  particules :', JSON.stringify(particles).slice(0, 200)); particles = {};
    await sleep(1500);
  }
  if (only === 'all' || only === 'ruby') {
    console.log('== Ruby');
    await become('ruby', 6);
    await use('Crescent Rose'); await sleep(400);
    console.log('  tir 1, pantin :', summary(/pose\/ruby/));
    await use('Crescent Rose'); await sleep(300); await use('Crescent Rose'); await sleep(300);
    console.log('  tirs 2-3, pantin :', summary(/pose\/ruby/), '| particules :', JSON.stringify(particles).slice(0, 160)); particles = {};
    await sleep(2500);
    console.log('  apres les tirs (le pantin doit avoir disparu) :', summary(/pose\/ruby/));
    bot.chat('/tp @s 0.5 65 12 180 0'); await sleep(600); await face();
    const before = {}; for (const e of displays(/pose\/ruby/)) before[e.id] = transforms[e.id];
    await use('Petales (rapide)'); await sleep(250);
    const ids = displays(/pose\/ruby/).map(e => e.id); const t0 = {}; for (const id of ids) t0[id] = transforms[id] || 0;
    let maxY = -1e9; const bb = body(); const y0 = bb ? bb.position.y : 0;
    for (let i = 0; i < 12; i++) { await sleep(50); const b = body(); if (b) maxY = Math.max(maxY, b.position.y); }
    const upd = ids.map(id => (transforms[id] || 0) - t0[id]);
    console.log('  ruee : pantin', ids.length, 'parties, mises a jour pendant la vrille', JSON.stringify(upd), '| decollage', (maxY - y0).toFixed(2), 'bloc(s)');
    await sleep(2500);
    // Yeux d'Argent : on fait tomber le mannequin sous 5 coeurs.
    bot.chat('/tp @s 0.5 65 3 180 0'); await sleep(1200); await face(); bot.chat('/cf heal');
    const h1 = hp(); particles = {};
    for (let i = 0; i < 4; i++) { const b = body(); if (!b) break; bot.setQuickBarSlot(Math.max(0, slotOf('Crescent Rose'))); await sleep(150); await face(); bot.attack(b); await sleep(700); if (/pose\/ruby_yeux|pose\/ruby/.test(summary(/pose\/ruby/))) break; }
    await sleep(200);
    console.log('  yeux : pantin', summary(/pose\/ruby/));
    await sleep(1500);
    console.log('  yeux : sante du testeur', h1, '->', hp(), '| particules', JSON.stringify(particles).slice(0, 220));
    await sleep(2000);
  }
  if (only === 'all' || only === 'weiss') {
    console.log('== Weiss');
    await become('weiss', 5);
    const dusts = ['Rouge', 'Bleu', 'Jaune', 'Cyan', 'Blanche', 'Violette'];
    for (let d = 0; d < 6; d++) {
      if (d > 0) { await use('Myrtenaster'); await sleep(600); }
      bot.chat('/tp @s 0.5 65 5 180 0'); bot.chat('/cf heal'); await sleep(900); await face();
      await use('Glyphs (Type 1)'); await sleep(700);
      const p1 = summary(/pose\/weiss/), g1 = summary(/glyph\//);
      await sleep(1200);
      console.log(`  ${dusts[d]} type 1 : pantin ${p1} | signes ${g1} -> ${summary(/glyph\//)} | barre ${bars.filter(Boolean).join(' / ').slice(0, 70)}`);
      if (d === 1 || d === 4 || d === 5) {
        // Le pouvoir part au prochain coup du mannequin : le signe dans son dos doit s'effacer.
        bot.chat('/tp @s 0.5 65 2.5 180 0'); await sleep(700); await face();
        const w = slotOf('Myrtenaster'); bot.setQuickBarSlot(w); bot.setControlState('sneak', true); await sleep(200);
        await bot.lookAt(bot.entity.position.offset(0, 1.6, 5), true); bot.swingArm(); await sleep(150); bot.setControlState('sneak', false);
        await sleep(1000);
        console.log(`    coup du mannequin -> signes ${summary(/glyph\//)}`);
        bot.chat('/cf clear'); await sleep(500);
      }
      bot.chat('/tp @s 0.5 65 6 180 0'); bot.chat('/cf heal'); await sleep(900); await face();
      const h = hp(); const y = bot.entity.position.y;
      await use('Glyphs (Type 2)'); await sleep(600);
      const p2 = summary(/pose\/weiss/), g2 = summary(/glyph\//);
      await sleep(900); const ymid = bot.entity.position.y;
      await sleep(800);
      console.log(`  ${dusts[d]} type 2 : pantin ${p2} | signes ${g2} | sante ${h} -> ${hp()} | y ${y.toFixed(1)} -> ${ymid.toFixed(1)} -> ${bot.entity.position.y.toFixed(1)} | barre ${bars.filter(Boolean).join(' / ').slice(0, 70)}`);
      await sleep(d === 3 ? 13500 : 11500);
      bot.chat('/cf clear'); await sleep(400);
    }
  }
  bot.chat('/cf test third-person'); await sleep(1000);
  bot.quit(); process.exit(0);
});
