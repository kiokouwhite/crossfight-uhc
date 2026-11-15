// Ice Flower (Ruby et Weiss) : Weiss plante son epee, la glace s'etend, Ruby court autour puis revient a sa place.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let chat = []; bot.on('messagestr', m => chat.push(m));
const kind = {}; bot._client.on('entity_metadata', p => { const m = JSON.stringify(p.metadata).match(/crossfight:pose\/([a-z]+)_/); if (m) kind[p.entityId] = m[1]; });
let dust = 0; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') dust++; });
const text = i => (JSON.stringify(i.customName || '').match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join('');
const ice = () => { let n = 0; const p = bot.entity.position; for (let x = -26; x <= 26; x += 2) for (let z = -26; z <= 26; z += 2) { const b = bot.blockAt(new (require('vec3'))(Math.floor(x), 64, Math.floor(z))); if (b && b.name === 'packed_ice') n++; } return n; };
const pup = () => { const c = {}; for (const e of Object.values(bot.entities)) if (e.name === 'item_display' && kind[e.id]) c[kind[e.id]] = (c[kind[e.id]] || 0) + 1; return JSON.stringify(c); };
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/cf change ruby'); await sleep(4000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/cf test duo'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  const start = bot.entity.position.clone();
  const s = bot.inventory.slots.slice(36, 45).findIndex(i => i && text(i).includes('Ultime en Duo'));
  bot.setQuickBarSlot(s); await sleep(300); bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(900);
  const w = bot.currentWindow; const slot = w.slots.findIndex(i => i && text(i).includes('Ice Flower'));
  dust = 0; await bot.clickWindow(slot, 0, 0); const t0 = Date.now();
  let far = 0;
  for (let i = 0; i < 12; i++) { await sleep(200); const d = Math.hypot(bot.entity.position.x - start.x, bot.entity.position.z - start.z); far = Math.max(far, d);
    console.log(((Date.now() - t0) / 1000).toFixed(1) + ' s : glace', ice(), '| pantins', pup(), '| Ruby a', d.toFixed(1), 'blocs de son depart'); }
  console.log('particules', dust, '| plus loin', far.toFixed(1), '| message', chat.filter(m => /lancent/.test(m)).pop());
  bot.chat('/cf clear'); await sleep(800); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
