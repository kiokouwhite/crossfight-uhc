// La chasse aux pieces : le bot (admin) va dans le lieu, repere les pieces qu'il voit, marche sur deux d'entre elles.
// Usage : node chasse.js <monde> <x> <y> <z>   (un point au-dessus du milieu du lieu)
const mineflayer = require('mineflayer'); const { execSync } = require('child_process');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const console_ = c => execSync(`timeout 5 sh -c "echo '${c}' > /home/user/testserver/console.in"`);
const [world, x, y, z] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot._client.on('system_chat', m => { if (!m.isActionBar) console.log('  >', flat(m.content)); });
bot.once('spawn', async () => {
  await sleep(3000);
  console_('gamemode creative Invite'); console_('clear Invite');
  console_(`execute in minecraft:${world} run tp Invite ${x} ${y} ${z}`); await sleep(5000);
  for (let n = 0; n < 3; n++) {
    const seen = Object.values(bot.entities).filter(e => e.name === 'item_display');
    console.log('pieces vues :', seen.map(e => e.position.floored().toString()).join(' '));
    if (!seen.length) break;
    const t = seen[0].position;
    console_(`execute in minecraft:${world} run tp Invite ${t.x} ${t.y} ${t.z}`); await sleep(2500);
    console.log('inventaire :', bot.inventory.items().map(i => i.count + ' ' + i.name).join(', '));
  }
  bot.quit(); process.exit(0);
});
