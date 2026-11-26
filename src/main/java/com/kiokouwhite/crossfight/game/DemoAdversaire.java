package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Skins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * /cf demo contre|falchion : un adversaire de demonstration, pour filmer une capacite. Un mannequin a l'apparence d'un
 * joueur (un skin de personnage tire au hasard, une epee en fer) se tient devant le joueur ; la meme commande le retire.
 * <ul>
 *   <li>contre : des que Marth lance son Contre, il le frappe dans la seconde du Contre (la riposte part), puis revient
 *   se placer devant lui pour la prise suivante ;</li>
 *   <li>falchion : immobile tant qu'on ne le frappe pas ; frappe-le et il te poursuit et te frappe, jusqu'a ce que tu
 *   n'aies plus que 5 coeurs (il s'arrete pile la) : de quoi filmer le soin de la Falchion. Frappe-le de nouveau pour
 *   qu'il reprenne.</li>
 * </ul>
 */
public final class DemoAdversaire implements Listener {

    public static final String TAG = "cf_demo_adversaire";
    public static final List<String> MODES = List.of("contre", "falchion");

    private static final Map<UUID, Demo> DEMOS = new HashMap<>();
    private static final Random RND = new Random();
    /** Distance a laquelle il se tient devant le joueur (blocs), et sa vie (il ne doit jamais mourir). */
    private static final double DISTANCE = 2.4, VIE = 1000.0;
    /** Contre : le coup part ce nombre de ticks apres le debut du Contre (qui dure 20 ticks) ; le retour, apres la riposte. */
    private static final long COUP = 7L, RETOUR = 60L;
    /**
     * Falchion : un coup tous les CADENCE ticks, qui retire PERTE points de vie quelle que soit l'armure (un coeur : la
     * descente prend quelques secondes, avec ou sans armure) ; il s'arrete a PLANCHER points de vie (5 coeurs).
     */
    private static final int CADENCE = 12;
    private static final double PERTE = 2.0, PLANCHER = 10.0;

    private static final class Demo {
        final Mannequin corps;
        final String mode;
        boolean combat;
        int attente;
        BukkitTask tache;
        Demo(Mannequin corps, String mode) { this.corps = corps; this.mode = mode; }
        void retirer() {
            if (tache != null) tache.cancel();
            if (corps.isValid()) corps.remove();
        }
    }

    /**
     * Place l'adversaire de ce mode devant le joueur, ou le retire s'il y est deja (un adversaire d'un autre mode est
     * remplace). Vrai s'il vient d'etre place.
     */
    public static boolean toggle(Player p, String mode) {
        Demo old = DEMOS.remove(p.getUniqueId());
        if (old != null) {
            old.retirer();
            if (old.mode.equals(mode)) return false;
        }
        Demo d = new Demo(spawn(p), mode);
        if (mode.equals("falchion")) d.tache = Bukkit.getScheduler().runTaskTimer(CrossfightPlugin.get(), () -> combat(p, d), 1L, 1L);
        DEMOS.put(p.getUniqueId(), d);
        return true;
    }

