// /cf test duo-third-person : le mannequin du centre a ses coequipieres a cote de lui et lance ses ultimes en duo sur le testeur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let chat = []; bot.on('messagestr', m => chat.push(m));
const text = i => (JSON.stringify(i.customName || '').match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join('');
const lore = i => { const l = (i.components || []).find(c => c.type === 'lore'); return l ? (JSON.stringify(l.data).match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join(' ') : ''; };
const names = () => Object.values(bot.entities).filter(e => e.name === 'mannequin').map(e => (JSON.stringify(e.metadata || '').match(/"text":"([^"]+)"|"value":"([A-Z][^"]{2,})"/g) || []).join('').replace(/"text":|"value":|"/g, '').slice(0, 40)).join(' ; ');
async function ult() {
  const s = bot.inventory.slots.slice(36, 45).findIndex(i => i && text(i).includes('Ultime en Duo'));
  if (s < 0) return console.log('  pas d\'objet Ultime en Duo :', bot.inventory.slots.slice(36, 45).map(i => i ? text(i) || i.name : '-').join(','));
  bot.setQuickBarSlot(s); await sleep(300); bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(900);
  const w = bot.currentWindow; if (!w) return console.log('  pas de menu');
  const items = w.slots.slice(0, 9).map((i, n) => i ? [n, i] : null).filter(Boolean);
  console.log('  menu :', items.map(([n, i]) => text(i).replace(/light_purple|gray/, '') + (/Pret/.test(lore(i)) ? ' (pret)' : ' (non)')).join(', '));
  const ready = items.find(([n, i]) => /Pret/.test(lore(i)));
  if (ready) { chat = []; await bot.clickWindow(ready[0], 0, 0); await sleep(1200); console.log('  lancement :', chat.filter(m => /lancent/.test(m)).pop()); }
}
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test duo-third-person ruby'); await sleep(7000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  console.log('mannequins :', Object.values(bot.entities).filter(e => e.name === 'mannequin').length, '|', chat.filter(m => /duo/i.test(m)).pop());
  await ult();
  bot.chat('/cf change yang'); await sleep(4000); console.log('en Yang : mannequins', Object.values(bot.entities).filter(e => e.name === 'mannequin').length); await ult();
  bot.chat('/cf clear'); await sleep(2500); console.log('apres /cf clear : mannequins', Object.values(bot.entities).filter(e => e.name === 'mannequin').length);
  bot.chat('/cf test duo-third-person'); await sleep(2500); console.log('sortie : mannequins', Object.values(bot.entities).filter(e => e.name === 'mannequin').length, '|', chat.slice(-1)[0]);
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
