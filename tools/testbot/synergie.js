const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
function make(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  b.on('windowOpen', w => b.closeWindow(w));
  b.chatLog = []; b.on('messagestr', m => { b.chatLog.push(m); if (/lancent|Synergie|Lame Electrisante|Dash Double|Talisman Electrifiant|Vol Electrique/.test(m)) log(name, 'CHAT', m.slice(0, 110)); });
  return b;
}
const A = make('Tester'), B = make('Tester2');
const ready = b => new Promise(r => b.once('spawn', r));
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
async function click(b, slot, ms) { b.setQuickBarSlot(slot); await sleep(150); b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(ms); }
async function zHealth(b) { b.chatLog = []; b.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); const m = b.chatLog.filter(x => /entity data/.test(x)).pop(); return m ? m.replace(/.*data: /, '') : '-'; }
(async () => {
  await Promise.all([ready(A), ready(B)]); await sleep(1500);
  if (A.health <= 0) A._client.write('client_command', { actionId: 0 }); if (B.health <= 0) B._client.write('client_command', { actionId: 0 }); await sleep(500);
  await ensureTest(A); await ensureTest(B);
  A.chat('/cf change gunvolt'); B.chat('/cf change kirin'); await sleep(2000);
  B.chat('/cf ally Tester'); await sleep(800); B.chat('/tp @s 2 65 0'); await sleep(500);
  A.chat('/cf mannequin'); await sleep(800);
  const z = Object.values(A.entities).find(e => e.name === 'zombie');
  // Synergie Gunvolt + Kirin : Lame Electrisante (item slot 4 pour Gunvolt)
  await click(A, 4, 800); log('A synergie sans jauge/test :', A.chatLog.filter(m => /Synergie|lancent/.test(m)).pop() || '-');
  A.chat('/cf duo 100'); await sleep(600); await click(A, 4, 800);
  await A.lookAt(z.position.offset(0, 1, 0), true); A.setQuickBarSlot(0); await sleep(200); A.attack(z); await sleep(600);
  log('A frappe pendant Lame Electrisante -> mannequin immobilise ?', A.chatLog.filter(m => /immobilise/.test(m)).pop() || '(message cote mannequin)', '| vie', await zHealth(A));
  // Acura + Kirin : Dash Double Lame
  A.chat('/cf change acura'); await sleep(2000); A.chat('/cf duo 100'); await sleep(500);
  const slotA = A.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'netherite_scrap');
  await click(A, slotA, 800); const p0 = A.entity.position.clone(); await A.lookAt(z.position.offset(0, 1, 0), true); await click(A, slotA, 800);
  log('A dash double lame :', p0.toString(), '->', A.entity.position.toString(), '| bar/chat', A.chatLog.filter(m => /Dash Double/.test(m)).pop() || '-');
  // Kirin + Asimov : Talisman Electrifiant (B lance)
  A.chat('/cf change asimov'); await sleep(2000); B.chat('/cf duo 100'); await sleep(500);
  const slotB = B.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'netherite_scrap');
  await click(B, slotB, 800); await B.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); const pb = B.entity.position.clone(); await click(B, slotB, 1000);
  log('B talisman electrifiant :', pb.toString(), '->', B.entity.position.toString(), '(mannequin', z.position.toString() + ')');
  A.chat('/cf mannequin clear'); await sleep(300); A.chat('/cf test'); B.chat('/cf test'); await sleep(800); A.quit(); B.quit(); process.exit(0);
})();