    /** Marth vient de lancer son Contre : l'adversaire « contre » de ce joueur, s'il en a un, le frappe aussitot. */
    public static void contre(Player p) {
        Demo d = DEMOS.get(p.getUniqueId());
        if (d == null || !d.mode.equals("contre")) return;
        Mannequin m = d.corps;
        if (!m.isValid() || !m.getWorld().equals(p.getWorld())) return;
        // Repousse par une riposte precedente, ou laisse loin : il revient d'abord devant le joueur.
        if (m.getLocation().distanceSquared(p.getLocation()) > 3.5 * 3.5) m.teleport(place(p));
        face(m, p);
        CrossfightPlugin plugin = CrossfightPlugin.get();
        // Un pas vers le joueur, puis le coup d'epee.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid() || !p.isOnline()) return;
            face(m, p);
            avance(m, p, 0.32);
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid() || !p.isOnline() || !m.getWorld().equals(p.getWorld())) return;
            frappe(m, p, 4.0);
        }, COUP);
        // Apres la riposte (projection, puis 1 s immobilise), il revient se placer devant le joueur.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid() || !p.isOnline() || !m.getWorld().equals(p.getWorld())) return;
            m.setHealth(VIE);
            m.teleport(place(p));
        }, COUP + RETOUR);
    }

    /** Falchion : chaque tick, s'il est en combat, il poursuit le joueur et le frappe, jusqu'a 5 coeurs. */
    private static void combat(Player p, Demo d) {
        Mannequin m = d.corps;
        if (!p.isOnline() || !m.isValid()) { d.retirer(); DEMOS.remove(p.getUniqueId(), d); return; }
        if (!d.combat) return;
        if (p.isDead() || !m.getWorld().equals(p.getWorld()) || p.getHealth() <= PLANCHER) { d.combat = false; return; }
        face(m, p);
        double dist = m.getLocation().distance(p.getLocation());
        if (dist > 2.0) avance(m, p, 0.22);
        if (d.attente > 0) { d.attente--; return; }
        if (dist <= 3.0) {
            // Le coup (animation, son, recul) avec des degats infimes, puis la vraie perte, fixe : jamais sous 5 coeurs.
            double avant = p.getHealth();
            frappe(m, p, 0.01);
            if (!p.isDead()) p.setHealth(Math.max(PLANCHER, Math.min(p.getHealth(), avant - PERTE)));
            if (p.getHealth() <= PLANCHER) d.combat = false;
            d.attente = CADENCE;
        }
    }

    /** On frappe l'adversaire « falchion » : il entre en combat (si le joueur a plus de 5 coeurs). Il ne perd rien. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mannequin m) || !m.getScoreboardTags().contains(TAG)) return;
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> { if (m.isValid()) m.setHealth(VIE); });
        if (!(com.kiokouwhite.crossfight.listener.DamageListener.livingAttackerOf(e.getDamager()) instanceof Player p)) return;
        Demo d = DEMOS.get(p.getUniqueId());
        if (d == null || d.corps != m || !d.mode.equals("falchion") || d.combat || p.getHealth() <= PLANCHER) return;
        d.combat = true;
        d.attente = 6;
    }

    /** Retire tous les adversaires de demonstration (arret du plugin). */
    public static void removeAll() {
        for (Demo d : DEMOS.values()) d.retirer();
        DEMOS.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Demo d = DEMOS.remove(e.getPlayer().getUniqueId());
        if (d != null) d.retirer();
    }

    private static void frappe(Mannequin m, Player p, double degats) {
        face(m, p);
        m.swingMainHand();
        p.setNoDamageTicks(0);
        p.damage(degats, m);
        m.setHealth(VIE);
    }

    private static void avance(Mannequin m, Player p, double vitesse) {
        Vector vers = p.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
        if (vers.lengthSquared() < 1e-4) return;
        Vector v = vers.normalize().multiply(vitesse);
        m.setVelocity(v.setY(m.isOnGround() ? 0.0 : m.getVelocity().getY()));
    }

    private static Mannequin spawn(Player p) {
        Mannequin m = (Mannequin) p.getWorld().spawnEntity(place(p), EntityType.MANNEQUIN);
        m.addScoreboardTag(TAG);
        m.setPersistent(false);
        m.setImmovable(false);
        // Comme un joueur : pas de ligne sous le nom (« PNJ » par defaut), pas de nom.
        m.setDescription(null);
        m.setCustomNameVisible(false);
        // Un skin de personnage au hasard (pas celui de Marth), sinon un skin par defaut du jeu.
        List<String> ids = new ArrayList<>(Skins.characters());
        ids.remove("marth");
        var profil = ids.isEmpty() ? null : Skins.profile(ids.get(RND.nextInt(ids.size())));
        m.setProfile(profil != null ? profil : Mannequin.defaultProfile());
        var vie = m.getAttribute(Attribute.MAX_HEALTH);
        if (vie != null) vie.setBaseValue(VIE);
        m.setHealth(VIE);
        var eq = m.getEquipment();
        if (eq != null) eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        return m;
    }

    /** La place de l'adversaire : DISTANCE blocs devant le joueur, a sa hauteur, tourne vers lui. */
    private static Location place(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        Location at = p.getLocation().clone().add(dir.normalize().multiply(DISTANCE));
        at.setY(p.getLocation().getY());
        at.setDirection(p.getLocation().toVector().subtract(at.toVector()));
        return at;
    }

    private static void face(Mannequin m, Player p) {
        Vector d = p.getEyeLocation().toVector().subtract(m.getEyeLocation().toVector());
        if (d.lengthSquared() < 1e-4) return;
        Location look = m.getLocation().clone();
        look.setDirection(d);
        m.setRotation(look.getYaw(), look.getPitch());
    }
}
