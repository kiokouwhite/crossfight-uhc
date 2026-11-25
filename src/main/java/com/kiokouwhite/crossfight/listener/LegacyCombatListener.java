package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Mannequins;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat facon 1.7.10 (regle combat-1.7) :
 *  - aucun temps de recharge d'attaque (spam clic a pleine puissance), pas d'attaque balayante ;
 *  - neuf coups par seconde au plus ; un clic sur un ennemi enchaine ensuite les coups tout seul, a ce rythme, tant qu'il
 *    reste dans le viseur (regle clic-maintenu) ;
 *  - le recul d'epoque : 0,4 a l'horizontale et a la verticale, la vitesse de la victime divisee par deux, et le
 *    coup en courant (ou Recul) qui pousse de 0,5 de plus dans le regard de l'attaquant ;
 *  - les coups critiques d'epoque : un bonus tire au hasard (jusqu'a la moitie des degats, plus un), et permis en courant ;
 *  - Tranchant a 1,25 par niveau ; l'armure a 4 % par point, quelle que soit la force du coup ;
 *  - l'epee pare (regle blocage-epee) : clic droit maintenu, la moitie des degats, de tous les cotes (sauf l'epee dont
 *    le clic droit lance une capacite) ;
 *  - la canne a peche, les boules de neige et les oeufs repoussent les joueurs ;
 *  - pas de delai entre deux perles ; la pomme doree enchantee rend ses effets d'epoque ; les joueurs ne se poussent pas.
 */
public final class LegacyCombatListener implements Listener {

    private static final double NO_COOLDOWN_ATTACK_SPEED = 1024.0;
    private static final double REACH = 3.0;

    private final CrossfightPlugin plugin;

