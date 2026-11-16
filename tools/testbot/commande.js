// Envoie une commande au nom d'un joueur et affiche ce que le serveur lui repond pendant quelques secondes.
// Usage : node commande.js <joueur> <secondes> <commande sans le />
const mineflayer = require('mineflayer');
const [name, secs, ...cmd] = process.argv.slice(2);
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', () => setTimeout(() => {
  bot._client.on('system_chat', m => { if (!m.isActionBar) console.log('>', flat(m.content)); });
  bot.chat('/' + cmd.join(' '));
  setTimeout(() => { console.log('position :', bot.game.dimension, bot.entity.position.floored().toString()); bot.quit(); process.exit(0); }, +secs * 1000);
}, 2500));
