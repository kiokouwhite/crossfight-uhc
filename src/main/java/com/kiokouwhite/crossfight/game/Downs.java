package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A terre (regle a-terre), comme dans les battle royale : un joueur en equipe qui prend un coup mortel ne meurt pas,
 * il tombe au sol, sa barre de vie a nouveau pleine. Il rampe, ne peut ni frapper, ni lancer de capacite, ni manger,
 * ni etre soigne, et perd un coeur toutes les deux secondes. Un allie le releve en restant accroupi a cote de lui
 * pendant cinq secondes (il se releve avec trois coeurs). Un ennemi peut l'achever en le frappant ; s'il se vide de
 * sa vie, il meurt, et c'est celui qui l'a mis a terre qui a le kill.
 *
 * Seulement si un allie est encore debout, dans le meme monde : le dernier debout d'une equipe meurt directement, et
 * quand plus personne de l'equipe n'est debout, ceux qui sont a terre meurent tous.
 *
 * Ramper : le serveur fixe sa pose a plat ventre (les autres le voient ramper, son corps n'a plus que 0,6 bloc de haut),
 * et son propre jeu le met a plat ventre parce qu'il voit un bloc invisible au-dessus de sa tete (envoye a lui seul,
 * jamais pose dans le monde).
 */
public final class Downs implements Listener {

    private static final double REVIVE_RANGE = 2.5;
    private static final Color BLOOD = Color.fromRGB(190, 20, 30);

    private final CrossfightPlugin plugin;
    private final NamespacedKey slowKey, jumpKey;
    private final Map<UUID, Down> downs = new HashMap<>();
    /** Pendant qu'on acheve un joueur a terre : ce coup-la ne le remet pas a terre. */
    private final Set<UUID> finishing = new HashSet<>();

    private static final class Down {
        final UUID downer;
        int ticks;
        /** Ticks de reanimation accumules, et l'allie qui le releve. */
        int revive;
        UUID reviver;
        /** Les blocs invisibles montres au-dessus de sa tete, et ou ils sont centres. */
        final List<Block> ceiling = new ArrayList<>();
        Block above;

        Down(UUID downer) { this.downer = downer; }
    }

