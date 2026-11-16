// Les services des lieux : le bot (admin) va a la borne d'un lieu, clique dessus, et dit ce qu'il a recu.
// Usage : node lieux_services.js <monde> <x> <z> [<x> <z> ...]   (le milieu de chaque lieu ; la partie de test doit tourner)
const mineflayer = require('mineflayer'); const { execSync } = require('child_process');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const console_ = c => execSync(`timeout 5 sh -c "echo '${c}' > /home/user/testserver/console.in"`);
const [world, ...nums] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let bar = '';
bot._client.on('action_bar', m => { bar = flat(m.text); });
bot._client.on('system_chat', m => { if (m.isActionBar) bar = flat(m.content); });
const items = () => bot.inventory.items().map(i => `${i.count} ${i.name}${i.enchants && i.enchants.length ? ' [' + i.enchants.map(e => e.name + ' ' + e.lvl).join(', ') + ']' : ''}`).join(' ; ');
bot.once('spawn', async () => {
  await sleep(3000);
  console_('gamemode survival Invite'); console_('xp set Invite 30 levels'); console_('clear Invite');
  console_('give Invite iron_sword'); console_('give Invite gold_ingot 12'); await sleep(1000);
  for (let i = 0; i + 1 < nums.length; i += 2) {
    console_(`execute in minecraft:${world} run tp Invite ${nums[i]} 105 ${nums[i + 1]}`); console_('effect give Invite slow_falling 30 1 true'); console_('effect give Invite resistance 30 4 true');
    await sleep(2500);
    // On se pose sur la borne : elle est la ou le serveur met ses entites.
    await sleep(3500);
    const mark = Object.values(bot.entities).find(e => e.name === 'interaction');
    if (!mark) { console.log(nums[i], nums[i + 1], ': pas de borne vue ; entites :', [...new Set(Object.values(bot.entities).map(e => e.name))].join(',')); continue; }
    console_(`execute in minecraft:${world} run tp Invite ${mark.position.x} ${mark.position.y} ${mark.position.z + 1.5}`); await sleep(1500);
    const label = Object.values(bot.entities).find(e => e.name === 'text_display' && e.position.distanceTo(mark.position) < 4);
    const before = items(), level = bot.experience.level; bar = '';
    const sword = bot.inventory.items().find(i => i.name === 'iron_sword'); if (sword) await bot.equip(sword, 'hand');
    await bot.lookAt(mark.position.offset(0, 1, 0), true);
    bot.useOn(mark); await sleep(1200);
    const win = bot.currentWindow;
    if (win) { console.log('   menu :', win.title ? flat(win.title) : '?', '|', win.slots.slice(0, 9).filter(Boolean).map(s => s.count + ' ' + s.name).join(', ')); await bot.clickWindow(2, 0, 0); await sleep(600); bot.closeWindow(win); await sleep(400); }
    console.log(`borne en ${mark.position.floored()} | barre : "${bar}" | niveaux ${level} -> ${bot.experience.level}\n   avant : ${before}\n   apres : ${items()}`);
  }
  bot.quit(); process.exit(0);
});
