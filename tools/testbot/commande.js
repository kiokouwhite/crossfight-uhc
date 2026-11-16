// Envoie une ou plusieurs commandes au nom d'un joueur et affiche ce que le serveur lui repond.
// Usage : node commande.js <joueur> <secondes entre deux commandes> <commande sans le /> [;; <commande> ...]
const mineflayer = require('mineflayer');
const [name, secs, ...rest] = process.argv.slice(2);
const cmds = rest.join(' ').split(';;').map(c => c.trim()).filter(Boolean);
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', async () => {
  await sleep(2500);
  bot._client.on('system_chat', m => { if (!m.isActionBar) console.log('  >', flat(m.content)); });
  for (const c of cmds) {
    console.log('/' + c);
    bot.chat('/' + c);
    await sleep(+secs * 1000);
  }
  console.log('position :', bot.game.dimension, bot.entity.position.floored().toString());
  bot.quit(); process.exit(0);
});
