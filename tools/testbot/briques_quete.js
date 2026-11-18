// Le casse-briques dans la quete d'Arcadia : parler a Anna lance la quete et dresse le tableau ; les pommes rattrapees
// font avancer la quete de l'equipe.
// node briques_quete.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], bar = '', compte = '';
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) bar = t; else said.push(t); });
  bot._client.on('entity_metadata', d => { for (const m of d.metadata) if (m.key === 23) { const t = flat(m.value); if (/Pommes/.test(t)) compte = t; } });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier arcadia'); await sleep(500);
  bot.chat('/cf quete tp arcadia'); await sleep(5000);
  const anna = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
  const affichages = () => Object.values(bot.entities).filter(e => e.name === 'text_display').length;
  const avant = affichages();
  said = []; bot.useOn(anna); await sleep(2000);
  console.log('Anna :', (said.find(t => /Anna/.test(t)) || '').slice(0, 150));
  console.log('tableau :', affichages() - avant, 'affichages |', compte);
  const fin = Date.now() + 25000; let clic = 0;
  while (Date.now() < fin) { if (Date.now() - clic > 1500) { bot.swingArm('right'); clic = Date.now(); } await sleep(100); }
  console.log('apres 25 s sans bouger :', compte, '| barre :', bar);
  bot.setControlState('sneak', true); await sleep(500); bot.setControlState('sneak', false); await sleep(1000);
  console.log('ferme :', affichages() - avant, 'affichages restants');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