    public Downs(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.slowKey = new NamespacedKey(plugin, "a_terre_vitesse");
        this.jumpKey = new NamespacedKey(plugin, "a_terre_saut");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isDown(Player p) {
        return p != null && downs.containsKey(p.getUniqueId());
    }

    private boolean enabled() {
        return plugin.game().rules().on("a-terre");
    }

    private int bleedTicks() { return Math.max(5, (int) Math.round(20 * plugin.getConfig().getDouble("downs.bleed-seconds", 2.0))); }
    private int reviveTicks() { return Math.max(5, (int) Math.round(20 * plugin.getConfig().getDouble("downs.revive-seconds", 5.0))); }
    private double reviveHealth() { return Math.max(1, plugin.getConfig().getDouble("downs.revive-health", 6.0)); }

    private static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    /** Debout et en jeu : peut relever un allie, et garde son equipe en vie. */
    private boolean standing(Player p) {
        if (p == null || !p.isOnline() || p.isDead() || isDown(p)) return false;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        CfPlayer cf = plugin.players().get(p);
        return cf != null && cf.isAlive() && !cf.isWaitingRespawn();
    }

    /** Un allie de ce joueur est-il encore debout, dans le meme monde ? */
    private boolean hasStandingAlly(Player p) {
        Team team = plugin.teams().teamOf(p);
        if (team == null) return false;
        for (Player m : team.onlineMembers()) if (!m.equals(p) && m.getWorld().equals(p.getWorld()) && standing(m)) return true;
        return false;
    }

    // ------------------------------------------------------------------ tomber

    /** Le coup mortel : si un allie est debout, le joueur tombe a terre au lieu de mourir. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enabled() || !plugin.game().isRunning()) return;
        if (isDown(p) || finishing.contains(p.getUniqueId())) return;
        var cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (e.getFinalDamage() < p.getHealth()) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        if (!hasStandingAlly(p)) return;
        Player attacker = e instanceof EntityDamageByEntityEvent be ? DamageListener.attackerOf(be.getDamager()) : null;
        if (attacker == null) attacker = p.getKiller();
        if (attacker != null && attacker.equals(p)) attacker = null;
        e.setCancelled(true);
        knock(p, attacker);
    }

    private void knock(Player p, Player attacker) {
        Down d = new Down(attacker == null ? null : attacker.getUniqueId());
        downs.put(p.getUniqueId(), d);
        // Le coup se voit et s'entend quand meme.
        p.playHurtAnimation(0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, 1f, 0.7f);
        blood(p, p.getLocation().add(0, 1, 0), 30, 0.6, 1.4f);
        // Une barre pleine, sans rien qui la remonte ni la protege.
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.ABSORPTION);
        p.setAbsorptionAmount(0);
        p.setHealth(maxHealth(p));
        p.setNoDamageTicks(20);
        p.setSprinting(false);
        modifier(p, Attribute.MOVEMENT_SPEED, slowKey, -0.3);
        modifier(p, Attribute.JUMP_STRENGTH, jumpKey, -1.0);
        p.setPose(Pose.SWIMMING, true);
        ceiling(p, d);

        p.showTitle(Title.title(Msg.mm("<red><bold>A TERRE"), Msg.mm("<gray>Un allie peut te relever"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(500))));
        Msg.broadcast("<red>" + p.getName() + " <gray>est a terre" + (attacker == null ? "." : " (<red>" + attacker.getName() + "</red>)."));
        if (attacker != null) Msg.actionBar(attacker, "<gold>" + p.getName() + " est a terre : acheve-le !");
        Team team = plugin.teams().teamOf(p);
        if (team != null) for (Player m : team.onlineMembers()) {
            if (m.equals(p)) continue;
            String where = m.getWorld().equals(p.getWorld()) ? " <gray>(" + (int) m.getLocation().distance(p.getLocation()) + " m)" : "";
            Msg.send(m, "<red>" + p.getName() + " est a terre !" + where + " <yellow>Accroupis-toi a cote de lui pour le relever.");
            m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
        }
    }

    /** Du sang autour de lui, que les autres voient : lui, le nez au sol, l'aurait en pleine figure. */
    private static void blood(Player p, Location at, int count, double dy, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(BLOOD, size);
        for (Player o : p.getWorld().getPlayers())
            if (!o.equals(p) && o.getLocation().distanceSquared(at) < 64 * 64) o.spawnParticle(Particle.DUST, at, count, 0.35, dy, 0.35, 0, dust);
    }

    private void modifier(Player p, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance a = p.getAttribute(attr);
        if (a == null) return;
        a.removeModifier(key);
        a.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    /** Fin de l'etat a terre : il retrouve ses jambes (relevé, achevé, mort, fin de partie). */
    private void clear(Player p) {
        Down d = downs.remove(p.getUniqueId());
        if (d == null) return;
        for (Attribute attr : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance a = p.getAttribute(attr);
            if (a != null) { a.removeModifier(slowKey); a.removeModifier(jumpKey); }
        }
        p.setPose(Pose.STANDING, false);
        if (p.isOnline()) for (Block b : d.ceiling) p.sendBlockChange(b.getLocation(), b.getBlockData());
        d.ceiling.clear();
    }

    /** Les blocs invisibles au-dessus de sa tete, pour lui seul : son jeu le garde a plat ventre. Ils le suivent. */
    private void ceiling(Player p, Down d) {
        Block above = p.getLocation().getBlock().getRelative(0, 1, 0);
        if (above.equals(d.above)) return;
        d.above = above;
        List<Block> want = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block b = above.getRelative(dx, 0, dz);
            if (b.isPassable() && !b.isLiquid()) want.add(b);
        }
        for (Block b : d.ceiling) if (!want.contains(b)) p.sendBlockChange(b.getLocation(), b.getBlockData());
        BlockData barrier = Material.BARRIER.createBlockData();
        for (Block b : want) p.sendBlockChange(b.getLocation(), barrier);
        d.ceiling.clear();
        d.ceiling.addAll(want);
    }

    // ------------------------------------------------------------------ a chaque tick

    private void tick() {
        if (downs.isEmpty()) return;
        boolean running = plugin.game().isRunning();
        for (UUID id : new ArrayList<>(downs.keySet())) {
            Down d = downs.get(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { downs.remove(id); continue; }
            if (!running || !enabled() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { clear(p); continue; }
            // Plus personne de son equipe debout : il meurt.
            if (!hasStandingAlly(p)) { finish(p, d, "plus aucun allie debout"); continue; }
            d.ticks++;
            p.setPose(Pose.SWIMMING, true);
            ceiling(p, d);
            if (p.isSprinting()) p.setSprinting(false);

            // Le sang qu'il perd : un coeur toutes les deux secondes.
            if (d.ticks % bleedTicks() == 0) {
                if (p.getHealth() <= 2.0) { finish(p, d, null); continue; }
                p.setHealth(p.getHealth() - 2.0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f);
            }
            if (d.ticks % 10 == 0) blood(p, p.getLocation().add(0, 0.4, 0), 4, 0.1, 1.2f);

            // Un allie accroupi tout pres le releve.
            Player reviver = reviver(p, d);
            if (reviver == null) {
                if (d.revive > 0 && d.reviver != null) {
                    Player was = Bukkit.getPlayer(d.reviver);
                    if (was != null) Msg.actionBar(was, "<gray>Reanimation de " + p.getName() + " interrompue.");
                }
                d.revive = 0;
                d.reviver = null;
                if (d.ticks % 10 == 1) Msg.actionBar(p, "<red><bold>A TERRE</bold> <gray>| <white>" + seconds(p.getHealth() / 2.0 * bleedTicks())
                        + " <gray>avant de mourir | un allie doit s'accroupir a cote de toi");
                continue;
            }
            if (!reviver.getUniqueId().equals(d.reviver)) { d.reviver = reviver.getUniqueId(); d.revive = 0; }
            d.revive++;
            int need = reviveTicks();
            if (d.revive >= need) { revive(p, reviver); continue; }
            if (d.revive % 4 == 1) {
                String bar = bar(d.revive, need), left = seconds(need - d.revive);
                Msg.actionBar(reviver, "<green>Tu releves " + p.getName() + " " + bar + " <gray>" + left);
                Msg.actionBar(p, "<green>" + reviver.getName() + " te releve " + bar + " <gray>" + left);
                if (d.revive % 20 == 1) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + d.revive / (float) need);
            }
        }
    }

    private Player reviver(Player p, Down d) {
        Team team = plugin.teams().teamOf(p);
        if (team == null) return null;
        Player best = null;
        double bestDist = REVIVE_RANGE * REVIVE_RANGE;
        for (Player m : team.onlineMembers()) {
            if (m.equals(p) || !m.isSneaking() || !standing(m) || !m.getWorld().equals(p.getWorld())) continue;
            double dist = m.getLocation().distanceSquared(p.getLocation());
            // Celui qui avait commence garde la main tant qu'il reste a portee.
            if (m.getUniqueId().equals(d.reviver) && dist <= REVIVE_RANGE * REVIVE_RANGE) return m;
            if (dist <= bestDist) { best = m; bestDist = dist; }
        }
        return best;
    }

    private static String bar(int done, int need) {
        int full = (int) Math.round(10.0 * done / need);
        return "<white>" + "▌".repeat(full) + "<dark_gray>" + "▌".repeat(10 - full);
    }

    private static String seconds(double ticks) {
        return String.format(java.util.Locale.FRANCE, "%.1f s", Math.max(0, ticks) / 20.0);
    }

    private void revive(Player p, Player reviver) {
        clear(p);
        p.setHealth(Math.min(maxHealth(p), reviveHealth()));
        p.setNoDamageTicks(20);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
        p.showTitle(Title.title(Msg.mm("<green><bold>RELEVE"), Msg.mm("<gray>par " + reviver.getName()),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
        Msg.actionBar(reviver, "<green>Tu as releve " + p.getName() + " !");
        Msg.broadcast("<green>" + p.getName() + " <gray>a ete releve par <green>" + reviver.getName() + "<gray>.");
    }

    /** Il meurt pour de bon : le kill va a celui qui l'a mis a terre. */
    private void finish(Player p, Down d, String why) {
        clear(p);
        Player downer = d.downer == null ? null : Bukkit.getPlayer(d.downer);
        if (why != null) Msg.send(p, "<red>Tu meurs : " + why + ".");
        finishing.add(p.getUniqueId());
        try {
            var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC_KILL);
            if (downer != null && downer.isOnline()) source.withCausingEntity(downer).withDirectEntity(downer);
            p.damage(Math.max(1000, p.getHealth() * 10), source.build());
            if (!p.isDead() && p.getHealth() > 0) p.setHealth(0);
        } finally {
            finishing.remove(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ ce qu'il ne peut pas faire

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Down d = downs.get(p.getUniqueId());
        if (d == null) return;
        clear(p);
        // Achevé par l'environnement (lave, chute...) : le kill reste a celui qui l'a mis a terre.
        if (p.getKiller() == null && d.downer != null) {
            Player downer = Bukkit.getPlayer(d.downer);
            if (downer != null) p.setKiller(downer);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Player attacker = DamageListener.attackerOf(e.getDamager());
        if (attacker != null && attacker != e.getEntity() && isDown(attacker)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!isDown(e.getPlayer())) return;
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (isDown(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEat(PlayerItemConsumeEvent e) {
        if (isDown(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (isDown(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isDown(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting() && isDown(e.getPlayer())) e.setCancelled(true);
    }

    /** A terre, rien ne le soigne : seul un allie le releve. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && isDown(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }
}
