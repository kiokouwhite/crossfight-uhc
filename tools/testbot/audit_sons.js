const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let sounds = [];
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') { const m = JSON.stringify(data).match(/crossfight:[a-z0-9_.]+/); if (m) sounds.push(m[0].replace('crossfight:', '')); }
});
const take = () => { const s = [...new Set(sounds)]; sounds = []; return s; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const abilityId = it => { try { const s = JSON.stringify(it.components || it.nbt || ''); const m = s.match(/crossfight:ability"[^}]*?"value":"([a-z0-9_]+)"/); return m ? m[1] : it.name; } catch (e) { return it.name; } };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  const chars = process.argv.slice(2);
  for (const c of chars) {
    bot.chat('/cf change ' + c); await sleep(1300); bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(250); bot.chat('/cf mannequin'); await sleep(600);
    const z = zomb(); if (z) { bot.chat('/tp @s ' + (z.position.x + 2.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(350); await bot.lookAt(z.position.offset(0, 1.2, 0), true); }
    take();
    const out = [];
    for (let s = 0; s <= 8; s++) {
      const it = bot.inventory.slots[36 + s]; if (!it || it.name === 'golden_apple') continue;
      if (z && zomb()) await bot.lookAt(zomb().position.offset(0, 1.2, 0), true);
      await click(s, 650);
      const got = take();
      out.push((got.length ? 'OK   ' : 'RIEN ') + s + ' ' + abilityId(it) + (got.length ? ' -> ' + got.join(',') : ''));
    }
    console.log('== ' + c + '\n   ' + out.join('\n   '));
  }
  bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
