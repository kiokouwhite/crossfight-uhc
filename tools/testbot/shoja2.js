const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
function make(name) { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' }); b.on('windowOpen', w => b.closeWindow(w)); b.chatLog = []; b.on('messagestr', m => { b.chatLog.push(m); if (/lancent|Pardon|Bulles Bouillantes|Souffrance|Amitie|Tir Curatif|Emprunt/.test(m)) log(name, 'CHAT', m.slice(0, 110)); }); return b; }
const A = make('Tester'), B = make('Tester2');
const ready = b => new Promise(r => b.once('spawn', r));
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
async function click(b, slot, ms) { b.setQuickBarSlot(slot); await sleep(150); b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(ms); }
function countBlocks(b, re, r) { let n = 0; const p = b.entity.position; for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) for (let y = -2; y <= 6; y++) { const bl = b.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (bl && re.test(bl.name)) n++; } return n; }
(async () => {
  await Promise.all([ready(A), ready(B)]); await sleep(1500);
  if (A.health <= 0) A._client.write('client_command', { actionId: 0 }); if (B.health <= 0) B._client.write('client_command', { actionId: 0 }); await sleep(500);
  await ensureTest(A); await ensureTest(B);
  A.chat('/cf change hiro'); B.chat('/cf change pinkou'); await sleep(2000); B.chat('/cf ally Tester'); await sleep(800); B.chat('/tp @s 2 65 0'); await sleep(500);
  A.chat('/cf mannequin'); await sleep(800); const z = Object.values(A.entities).find(e => e.name === 'zombie');
  const slotA = A.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'netherite_scrap');
  await click(A, slotA, 1500); log('Pardon Temporaire : cage bois autour de A =', countBlocks(A, /oak_/, 7), '| mannequin', z ? z.position.toString() : '-', '| A', A.entity.position.toString());
  await sleep(1000);
  A.chat('/cf change crystal'); await sleep(2000); const slotA2 = A.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'netherite_scrap');
  await click(A, slotA2, 800); const h = A.health; await click(A, 2, 600); log('Souffrance Abregee : crystallization vie', h, '->', A.health.toFixed(1), '(attendu inchangee)');
  A.chat('/cf mannequin clear'); A.chat('/cf clear'); await sleep(1500); A.chat('/cf test'); B.chat('/cf test'); await sleep(800); A.quit(); B.quit(); process.exit(0);
})();
