// /cf admin et /cf regle : un operateur nomme un administrateur de partie, qui regle la partie sans etre operateur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' }); b._client.on('add_resource_pack', d => b._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 })); b.log = []; b.on('messagestr', m => b.log.push(m)); return b; };
const op = mk('Chef'), guest = mk('Joueur');
const say = async (b, cmd, wait = 700) => { b.log = []; b.chat(cmd); await sleep(wait); return b.log.filter(m => !/a rejoint|Bienvenue/.test(m)).slice(-1)[0] || '(rien)'; };
(async () => {
  await sleep(6000);
  console.log('Joueur, avant :', await say(guest, '/cf regle pvp 20'));
  console.log('Chef nomme :', await say(op, '/cf admin Joueur'));
  console.log('  recu par Joueur :', guest.log.slice(-1)[0]);
  console.log('Joueur regle le pvp :', await say(guest, '/cf regle pvp 20'));
  console.log('Joueur regle la bordure :', await say(guest, '/cf regle bordure 600'));
  console.log('Joueur, valeur refusee :', await say(guest, '/cf regle vies 99'));
  console.log('Joueur, interrupteur :', await say(guest, '/cf regle regeneration oui'));
  guest.log = []; guest.chat('/cf regle'); await sleep(900); console.log('liste (' + guest.log.length + ' lignes) :', guest.log.slice(1, 9).join(' | ').slice(0, 420));
  console.log('Joueur, commande d\'operateur :', await say(guest, '/cf heal'));
  console.log('Joueur ne peut pas nommer :', await say(guest, '/cf admin Chef'));
  console.log('Chef, liste :', await say(op, '/cf admin'));
  console.log('Chef retire :', await say(op, '/cf admin Joueur'));
  console.log('Joueur, apres :', await say(guest, '/cf regle pvp 30'));
  await say(op, '/cf regle pvp 30'); await say(op, '/cf regle bordure 1000'); await say(op, '/cf regle regeneration non');
  op.quit(); guest.quit(); process.exit(0);
})();