    public LegacyCombatListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.game().rules().on("combat-1.7");
    }

    /** L'epee pare-t-elle ? */
    public boolean blocking() {
        return enabled() && plugin.game().rules().on("blocage-epee");
    }

    /** Chaque seconde : les reglages suivent la regle (elle se change en jeu), les epees gagnent ou perdent la parade. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean block = blocking();
            for (Player p : Bukkit.getOnlinePlayers()) {
                apply(p);
                p.setCollidable(!enabled());
                var inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    org.bukkit.inventory.ItemStack it = inv.getItem(i);
                    if (it == null || !it.getType().name().endsWith("_SWORD")) continue;
                    boolean has = it.hasData(io.papermc.paper.datacomponent.DataComponentTypes.BLOCKS_ATTACKS);
                    // Une epee dont le clic droit lance une capacite ne pare pas : ce clic est pris.
                    boolean parry = block && !plugin.abilities().rightClickTaken(p, it);
                    if (parry == has) continue;
                    if (parry) it.setData(io.papermc.paper.datacomponent.DataComponentTypes.BLOCKS_ATTACKS, PARRY);
                    else it.resetData(io.papermc.paper.datacomponent.DataComponentTypes.BLOCKS_ATTACKS);
                    inv.setItem(i, it);
                }
            }
        }, 20L, 20L);        Bukkit.getScheduler().runTaskTimer(plugin, this::chain, 1L, 1L);
    }

    /** La parade de l'epee : pas de delai, la moitie des degats, de tous les cotes ; ce qui traverse un bouclier la traverse. */
    private static final io.papermc.paper.datacomponent.item.BlocksAttacks PARRY = io.papermc.paper.datacomponent.item.BlocksAttacks.blocksAttacks()
            .blockDelaySeconds(0f).disableCooldownScale(0f)
            .addDamageReduction(io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction.damageReduction().horizontalBlockingAngle(180f).base(0f).factor(0.5f).build())
            .bypassedBy(io.papermc.paper.registry.keys.tags.DamageTypeTagKeys.BYPASSES_SHIELD)
            .build();

    /** Supprime le temps de recharge d'attaque du joueur. */
    public void apply(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;
        speed.setBaseValue(enabled() ? NO_COOLDOWN_ATTACK_SPEED : 4.0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSweep(EntityDamageByEntityEvent e) {
        if (enabled() && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ le rythme des coups

    /** Neuf coups par seconde au plus, et autant en gardant le clic gauche enfonce. */
    private static final double CPS = 9.0, PER_TICK = CPS / 20.0;
    /** La reserve de coups : elle se remplit de 0,45 par tick ; un peu plus d'un coup d'avance, pour que des clics reguliers tiennent bien 9 par seconde. */
    private static final double RESERVE = 1.45;
    /** En ticks : le clic est tenu pour relache quand l'ennemi a quitte le viseur depuis ce temps. */
    private static final int LOST = 4;

    private static final class Rhythm {
        double ready = RESERVE;
        int seen = Bukkit.getCurrentTick();
        /** L'enchainement : le dernier tick ou un ennemi etait dans le viseur ; -1 hors enchainement. */
        int aimed = -1;
    }

    private final Map<UUID, Rhythm> rhythms = new HashMap<>();
    /** Vrai pendant un coup que le plugin porte lui-meme (l'enchainement) : il ne relance pas l'enchainement. */
    private boolean chained;

    private Rhythm rhythm(Player p) {
        Rhythm r = rhythms.computeIfAbsent(p.getUniqueId(), k -> new Rhythm());
        int now = Bukkit.getCurrentTick();
        r.ready = Math.min(RESERVE, r.ready + (now - r.seen) * PER_TICK);
        r.seen = now;
        return r;
    }

    /** Chaque coup porte prend dans la reserve : au-dela de 9 par seconde, il ne part pas. Un vrai clic sur un ennemi ouvre l'enchainement. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (!enabled()) return;
        Rhythm r = rhythm(e.getPlayer());
        if (r.ready < 1) { e.setCancelled(true); return; }
        r.ready -= 1;
        if (!chained && holds() && plugin.game().isRunning() && e.getAttacked() instanceof LivingEntity) r.aimed = Bukkit.getCurrentTick();
    }

    /** Le clic gauche garde enfonce enchaine-t-il les coups ? */
    public boolean holds() {
        return enabled() && plugin.game().rules().on("clic-maintenu");
    }

    /** L'ennemi que le joueur a dans le viseur, a portee et sans mur entre eux. */
    private LivingEntity aimedAt(Player p) {
        // La portee suit l'attribut de portee d'interaction (capacites de "Reach").
        var range = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double reach = range == null ? REACH : Math.max(1.0, range.getValue());
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach, 0.3,
                ent -> ent != p && ent instanceof LivingEntity le && (Effects.isEnemy(p, le) || Mannequins.isMannequin(le)));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return null;
        var block = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach);
        if (block != null && block.getHitPosition().distanceSquared(p.getEyeLocation().toVector())
                < hit.getHitPosition().distanceSquared(p.getEyeLocation().toVector())) return null;
        return target;
    }

    /** Un balancement de bras (un clic dans le vide, ou le clic tenu sur un bloc) : s'il vise un ennemi, l'enchainement s'ouvre. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (!holds() || e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        if (!plugin.game().isRunning() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (aimedAt(p) != null) rhythm(p).aimed = Bukkit.getCurrentTick();
    }

    /**
     * A chaque tick : tant que l'ennemi reste dans le viseur, les coups partent seuls, au rythme de la reserve (9 par seconde).
     * Le jeu ne dit pas au serveur quand le bouton est relache : l'enchainement s'arrete quand le viseur quitte l'ennemi.
     */
    private void chain() {
        if (!holds()) return;
        int now = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rhythm r = rhythms.get(p.getUniqueId());
            if (r == null || r.aimed < 0) continue;
            if (!plugin.game().isRunning() || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) { r.aimed = -1; continue; }
            LivingEntity target = aimedAt(p);
            if (target == null) { if (now - r.aimed > LOST) r.aimed = -1; continue; }
            r.aimed = now;
            if (rhythm(p).ready < 1) continue;
            chained = true;
            try { p.swingMainHand(); p.attack(target); } finally { chained = false; }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        rhythms.remove(e.getPlayer().getUniqueId());
        knock.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ les degats d'epoque

    private final java.util.Random random = new java.util.Random();

    /** Coups critiques et Tranchant de la 1.7, avant que les capacites ne lisent les degats. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOldDamage(EntityDamageByEntityEvent e) {
        if (!enabled() || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(e.getDamager() instanceof Player p) || Effects.isAbilityDamage()) return;
        int sharp = p.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS);
        double modern = sharp > 0 ? 0.5 * sharp + 0.5 : 0;
        double plain = Math.max(0, e.getDamage() - modern) / (e.isCritical() ? 1.5 : 1.0);
        @SuppressWarnings("deprecation")
        boolean falling = p.getFallDistance() > 0 && !p.isOnGround() && !p.isClimbing() && !p.isInWater() && !p.isInsideVehicle()
                && !p.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        double bonus = 0;
        if (falling && plain > 0) {
            bonus = random.nextInt((int) (plain / 2 + 2));
            // En courant, le jeu d'aujourd'hui ne montre pas le coup critique : on le montre.
            if (!e.isCritical()) e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.2);
        }
        e.setDamage(plain + bonus + 1.25 * sharp);
    }

    /** L'armure de la 1.7 : 4 % par point, sans tenir compte de la force du coup. Le reste (Resistance, Protection, absorption) suit. */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOldArmor(EntityDamageEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player victim) || Effects.isAbilityDamage()) return;
        var ARMOR = EntityDamageEvent.DamageModifier.ARMOR;
        if (!e.isApplicable(ARMOR) || e.getDamage(ARMOR) == 0) return;
        AttributeInstance attr = victim.getAttribute(Attribute.ARMOR);
        if (attr == null) return;
        double before = 0;
        for (var m : EntityDamageEvent.DamageModifier.values()) {
            if (m == ARMOR) break;
            if (e.isApplicable(m)) before += e.getDamage(m);
        }
        double armor0 = e.getDamage(ARMOR), armor1 = -before * Math.min(20, attr.getValue()) * 0.04;
        double after0 = before + armor0, after1 = before + armor1;
        e.setDamage(ARMOR, armor1);
        var RES = EntityDamageEvent.DamageModifier.RESISTANCE;
        var MAGIC = EntityDamageEvent.DamageModifier.MAGIC;
        var ABS = EntityDamageEvent.DamageModifier.ABSORPTION;
        double res0 = e.isApplicable(RES) ? e.getDamage(RES) : 0, res1 = after0 > 1e-6 ? res0 * after1 / after0 : 0;
        if (e.isApplicable(RES)) e.setDamage(RES, res1);
        double magic0 = e.isApplicable(MAGIC) ? e.getDamage(MAGIC) : 0, magic1 = after0 + res0 > 1e-6 ? magic0 * (after1 + res1) / (after0 + res0) : 0;
        if (e.isApplicable(MAGIC)) e.setDamage(MAGIC, magic1);
        if (e.isApplicable(ABS)) e.setDamage(ABS, -Math.min(victim.getAbsorptionAmount(), Math.max(0, after1 + res1 + magic1)));
    }

    // ------------------------------------------------------------------ le recul d'epoque

    /** Le recul calcule pour une victime, en attendant que le jeu envoie le sien. */
    private final Map<UUID, org.bukkit.util.Vector> knock = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnock(EntityDamageByEntityEvent e) {
        if (!enabled() || !(e.getEntity() instanceof LivingEntity victim) || Effects.isAbilityDamage()) return;
        Player attacker = null;
        boolean melee = false;
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && e.getDamager() instanceof Player p) { attacker = p; melee = true; }
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile pr && pushes(pr) && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        AttributeInstance firm = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (firm != null && firm.getValue() >= 1) return;
        org.bukkit.util.Vector v = victim.getVelocity();
        double dx = attacker.getLocation().getX() - victim.getLocation().getX(), dz = attacker.getLocation().getZ() - victim.getLocation().getZ();
        while (dx * dx + dz * dz < 1e-4) { dx = (random.nextDouble() - random.nextDouble()) * 0.01; dz = (random.nextDouble() - random.nextDouble()) * 0.01; }
        double far = Math.sqrt(dx * dx + dz * dz);
        double x = v.getX() / 2 - dx / far * 0.4, y = Math.min(0.4, v.getY() / 2 + 0.4), z = v.getZ() / 2 - dz / far * 0.4;
        if (melee) {
            int extra = (attacker.isSprinting() ? 1 : 0) + attacker.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK);
            if (extra > 0) {
                double yaw = Math.toRadians(attacker.getLocation().getYaw());
                x += -Math.sin(yaw) * extra * 0.5;
                y += 0.1;
                z += Math.cos(yaw) * extra * 0.5;
            }
        }
        org.bukkit.util.Vector push = new org.bukkit.util.Vector(x, y, z);
        UUID id = victim.getUniqueId();
        knock.put(id, push);
        // Sans paquet de vitesse du jeu a remplacer (une creature, un coup sans recul) : applique au tick suivant.
        Bukkit.getScheduler().runTask(plugin, () -> { if (knock.remove(id) == push && victim.isValid()) victim.setVelocity(push); });
    }

    /** Le jeu envoie son recul a la victime : c'est celui de la 1.7 qui part a la place. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(org.bukkit.event.player.PlayerVelocityEvent e) {
        org.bukkit.util.Vector push = knock.remove(e.getPlayer().getUniqueId());
        if (push != null) e.setVelocity(push);
    }

    private static boolean pushes(org.bukkit.entity.Projectile pr) {
        return pr instanceof org.bukkit.entity.FishHook || pr instanceof org.bukkit.entity.Snowball || pr instanceof org.bukkit.entity.Egg;
    }

    /** Canne a peche, boule de neige, oeuf : un coup sans degats, qui repousse comme en 1.7. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightHit(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!enabled() || !pushes(e.getEntity()) || !(e.getHitEntity() instanceof LivingEntity target) || !(e.getEntity().getShooter() instanceof Player p) || p.equals(target)) return;
        if (!plugin.game().isRunning() && !Mannequins.isMannequin(target)) return;
        target.damage(0.0001, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.THROWN).withCausingEntity(p).withDirectEntity(e.getEntity()).build());
    }

    // ------------------------------------------------------------------ perles et pommes

    /** Pas de delai entre deux perles. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (!enabled() || !(e.getEntity() instanceof org.bukkit.entity.EnderPearl) || !(e.getEntity().getShooter() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(org.bukkit.Material.ENDER_PEARL, 0));
    }

    /** La pomme doree enchantee de la 1.7 : Regeneration V 30 s, Absorption 2 min, Resistance et Resistance au feu 5 min. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNotchApple(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (!enabled() || e.getItem().getType() != org.bukkit.Material.ENCHANTED_GOLDEN_APPLE) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (var type : new org.bukkit.potion.PotionEffectType[]{org.bukkit.potion.PotionEffectType.REGENERATION, org.bukkit.potion.PotionEffectType.ABSORPTION,
                    org.bukkit.potion.PotionEffectType.RESISTANCE, org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE}) p.removePotionEffect(type);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 20 * 30, 4));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 20 * 120, 0));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 300, 0));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 20 * 300, 0));
        });
    }
}
