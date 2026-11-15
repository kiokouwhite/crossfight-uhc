// Le lobby agrandi dont le verre change de couleur sous les pas, la Force a +1 par niveau, les minerais
// multiplies et /cf regenere (une nouvelle map, meme graine, avec puis sans minerais multiplies).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const where = () => bot.entity.position.x.toFixed(1) + ' ' + bot.entity.position.y.toFixed(1) + ' ' + bot.entity.position.z.toFixed(1);
const chat = []; bot.on('messagestr', m => chat.push(m));
function floor() {
  const p = bot.entity.position.floored(); const count = {}; let side = 0;
  for (let x = -70; x <= 70; x++) { let row = 0; for (let z = -70; z <= 70; z++) { const b = bot.blockAt(p.offset(x, -1, z)); if (b && b.name !== 'air') { count[b.name] = (count[b.name] || 0) + 1; row++; } } side = Math.max(side, row); }
  return { side, count };
}
async function ores() {
  // Par tranches, pour laisser le bot repondre au serveur pendant le compte.
  const { Vec3 } = require('vec3'); const p = bot.entity.position.floored(); const count = {}; let total = 0;
  for (let x = -32; x < 32; x++) {
    for (let z = -32; z < 32; z++) for (let y = -64; y < 120; y++) {
      const b = bot.blockAt(new Vec3(p.x + x, y, p.z + z)); if (b && b.name.endsWith('_ore')) { const n = b.name.replace('deepslate_', ''); count[n] = (count[n] || 0) + 1; total++; }
    }
    await sleep(5);
  }
  return { total, count };
}
// Les points d'attaque du joueur, lus par la commande /attribute.
const damage = async () => { chat.length = 0; bot.chat('/attribute @s minecraft:attack_damage get'); await sleep(700); return chat.filter(m => m.includes('attribute')).pop(); };
bot.once('spawn', async () => {
  await sleep(5000);
  let f = floor(); const p0 = bot.entity.position.floored();
  console.log('arrivee :', where(), '| monde', bot.game.dimension, '| cote du sol', f.side, JSON.stringify(f.count));
  await bot.look(0.6, 0, true); bot.setControlState('forward', true); await sleep(3000); bot.setControlState('forward', false); await sleep(600);
  f = floor(); console.log('apres 3 s de marche :', where(), JSON.stringify(f.count));
  // La Force.
  console.log('sans Force :', await damage());
  bot.chat('/effect give @s minecraft:strength 30 0'); await sleep(1000); console.log('Force I  :', await damage());
  bot.chat('/effect give @s minecraft:strength 30 1'); await sleep(1000); console.log('Force II :', await damage());
  bot.chat('/cf regle force 3'); await sleep(1000); console.log('regle force 3 :', await damage());
  bot.chat('/cf regle force 1'); await sleep(1000); bot.chat('/effect clear @s'); await sleep(1000); console.log('effet retire :', await damage());
  // Les minerais : meme graine, sans puis avec.
  for (const times of [1, 5]) {
    bot.chat('/cf regle minerais ' + times); await sleep(800);
    bot.chat('/cf regenere 424242'); for (let i = 0; i < 80 && !chat.some(m => m.includes('Nouvelle map prete')); i++) await sleep(500);
    chat.length = 0; await sleep(9000);
    const o = await ores(); console.log('minerais x' + times + ' :', where(), '| total', o.total, JSON.stringify(o.count), '|', chat.slice(-1)[0]);
  }
  f = floor(); console.log('lobby de la nouvelle map : cote', f.side, JSON.stringify(f.count));
  bot.quit(); process.exit(0);
});
