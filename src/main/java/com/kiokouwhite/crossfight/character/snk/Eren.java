package com.kiokouwhite.crossfight.character.snk;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.Skins;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eren Jaeger - Path of Destruction.
 */
public final class Eren extends Character implements Listener {

    static final NamespacedKey ROCK = new NamespacedKey("crossfight", "eren_rock");
    static final NamespacedKey BOLT = new NamespacedKey("crossfight", "eren_bolt");
    static final NamespacedKey TITAN_WEAPON = new NamespacedKey("crossfight", "eren_titan_weapon");
    static final NamespacedKey USES = new NamespacedKey("crossfight", "eren_weapon_uses");

    public static final String[] TITAN_IDS = {"colossal", "feminin", "cuirasse", "bestial", "machoire", "charrette", "marteau"};
    static final String[] TITAN_NAMES = {"Titan Colossal", "Titan Feminin", "Titan Cuirasse", "Titan Bestial", "Titan Machoire", "Titan Charrette", "Titan Marteau d'Armes"};
    static final Material[] TITAN_ICONS = {Material.CAMPFIRE, Material.QUARTZ, Material.IRON_CHESTPLATE, Material.COBBLESTONE, Material.BONE, Material.SADDLE, Material.ANVIL};
    static final int[] TITAN_CD = {20, 20, 0, 15, 60, 25, 15};
    static final String[] TITAN_DESC = {
            "Vapeur brulante : les adversaires a 5 blocs prennent 1 coeur et brulent. A la mort du Titan : zone de chaleur de 7 blocs pendant 5 s.",
            "Plus aucun degat et Speed pendant 10 secondes.",
            "Intervertible : Resistance ou Speed.",
            "Lance un rocher : 2 coeurs et ejection de 10 blocs.",
            "Coup de griffe a 2 blocs : 4 coeurs.",
            "Dash de 10 blocs puis Speed II pendant 5 secondes.",
            "Cree une arme au hasard (epee 12 dmg / 4 coups, arbalete 4 coeurs / 1 tir, lance 8 dmg a 5 blocs / 5 coups)."};

    public Eren() {
        super("eren", "Eren Jaeger", Voie.DESTRUCTION, Material.IRON_SWORD);
        addAbility(new SNK.OdmGear(SNK.Style.HAINE, "Style Haine : si le grappin attrape un adversaire, Eren lui fonce dessus et lui inflige 2 coeurs a l'impact (10 s)."));
        addAbility(new ChargeTridimensionnelle());
        addAbility(new EliminationMorale());
        addAbility(new TitansPrimordiaux());
        for (String id : TITAN_IDS) addAbility(new TitanPower(id));
        setUltimate(new TitanAssaillant());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 9.0, "<gold><bold>Lames (Style Haine)", List.of("<gray>Equipement tridimensionnel d'Eren."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Grappin Style Haine : fonce sur l'adversaire attrape",
                "<white>Charge tridimensionnelle : dash vers l'ennemi le plus proche",
                "<white>Elimination Morale : +0.2 Force par kill de l'adversaire",
                "<white>Titans primordiaux : 7 pouvoirs a voler sur 4 cibles",
                "<light_purple>Ultime : forme de Titan (Force, Speed, vol de vie)");
    }

    // ------------------------------------------------------------- helpers

    static boolean isTitan(Player p) {
        CfPlayer cf = SNK.plugin().players().get(p);
        if (cf.character() == null || !"eren".equals(cf.character().id())) return false;
        Ability ult = cf.character().ability("eren_ult");
        return ult != null && SNK.plugin().abilities().state(p, ult).get("titan", false);
    }

    static AbilityState titansState(Player p) {
        CfPlayer cf = SNK.plugin().players().get(p);
        if (cf.character() == null) return null;
        Ability a = cf.character().ability("eren_titans");
        return a == null ? null : SNK.plugin().abilities().state(p, a);
    }

    /** Le plus grand bonus d'Elimination Morale que le badge sait ecrire : 12 kills, +2,4 de Force (tools/build_ultime_franuka.py). */
    static final int MORALE_MAX = 12;

    /**
     * Le badge d'Elimination Morale, sur la case du passif : la Force gagnee contre le dernier adversaire frappe
     * ("eren_m3" : +0,6). Porte par l'image de l'orbe, comme le badge de la Yato ; rien si les passifs sont masques.
     */
    @Override
    public String hudOrbVariant(Player p) {
        Ability a = ability("eren_morale");
        if (a == null || !com.kiokouwhite.crossfight.hud.HudPrefs.Pref.PASSIFS.of(p)) return "";
        int kills = SNK.plugin().abilities().state(p, a).get("last", 0);
        return "eren_m" + Math.max(0, Math.min(MORALE_MAX, kills));
    }

    /** En Titan, Eren ne parle plus : pas de replique de kill ("eren_titan_kill" n'est pas dans le pack). Son cri de mort reste le sien. */
    @Override
    public String voiceKey(Player p, String moment) {
        return "kill".equals(moment) && isTitan(p) ? "eren_titan_kill" : super.voiceKey(p, moment);
    }

    /** Les quatre cibles des Titans primordiaux, par leur pseudo ; celles qu'il a deja devorees sont barrees. En mode test, des noms de substitution. */
    @Override
    public List<String> sidebarLines(Player p) {
        AbilityState st = titansState(p);
        if (st == null) return List.of();
        List<String> out = new ArrayList<>();
        out.add("<white>Cibles des Titans :");
        if (SNK.plugin().game().isTest()) {
            // Des cibles d'essai, avec des tetes d'essai quand le pack en a.
            List<String> faces = com.kiokouwhite.crossfight.util.Heads.known();
            for (int i = 1; i <= 4; i++) {
                String face = faces.isEmpty() ? "" : com.kiokouwhite.crossfight.util.Heads.tag(p, faces.get((i - 1) % faces.size()));
                out.add((face.isEmpty() ? "<gray> • " : " " + face) + "<white>Joueur" + i);
            }
            return out;
        }
        List<String> targets = st.get("targets", null), names = st.get("names", null), eaten = st.get("eaten", null);
        if (targets == null || targets.isEmpty()) return List.of();
        for (int i = 0; i < targets.size(); i++) {
            String name = names != null && i < names.size() ? names.get(i) : Bukkit.getOfflinePlayer(java.util.UUID.fromString(targets.get(i))).getName();
            if (name == null) name = "?";
            // La tete du personnage de la cible, tant qu'elle est en ligne, a la place de la puce.
            String face = com.kiokouwhite.crossfight.util.Heads.tag(p, Bukkit.getPlayer(java.util.UUID.fromString(targets.get(i))));
            out.add(eaten != null && eaten.contains(targets.get(i)) ? "<dark_gray> ✔ <st>" + name + "</st>" : (face.isEmpty() ? "<gray> • " : " " + face) + "<white>" + name);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<String> titansOf(Player p) {
        AbilityState st = titansState(p);
        if (st == null) return new ArrayList<>();
        List<String> l = st.get("titans", null);
        if (l == null) { l = new ArrayList<>(); st.set("titans", l); }
        return l;
    }

    static boolean owns(Player p, String titan) { return titansOf(p).contains(titan); }

    /** Mode test : donne un titan (ou tous). Retourne -1 si le joueur n'est pas Eren. */
    public static int grantTitan(Player p, String which) {
        CfPlayer cf = SNK.plugin().players().get(p);
        if (cf.character() == null || !"eren".equals(cf.character().id())) return -1;
        List<String> owned = titansOf(p);
        int n = 0;
        for (String id : TITAN_IDS) {
            if (!which.equals("tous") && !which.equals(id)) continue;
            if (owned.contains(id)) continue;
            owned.add(id); n++;
            Msg.send(p, "<gold>Pouvoir obtenu : <white>" + TITAN_NAMES[index(id)]);
        }
        if (n > 0) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
        if (isTitan(p)) { TitanAssaillant.giveTitanItems(p); if (owned.contains("cuirasse")) TitanAssaillant.applyCuirasse(p, titansState(p)); }
        TitansPrimordiaux.refreshItem(p);
        return n;
    }

    static int index(String id) { for (int i = 0; i < TITAN_IDS.length; i++) if (TITAN_IDS[i].equals(id)) return i; return -1; }

    static boolean cdReady(AbilityState st, String key) {
        return SNK.plugin().game().isTest() || System.currentTimeMillis() >= st.get(key, 0L);
    }
    static int cdLeft(AbilityState st, String key) {
        return (int) Math.ceil(Math.max(0, st.get(key, 0L) - System.currentTimeMillis()) / 1000.0);
    }
    static void startCd(Player p, AbilityState st, String key, int seconds) {
        if (SNK.plugin().game().isTest()) return;
        double f = SNK.plugin().players().get(p).cooldownFactor();
        st.set(key, System.currentTimeMillis() + (long) (seconds * f * 1000));
    }

    // -------------------------------------------------------- style Haine

    static boolean haineReady(Player p, AbilityState gearSt) { return cdReady(gearSt, "haineAt"); }

    /** Eren fonce sur l'adversaire attrape et lui inflige 2 coeurs a l'impact. */
    static void haineDash(Player p, AbilityState gearSt, LivingEntity target) {
        startCd(p, gearSt, "haineAt", 10);
        gearSt.setEnabled(true);
        Effects.softFallUntil(p, 5000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        Puppet fly = SNK.flight(p);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                try {
                    if (!p.isOnline() || p.isDead() || target.isDead() || t > 25 || gearSt.get("stop", false) || (t > 3 && p.isSneaking())) { stop(); return; }
                    Vector diff = target.getLocation().add(0, 1, 0).toVector().subtract(p.getLocation().add(0, 1, 0).toVector());
                    if (Effects.distanceToBox(p.getLocation().add(0, 1, 0), target) < 1.6) {
                        Effects.damage(target, 4.0, p);
                        w.spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
                        w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.3);
                        w.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                        p.setVelocity(p.getVelocity().multiply(0.2));
                        stop();
                        return;
                    }
                    p.setVelocity(diff.normalize().multiply(2.2).add(new Vector(0, 0.04, 0)));
                    SNK.aim(fly, p, diff);
                    SNK.OdmGear.cable(p, target.getLocation().add(0, 1, 0));
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0, SNK.GAS);
                } catch (Exception ex) { stop(); }
            }
            void stop() { gearSt.setEnabled(false); gearSt.set("stop", false); SNK.land(p, fly); if (p.isOnline()) Effects.softFallUntil(p, 5000); cancel(); }
        }.runTaskTimer(SNK.plugin(), 0L, 1L);
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class ChargeTridimensionnelle extends ActiveAbility {
        ChargeTridimensionnelle() {
            super("eren_charge", "Charge tridimensionnelle", "Eren dash vers l'adversaire le plus proche a 15 blocs maximum.", Material.FEATHER, 20);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (isTitan(p)) { Msg.actionBar(p, "<red>Impossible sous forme de Titan."); return false; }
            LivingEntity target = Effects.nearestEnemy(p, 15);
            if (target == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 15 blocs."); return false; }
            Vector back = p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
            Location to = target.getLocation().add(back.normalize().multiply(1.2));
            to.setDirection(target.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
            // Assez de ticks pour qu'on le voie foncer : deux blocs et demi par tick, entre 4 et 7 ticks.
            int ticks = (int) Math.max(4, Math.min(7, Math.ceil(to.distance(p.getLocation()) / 2.5)));
            chargePose(p, to, ticks);
            SNK.rush(p, to, ticks, Particle.CLOUD);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.9, 0), 14, 0.3, 0.25, 0.3, 0, SNK.GAS);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.3f);
            return true;
        }

        /**
         * La charge, vue de l'exterieur : il fonce tete la premiere, le corps couche vers l'avant, les deux bras
         * rejetes en arriere, une lame dans chaque main. Le pantin regarde dans le sens de la charge et suit sa pente.
         */
        static void chargePose(Player p, Location to, int ticks) {
            ItemStack blade = new ItemStack(Material.IRON_SWORD);
            Puppet pu = Puppet.show(p, "eren", blade, "eren_charge");
            if (pu == null) return;
            Vector path = to.toVector().subtract(p.getLocation().toVector());
            Location heading = p.getLocation().setDirection(path);
            pu.face(heading.getYaw());
            if (Math.abs(heading.getPitch()) > 4) pu.spin(new Vector3f(1, 0, 0), heading.getPitch(), new Vector3f(0, 16, 0), 0);
            pu.second(blade);
            pu.endAfter(ticks + 3, 4);
        }
    }

    static final class EliminationMorale extends PassiveAbility {
        EliminationMorale() {
            super("eren_morale", "Elimination Morale", "Eren gagne 0.2 point de Force supplementaire par kill de l'adversaire qu'il frappe.");
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (!(victim instanceof Player v)) return;
            int kills = SNK.plugin().players().get(v).kills();
            // Le bonus contre ce joueur-la : montre sur la carte du passif (hudOrbVariant) jusqu'au prochain adversaire frappe.
            st.set("last", kills);
            if (kills > 0) { e.setDamage(e.getDamage() + 0.6 * kills); signal(attacker); }
        }
    }

    /** Les pouvoirs des titans voles. */
    static final class TitansPrimordiaux extends PassiveAbility {
        TitansPrimordiaux() {
            super("eren_titans", "Titans primordiaux", "4 adversaires sont choisis au hasard au debut de la partie : chaque fois qu'Eren en tue un, il vole les pouvoirs d'un des 7 titans primordiaux. Sous forme de Titan, chaque pouvoir vole devient un item dans la barre.", Material.NETHER_STAR);
        }

        /** Remet a jour la liste des titans voles dans la description de l'item. */
        static void refreshItem(Player p) {
            var inv = p.getInventory();
            CfPlayer cf = SNK.plugin().players().get(p);
            if (cf.character() == null) return;
            Ability a = cf.character().ability("eren_titans");
            if (a == null) return;
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || !"eren_titans".equals(Items.tag(it, com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY))) continue;
                ItemStack fresh = a.createItem();
                var meta = fresh.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.add(Msg.mm(""));
                List<String> owned = titansOf(p);
                lore.add(Msg.mm("<!italic><gold>Titans voles : <white>" + (owned.isEmpty() ? "aucun" : owned.size() + "/7")));
                for (String id : owned) lore.add(Msg.mm("<!italic><gray> - " + TITAN_NAMES[index(id)]));
                meta.lore(lore);
                fresh.setItemMeta(meta);
                inv.setItem(i, fresh);
            }
        }

        @Override public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = SNK.plugin().abilities().state(p, this);
            Bukkit.getScheduler().runTask(SNK.plugin(), () -> refreshItem(p));
            if (SNK.plugin().game().isTest() || st.get("targets", null) != null) return;
            List<Player> candidates = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) {
                CfPlayer ocf = SNK.plugin().players().get(o);
                if (o == p || ocf.team() == null || SNK.plugin().teams().sameTeam(p, o)) continue;
                candidates.add(o);
            }
            java.util.Collections.shuffle(candidates);
            List<String> targets = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Player o : candidates.subList(0, Math.min(4, candidates.size()))) { targets.add(o.getUniqueId().toString()); names.add(o.getName()); }
            st.set("targets", targets);
            st.set("names", names);
            if (!names.isEmpty()) Msg.send(p, "<gold>Titans primordiaux : tes cibles sont <white>" + String.join(", ", names) + "</white>.");
        }

        @Override public void onKill(Player killer, Player victim, AbilityState st) {
            List<String> targets = st.get("targets", null);
            boolean primordial = SNK.plugin().game().isTest() || (targets != null && targets.contains(victim.getUniqueId().toString()));
            if (!primordial) return;
            List<String> eaten = st.get("eaten", null);
            if (eaten == null) { eaten = new ArrayList<>(); st.set("eaten", eaten); }
            eaten.add(victim.getUniqueId().toString());
            List<String> owned = titansOf(killer);
            List<String> left = new ArrayList<>();
            for (String id : TITAN_IDS) if (!owned.contains(id)) left.add(id);
            if (left.isEmpty()) return;
            String id = left.get((int) (Math.random() * left.size()));
            owned.add(id);
            Msg.send(killer, "<gold>Tu as devore " + victim.getName() + " et vole le pouvoir du <white>" + TITAN_NAMES[index(id)] + "</white> !");
            killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
            if (isTitan(killer)) { TitanAssaillant.giveTitanItems(killer); if (id.equals("cuirasse")) TitanAssaillant.applyCuirasse(killer, st); }
            refreshItem(killer);
        }

/** Le pantin du Titan (deux fois la taille d'un joueur), pour ses gestes. Null sans le pack ou sans les poses. */
        static Puppet titanPuppet(Player p) {
            return Puppet.show(p, Puppet.available("eren_titan") ? "eren_titan" : "eren", null, "debout", 2f);
        }

        /** Il leve les deux poings, les abat sur le sol : la vapeur brulante jaillit autour de lui. */
        static boolean colossal(Player p) {
            Puppet pu = titanPuppet(p);
            if (pu == null) { steam(p); return true; }
            pu.pose("eren_titan_poings_leves", 4);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 0.7f);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { if (Puppet.of(p) == pu) pu.pose("eren_titan_frappe_sol", 2); }, 5L);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { if (p.isOnline() && !p.isDead()) steam(p); }, 7L);
            pu.endAfter(20, 4);
            return true;
        }

        /** L'impact au sol et la vapeur : une onde qui s'ecarte jusqu'a 5 blocs, puis des colonnes qui montent. */
        static void steam(Player p) {
            World w = p.getWorld();
            Location c = p.getLocation();
            w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.2, 0), 60, 1.2, 0.2, 1.2, c.clone().add(0, -1, 0).getBlock().getBlockData());
            w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 2, 0.6, 0.1, 0.6, 0);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            w.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
            for (LivingEntity enemy : Effects.enemiesWithin(p, 5)) { Effects.damage(enemy, 2.0, p); enemy.setFireTicks(100); }
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 30) { cancel(); return; }
                    if (t <= 8) {
                        // L'onde : un anneau de vapeur au ras du sol, qui s'ecarte du point d'impact.
                        double r = 5.0 * t / 8;
                        for (int k = 0; k < 28; k++) {
                            double a = Math.PI * 2 * k / 28 + t * 0.2;
                            w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0.15, 0.1, 0.15, 0.01);
                        }
                    }
                    // La vapeur qui monte de toute la zone.
                    if (t % 2 == 0) {
                        w.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.6, 0), 22, 2.6, 0.4, 2.6, 0.03);
                        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 0.4, 0), 6, 2.6, 0.2, 2.6, 0.03);
                    }
                    if (t % 10 == 0) w.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.7f);
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
        }

static boolean feminin(Player p) {
            SNK.invincible(p, 10000);
            Effects.give(p, PotionEffectType.SPEED, 0, 200);
            crystallize(p, 200);
            Msg.actionBar(p, "<aqua>Titan Feminin : cristallise pendant 10 s");
            return true;
        }

        /** Un morceau de cristal sur le corps : son pied dans le repere du corps (a l'echelle d'un joueur), sa largeur, sa hauteur. */
        private record Shard(double x, double y, float width, float height) {}

        /** Le cristal gagne le corps de bas en haut : les jambes, le torse, les deux bras, puis la tete. */
        private static final Shard[][] CRYSTAL = {
                {new Shard(0, 0, 0.62f, 0.78f)},
                {new Shard(0, 0.72f, 0.66f, 0.74f)},
                {new Shard(-0.4, 0.72f, 0.32f, 0.74f), new Shard(0.4, 0.72f, 0.32f, 0.74f)},
                {new Shard(0, 1.4f, 0.62f, 0.62f)}};
        /** Ticks entre deux etages de cristal, et ticks que met un etage a pousser. */
        static final int CRYSTAL_EVERY = 9, CRYSTAL_GROW = 7;

        /**
         * Le Titan se cristallise petit a petit : des blocs de cristal poussent sur son corps, des pieds a la tete, le
         * suivent tant que dure l'effet, puis volent en eclats. Lui-meme ne les voit pas (ils lui boucheraient la vue).
         */
        static void crystallize(Player p, int ticks) {
            World w = p.getWorld();
            float size = (float) (p.getAttribute(Attribute.SCALE) == null ? 1 : p.getAttribute(Attribute.SCALE).getValue());
            Player self = com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p) ? null : p;
            List<BlockDisplay> shards = new ArrayList<>();
            List<Shard> shapes = new ArrayList<>();
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(230, 240, 255), 1.3f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    boolean over = !p.isOnline() || p.isDead() || t >= ticks || !isTitan(p);
                    if (over) {
                        for (BlockDisplay d : shards) {
                            if (!d.isValid()) continue;
                            w.spawnParticle(Particle.BLOCK, d.getLocation().add(0, 0.5 * size, 0), 14, 0.3 * size, 0.3 * size, 0.3 * size, Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                            d.remove();
                        }
                        if (!shards.isEmpty()) w.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
                        cancel();
                        return;
                    }
                    int stage = t / CRYSTAL_EVERY;
                    if (t % CRYSTAL_EVERY == 0 && stage < CRYSTAL.length) {
                        for (Shard sh : CRYSTAL[stage]) {
                            float wd = sh.width() * size, ht = sh.height() * size;
                            BlockDisplay d = w.spawn(at(p, sh, size), BlockDisplay.class, disp -> {
                                disp.setBlock((stage % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS).createBlockData());
                                disp.setTransformation(new Transformation(new Vector3f(-wd / 2, 0, -wd / 2), new Quaternionf(), new Vector3f(wd, 0.02f, wd), new Quaternionf()));
                                disp.setBrightness(new Display.Brightness(15, 15));
                                disp.setTeleportDuration(2);
                                disp.setPersistent(false);
                            });
                            if (self != null) self.hideEntity(SNK.plugin(), d);
                            shards.add(d);
                            shapes.add(sh);
                            // Il pousse jusqu'a sa hauteur en quelques ticks.
                            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                                if (!d.isValid()) return;
                                d.setInterpolationDelay(0);
                                d.setInterpolationDuration(CRYSTAL_GROW);
                                d.setTransformation(new Transformation(new Vector3f(-wd / 2, 0, -wd / 2), new Quaternionf(), new Vector3f(wd, ht, wd), new Quaternionf()));
                            }, 2L);
                        }
                        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f + stage * 0.15f);
                        w.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.8f, 0.7f + stage * 0.1f);
                    }
                    for (int k = 0; k < shards.size(); k++) if (shards.get(k).isValid()) shards.get(k).teleport(at(p, shapes.get(k), size));
                    if (t % 5 == 0) w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.9 * size, 0), 6, 0.3 * size, 0.5 * size, 0.3 * size, 0, dust);
                    t++;
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
        }

        /** Le pied d'un morceau de cristal dans le monde : il tourne avec le corps du joueur. */
        private static Location at(Player p, Shard sh, float size) {
            double yaw = Math.toRadians(p.getBodyYaw());
            Location at = p.getLocation().add(sh.x() * size * Math.cos(yaw), sh.y() * size, sh.x() * size * Math.sin(yaw));
            at.setYaw(p.getBodyYaw());
            at.setPitch(0);
            return at;
        }

        /** Il leve un enorme rocher au-dessus de sa tete, puis le jette devant lui. */
        static boolean bestial(Player p) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
            Puppet pu = titanPuppet(p);
            if (pu == null) { throwRock(p); return true; }
            pu.pose("eren_titan_poings_leves", 4);
            float size = (float) (p.getAttribute(Attribute.SCALE) == null ? 1 : p.getAttribute(Attribute.SCALE).getValue());
            // Le rocher grossit entre ses mains, au-dessus de sa tete.
            List<BlockDisplay> held = rock(p.getLocation().add(0, 2.05 * size + ROCK_SIZE / 2, 0), 0.2f);
            for (BlockDisplay d : held) grow(d, 6);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.5f);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                for (BlockDisplay d : held) d.remove();
                if (!p.isOnline() || p.isDead()) return;
                if (Puppet.of(p) == pu) pu.pose("eren_titan_lancer", 2);
                throwRock(p);
            }, 8L);
            pu.endAfter(16, 4);
            return true;
        }

        /** La taille du rocher du Titan Bestial, en blocs. */
        static final float ROCK_SIZE = 2.4f;
        private static final String ROCK_FULL = "crossfight_rock_full";

        /** Un rocher : trois blocs de pierre imbriques de travers, centres sur {@code at}, a {@code scale} fois leur taille. */
        static List<BlockDisplay> rock(Location at, float scale) {
            Location c = at.clone();
            c.setYaw(0);
            c.setPitch(0);
            Object[][] parts = {
                    {Material.COBBLESTONE, 1.0f, new Vector3f(0, 0, 0), new Quaternionf()},
                    {Material.STONE, 0.72f, new Vector3f(0.22f, 0.16f, -0.12f), new Quaternionf().rotationXYZ(0.6f, 0.5f, 0.2f)},
                    {Material.MOSSY_COBBLESTONE, 0.66f, new Vector3f(-0.2f, -0.14f, 0.18f), new Quaternionf().rotationXYZ(-0.4f, 0.9f, 0.5f)}};
            List<BlockDisplay> out = new ArrayList<>();
            for (Object[] part : parts) {
                float full = ROCK_SIZE * (float) part[1];
                Vector3f off = new Vector3f((Vector3f) part[2]).mul(ROCK_SIZE);
                Quaternionf rot = (Quaternionf) part[3];
                out.add(c.getWorld().spawn(c, BlockDisplay.class, d -> {
                    d.setBlock(((Material) part[0]).createBlockData());
                    d.setTransformation(rockShape(off, rot, full * scale, scale));
                    d.setPersistent(false);
                    d.setViewRange(3f);
                    d.setMetadata(ROCK_FULL, new org.bukkit.metadata.FixedMetadataValue(SNK.plugin(), new Object[]{off, rot, full}));
                }));
            }
            return out;
        }

        /** Un bloc du rocher, de cote {@code side}, tourne autour de son centre, decale de {@code off} (a {@code scale} fois). */
        private static Transformation rockShape(Vector3f off, Quaternionf rot, float side, float scale) {
            Vector3f corner = rot.transform(new Vector3f(-side / 2, -side / 2, -side / 2)).add(new Vector3f(off).mul(scale));
            return new Transformation(corner, rot, new Vector3f(side, side, side), new Quaternionf());
        }

        /** Fait grossir un bloc du rocher jusqu'a sa taille en {@code ticks}. */
        private static void grow(BlockDisplay d, int ticks) {
            Object[] full = (Object[]) d.getMetadata(ROCK_FULL).get(0).value();
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                if (!d.isValid()) return;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                d.setTransformation(rockShape((Vector3f) full[0], (Quaternionf) full[1], (float) full[2], 1f));
            }, 2L);
        }

        /** Le rocher part : une fleche invisible le porte ; a l'impact, 2 coeurs et une ejection de 10 blocs. */
        static void throwRock(Player p) {
            Vector dir = p.getEyeLocation().getDirection();
            Arrow a = Effects.shootInstantArrow(p, dir, 1.7, 0.0);
            a.setInvisible(true);
            a.getPersistentDataContainer().set(ROCK, PersistentDataType.BYTE, (byte) 1);
            List<BlockDisplay> parts = rock(a.getLocation(), 1f);
            for (BlockDisplay d : parts) a.addPassenger(d);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { for (BlockDisplay d : parts) d.remove(); if (a.isValid()) a.remove(); }, 80L);
        }

/** Il arme sa main en griffe derriere l'epaule, puis la rabat devant lui : le coup part a ce moment-la. */
        static boolean machoire(Player p) {
            Puppet pu = titanPuppet(p);
            if (pu == null) { claw(p); return true; }
            // De vraies griffes au bout de ses deux mains, le temps du coup (l'image "griffes" du pack).
            if (com.kiokouwhite.crossfight.util.Fx.has("griffes")) {
                ItemStack claws = com.kiokouwhite.crossfight.util.Fx.image("griffes", Color.WHITE);
                pu.held(claws);
                pu.second(claws);
            }
            pu.pose("eren_titan_griffe_arme", 3);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                if (!p.isOnline() || p.isDead()) return;
                if (Puppet.of(p) == pu) pu.pose("eren_titan_griffe", 2);
                claw(p);
            }, 4L);
            pu.endAfter(11, 3);
            return true;
        }

        /** Le coup de griffe : trois sillons devant lui, 4 coeurs a ce qu'ils touchent. */
        static void claw(Player p) {
            World w = p.getWorld();
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            Location c = eye.clone().add(dir.clone().multiply(1.5));
            Vector side = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            for (int k = -1; k <= 1; k++) {
                w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(side.clone().multiply(k * 0.5)).add(0, k * 0.3, 0), 1);
            }
            w.spawnParticle(Particle.CRIT, c, 25, 0.6, 0.6, 0.6, 0.3);
            w.playSound(c, Sound.ENTITY_RAVAGER_ATTACK, 1f, 1.3f);
            int n = 0;
            for (LivingEntity enemy : Effects.enemiesWithin(p, c, 1.6)) { Effects.damage(enemy, 8.0, p); n++; }
            if (n == 0) Msg.actionBar(p, "<gray>Titan Machoire : dans le vide.");
        }

/** Il se jette a quatre pattes et file : le dash, puis le galop tant que dure la vitesse. */
        static boolean charrette(Player p) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Location to = p.getLocation().add(dir.normalize().multiply(10));
            SNK.rush(p, to, 4, Particle.CLOUD);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> Effects.give(p, PotionEffectType.SPEED, 1, 100), 5L);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            Puppet pu = titanPuppet(p);
            if (pu == null) return true;
            pu.pose("eren_titan_galop_a", 2);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || Puppet.of(p) != pu || t >= GALLOP) { cancel(); return; }
                    // Deux temps qui alternent tous les trois ticks : un galop tres rapide.
                    if (t % 3 == 0) pu.pose((t / 3) % 2 == 0 ? "eren_titan_galop_a" : "eren_titan_galop_b", 3);
                    if (t % 6 == 0) {
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 0.8f, 1.3f);
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.02);
                    }
                }
            }.runTaskTimer(SNK.plugin(), 1L, 1L);
            pu.endAfter(GALLOP, 4);
            return true;
        }
        /** La duree du galop a quatre pattes : le dash, puis les 5 secondes de vitesse. */
        static final int GALLOP = 105;

        /** L'arme jaillit du sol devant lui ; il l'empoigne et la brandit. */
        static boolean marteau(Player p) {
            if (hasTitanWeapon(p)) { Msg.actionBar(p, "<red>Marteau d'Armes : detruis d'abord l'arme actuelle."); return false; }
            int r = (int) (Math.random() * 3);
            ItemStack it;
            if (r == 0) {
                it = Weapons.melee(Material.NETHERITE_SWORD, 12.0, "<red><bold>Epee du Marteau d'Armes", List.of("<gray>12 dmg. Se brise au bout de 4 coups."));
                setWeapon(it, "sword", 4);
            } else if (r == 1) {
                it = Items.unbreakable(Items.of(Material.CROSSBOW, "<red><bold>Arbalete du Marteau d'Armes", List.of("<gray>Clic droit : un carreau de 4 coeurs (un seul tir).")));
                setWeapon(it, "crossbow", 1);
            } else {
                it = Weapons.melee(Material.TRIDENT, 8.0, "<red><bold>Lance du Marteau d'Armes", List.of("<gray>8 dmg. Clic droit : frappe a 5 blocs.", "<gray>Se brise au bout de 5 coups."));
                setWeapon(it, "spear", 5);
            }
            String name = r == 0 ? "epee" : r == 1 ? "arbalete" : "lance";
            Puppet pu = titanPuppet(p);
            if (pu == null) { forge(p, it, name); return true; }
            World w = p.getWorld();
            float size = (float) (p.getAttribute(Attribute.SCALE) == null ? 1 : p.getAttribute(Attribute.SCALE).getValue());
            Vector flat = p.getLocation().getDirection().setY(0);
            if (flat.lengthSquared() < 0.01) flat = new Vector(0, 0, 1);
            // L'arme part de sous le sol, juste devant lui, et monte a hauteur de main en trois ticks.
            Location ground = p.getLocation().add(flat.normalize().multiply(0.75 * size));
            Location low = ground.clone().add(0, -1.2 * size, 0), high = ground.clone().add(0, 1.1 * size, 0);
            ItemStack shown = new ItemStack(it.getType());
            org.bukkit.entity.ItemDisplay rising = w.spawn(low, org.bukkit.entity.ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.VERTICAL);
                // L'image d'une arme pointe en haut a droite : un huitieme de tour la dresse, pointe vers le ciel.
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationZ((float) Math.toRadians(45)), new Vector3f(1.5f * size, 1.5f * size, 1.5f * size), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(3);
                d.setPersistent(false);
            });
            pu.pose("eren_titan_saisit", 3);
            w.playSound(ground, Sound.BLOCK_STONE_BREAK, 1f, 0.5f);
            w.playSound(ground, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.4f);
            w.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 50, 0.5, 0.2, 0.5, ground.clone().add(0, -1, 0).getBlock().getBlockData());
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { if (rising.isValid()) rising.teleport(high); }, 1L);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                rising.remove();
                if (!p.isOnline() || p.isDead()) return;
                if (Puppet.of(p) == pu) { pu.held(shown); pu.pose("eren_titan_brandit", 3); }
                forge(p, it, name);
            }, 6L);
            pu.endAfter(17, 4);
            return true;
        }

        /** L'arme du Marteau d'Armes arrive dans la main du Titan. */
        static void forge(Player p, ItemStack it, String name) {
            var left = p.getInventory().addItem(it);
            for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
            Msg.actionBar(p, "<gold>Marteau d'Armes : " + name + " forgee !");
        }
    }

    /** Un pouvoir de titan : item individuel, donne a la transformation, utilisable sous forme de Titan. */
    static final class TitanPower extends ActiveAbility {
        final String titan;
        TitanPower(String titan) {
            super("eren_t_" + titan, TITAN_NAMES[index(titan)], TITAN_DESC[index(titan)], TITAN_ICONS[index(titan)], TITAN_CD[index(titan)]);
            this.titan = titan;
        }
        @Override public boolean hasItem() { return false; }
        @Override public boolean showInBar(Player p, CfPlayer cf, AbilityState st) { return isTitan(p) && owns(p, titan); }
        @Override protected List<String> extraLore() {
            return titan.equals("cuirasse") ? List.of("", "<yellow>Clic droit : intervertir") : super.extraLore();
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (!titan.equals("cuirasse")) return null;
            AbilityState ts = titansState(p);
            return "<yellow>Cuirasse <white>[" + ("speed".equals(ts == null ? "res" : ts.get("cuirasse", "res")) ? "Speed" : "Resistance") + "]";
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (!isTitan(p)) { Msg.actionBar(p, "<red>Les pouvoirs des titans ne s'utilisent que sous forme de Titan."); return false; }
            if (!owns(p, titan)) { Msg.actionBar(p, "<red>Tu n'as pas vole ce titan."); return false; }
            boolean done = use(p);
            // La carte du pouvoir en haut a gauche : le temps de son effet, ou un instant pour un coup.
            if (done && !titan.equals("cuirasse")) showFor(p, switch (titan) { case "feminin" -> 200; case "charrette" -> 100; case "colossal" -> 60; default -> 40; });
            return done;
        }

        private boolean use(Player p) {
            return switch (titan) {
                case "colossal" -> TitansPrimordiaux.colossal(p);
                case "feminin" -> TitansPrimordiaux.feminin(p);
                case "cuirasse" -> {
                    AbilityState ts = titansState(p);
                    if (ts != null) { ts.set("cuirasse", "speed".equals(ts.get("cuirasse", "res")) ? "res" : "speed"); TitanAssaillant.applyCuirasse(p, ts); }
                    yield true;
                }
                case "bestial" -> TitansPrimordiaux.bestial(p);
                case "machoire" -> TitansPrimordiaux.machoire(p);
                case "charrette" -> TitansPrimordiaux.charrette(p);
                case "marteau" -> TitansPrimordiaux.marteau(p);
                default -> false;
            };
        }
    }

    static void setWeapon(ItemStack it, String kind, int uses) {
        var meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(TITAN_WEAPON, PersistentDataType.STRING, kind);
        meta.getPersistentDataContainer().set(USES, PersistentDataType.INTEGER, uses);
        it.setItemMeta(meta);
    }
    static String weaponKind(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(TITAN_WEAPON, PersistentDataType.STRING);
    }
    static boolean hasTitanWeapon(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (weaponKind(it) != null) return true;
        return false;
    }
    /** Un coup de plus sur une arme du Marteau d'Armes ; la brise quand elle a servi. */
    static void useWeapon(Player p, ItemStack it) {
        var meta = it.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 1) - 1;
        if (uses <= 0) {
            p.getInventory().remove(it);
            p.getInventory().setItemInMainHand(null);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.8f);
            p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1, it.clone());
            Msg.actionBar(p, "<red>L'arme du Marteau d'Armes se brise.");
            return;
        }
        meta.getPersistentDataContainer().set(USES, PersistentDataType.INTEGER, uses);
        it.setItemMeta(meta);
    }

    // ----------------------------------------------------------------- ultime

    /** Forme de Titan, jusqu'a la mort. Clic droit sous cette forme : regeneration de 5 coeurs en 3 s. */
    static final class TitanAssaillant extends UltimateAbility implements CustomUse {
        TitanAssaillant() {
            super("eren_ult", "Titan Assaillant", "Eren se transforme en Titan jusqu'a sa mort : Force, Speed, il regenere 15% des degats qu'il inflige et obtient les pouvoirs des titans voles, mais ne peut plus manger de Pommes Dorees ni utiliser son equipement ou sa Charge. Clic droit sous cette forme : immobile 3 secondes, regenere 5 coeurs (50 s).", Material.ROTTEN_FLESH);
        }

        /** La regeneration du Titan garde son rugissement, sans redire la replique de la transformation. */
        @Override public String useSoundKey() { return "eren_ult_regen"; }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!st.get("titan", false)) return false;
            if (!cdReady(st, "regenAt")) { Msg.actionBar(p, "<red>Regeneration : encore " + cdLeft(st, "regenAt") + "s"); return true; }
            startCd(p, st, "regenAt", 50);
            Effects.root(p, 60);
            Msg.actionBar(p, "<green>Le Titan se regenere...");
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || ticks >= 60) { cancel(); return; }
                    ticks += 10;
                    Effects.heal(p, 10.0 / 6.0);
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1.5, 0), 8, 0.5, 0.6, 0.5, 0.02);
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.5, 0), 2, 0.5, 0.3, 0.5);
                }
            }.runTaskTimer(SNK.plugin(), 10L, 10L);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
            return true;
        }

        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            if (st.get("titan", false)) return false;
            if (!metamorphose(p, cf, st)) transform(p, cf, st);
            return true;
        }

        /** La transformation d'un coup (sans le modele articule dans le pack) : l'eclair, et le Titan est la. */
        static void transform(Player p, CfPlayer cf, AbilityState st) {
            becomeTitan(p, cf, st);
            lightning(p);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
            Msg.send(p, TITAN_MESSAGE);
        }

        static final String TITAN_MESSAGE = "<dark_red><bold>Tu es le Titan Assaillant.</bold> <gray>Plus de Pommes Dorees, d'equipement ni de Charge. Clic droit sur l'ultime : regeneration.";

        /** L'eclair de la transformation : la foudre, son tonnerre, un eclat dore et la vapeur qui jaillit. */
        static void lightning(Player p) {
            World w = p.getWorld();
            Location at = p.getLocation();
            w.strikeLightningEffect(at);
            w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.8f);
            w.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.5, 0), 120, 1.5, 1.5, 1.5, 0.1);
            w.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 240, 200));
        }

        // La transformation, en ticks depuis le lancement : la morsure, l'eclair, puis les os, les muscles et la peau.
        static final int BITE = 9, BOLT = 14, BONES = 21, FLESH = 39, SKIN = 57, DONE = 74;
        /** L'ordre dans lequel le Titan se forme : la colonne et les cotes d'abord, puis le crane, les bras, les jambes. */
        static final String[][] GROWTH = {{"body"}, {"head"}, {"arm_r", "arm_l"}, {"leg_r", "leg_l"}};
        static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 196, 40), 1.6f),
                BONE_DUST = new Particle.DustOptions(Color.fromRGB(236, 230, 208), 1.5f),
                FLESH_DUST = new Particle.DustOptions(Color.fromRGB(170, 36, 32), 1.6f);

        /**
         * La transformation, comme dans l'anime : Eren se mord la main, l'eclair dore tombe, et le Titan se forme
         * petit a petit dans la vapeur (le squelette os par os, puis les muscles a vif, puis la peau du Titan
         * Assaillant) avant de rugir. Eren ne bouge pas et ne craint rien pendant ces trois secondes et demie.
         * Rend false si le pack n'a pas les modeles articules : la transformation se fait alors d'un coup.
         */
        static boolean metamorphose(Player p, CfPlayer cf, AbilityState st) {
            if (!Puppet.available("eren_squelette")) return false;
            Puppet bite = Puppet.show(p, "eren", null, "debout");
            if (bite == null) return false;
            // Des maintenant il n'est plus question de relancer l'ultime ni de se servir de l'equipement.
            st.set("titan", true);
            Bukkit.getScheduler().runTask(SNK.plugin(), () -> cf.setUltimateLocked(true));
            Effects.root(p, DONE);
            Effects.invincible(p, DONE * 50L + 300);
            bite.pose("eren_morsure", 5);
            World w = p.getWorld();
            new BukkitRunnable() {
                int t = 0;
                Puppet titan;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || !st.get("titan", false)) { cancel(); return; }
                    Location at = p.getLocation();
                    if (t == BITE) {
                        // La morsure : le sang gicle de sa main.
                        Location hand = Puppet.of(p) == bite ? bite.hand() : at.clone().add(0, 1.5, 0);
                        w.spawnParticle(Particle.BLOCK, hand, 14, 0.08, 0.08, 0.08, 0.05, Material.REDSTONE_BLOCK.createBlockData());
                        w.spawnParticle(Particle.DUST, hand, 8, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(150, 10, 10), 1.0f));
                        w.playSound(at, Sound.ENTITY_GENERIC_EAT, 1f, 0.6f);
                        w.playSound(at, Sound.ENTITY_PLAYER_HURT, 0.8f, 0.8f);
                    }
                    if (t > BITE && t < BOLT) {
                        // L'etincelle doree qui annonce l'eclair, autour de sa main.
                        Location hand = Puppet.of(p) == bite ? bite.hand() : at.clone().add(0, 1.5, 0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 6, 0.25, 0.25, 0.25, 0.12);
                        w.spawnParticle(Particle.DUST, hand, 4, 0.2, 0.2, 0.2, 0, GOLD);
                    }
                    if (t == BOLT) {
                        // L'eclair : Eren devient le Titan (taille, bonus, objets, skin), encore invisible dans la vapeur.
                        becomeTitan(p, cf, st);
                        lightning(p);
                        for (int i = 0; i < 36; i++) {
                            Vector d = new Vector(Math.random() - 0.5, Math.random() * 0.9, Math.random() - 0.5).normalize().multiply(1.2 + Math.random() * 2.6);
                            w.spawnParticle(Particle.DUST, at.clone().add(0, 1.2, 0).add(d), 1, 0, 0, 0, 0, GOLD);
                        }
                        w.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1.8, 0), 90, 1.6, 2.0, 1.6, 0.4);
                        w.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1.5, 0), 3, 0.8, 0.8, 0.8, 0);
                        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, 1.0, 0), 18, 1.6, 1.0, 1.6, 0.04);
                        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                        // Le Titan qui se forme : deux fois la taille d'Eren, aucune partie visible pour l'instant.
                        titan = Puppet.show(p, "eren_squelette", null, "eren_titan_naissance", 2f);
                        if (titan != null) { titan.look(null, null); titan.endAfter(DONE - BOLT, 5); }
                    }
                    if (t > BOLT && t < DONE && t % 2 == 0) {
                        // La vapeur qui l'entoure pendant qu'il se forme.
                        w.spawnParticle(Particle.CLOUD, at.clone().add(0, 2.0, 0), 5, 1.3, 1.9, 1.3, 0.02);
                        if (t % 6 == 0) w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.6f);
                    }
                    boolean shown = titan != null && Puppet.of(p) == titan;
                    for (int i = 0; i < GROWTH.length; i++) {
                        if (t == BONES + i * 4) {
                            // Les os sortent de la vapeur, un a un.
                            for (String part : GROWTH[i]) {
                                if (shown) titan.look(part, "eren_squelette");
                                Location c = shown ? titan.center(part) : at.clone().add(0, 2, 0);
                                w.spawnParticle(Particle.DUST, c, 12, 0.35, 0.5, 0.35, 0, BONE_DUST);
                                w.spawnParticle(Particle.CLOUD, c, 6, 0.3, 0.4, 0.3, 0.03);
                            }
                            w.playSound(at, Sound.BLOCK_BONE_BLOCK_PLACE, 1f, 0.55f + i * 0.08f);
                            w.playSound(at, Sound.ENTITY_SKELETON_HURT, 0.6f, 0.5f);
                        }
                        if (t == FLESH + i * 4) {
                            // Les muscles poussent sur les os.
                            for (String part : GROWTH[i]) {
                                if (shown) titan.look(part, "eren_muscles");
                                Location c = shown ? titan.center(part) : at.clone().add(0, 2, 0);
                                w.spawnParticle(Particle.DUST, c, 14, 0.35, 0.5, 0.35, 0, FLESH_DUST);
                                w.spawnParticle(Particle.BLOCK, c, 8, 0.3, 0.45, 0.3, 0.02, Material.NETHER_WART_BLOCK.createBlockData());
                            }
                            w.playSound(at, Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, 0.5f);
                            w.playSound(at, Sound.ENTITY_SLIME_SQUISH, 0.7f, 0.5f);
                        }
                    }
                    if (t == SKIN) {
                        // La peau se referme d'un coup, dans un dernier jet de vapeur ; il se redresse et rugit.
                        if (shown) {
                            if (Puppet.available("eren_titan")) titan.look(null, "eren_titan");
                            titan.pose("eren_titan_rugit", 5);
                        }
                        w.spawnParticle(Particle.CLOUD, at.clone().add(0, 2.0, 0), 90, 1.4, 1.9, 1.4, 0.12);
                        w.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
                        w.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
                    }
                    if (t > SKIN + 3 && t < DONE - 4 && t % 3 == 0) {
                        // Le rugissement : des ondes qui partent de sa bouche.
                        Location mouth = shown ? titan.center("head") : at.clone().add(0, 3.3, 0);
                        w.spawnParticle(Particle.SONIC_BOOM, mouth, 1, 0, 0, 0, 0);
                    }
                    if (t >= DONE) {
                        Msg.send(p, TITAN_MESSAGE);
                        cancel();
                    }
                }
            }.runTaskTimer(SNK.plugin(), 1L, 1L);
            return true;
        }

        /** Eren devient le Titan : la taille, les bonus, les objets des titans voles, et le skin du Titan Assaillant. */
        static void becomeTitan(Player p, CfPlayer cf, AbilityState st) {
            st.set("titan", true);
            Bukkit.getScheduler().runTask(SNK.plugin(), () -> cf.setUltimateLocked(true));
            Boosts.set(p, "eren_titan", new Boosts.Boost(1, 1, 0, 0));
            var scale = p.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(2.0);
            var step = p.getAttribute(Attribute.STEP_HEIGHT);
            if (step != null) step.setBaseValue(1.1);
            if (owns(p, "cuirasse")) applyCuirasse(p, titansState(p));
            giveTitanItems(p);
            if (Skins.has("eren_titan")) Skins.wear(p, "eren_titan");
            World w = p.getWorld();
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !st.get("titan", false)) { cancel(); return; }
                    w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 2.0, 0), 2, 0.5, 0.8, 0.5, 0.01);
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation().add(0, 2.5, 0), 1, 0.3, 0.3, 0.3, 0.005);
                    if (t % 12 == 0) w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_AMBIENT, 0.4f, 0.7f);
                }
            }.runTaskTimer(SNK.plugin(), 10L, 10L);
        }

        /**
         * Sous forme de Titan : l'equipement, la Charge et l'item des Titans primordiaux laissent la place
         * a un item par pouvoir vole (barre d'abord, puis inventaire).
         */
        static void giveTitanItems(Player p) {
            CfPlayer cf = SNK.plugin().players().get(p);
            if (cf.character() == null) return;
            var inv = p.getInventory();
            var key = com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY;
            java.util.Set<String> present = new java.util.HashSet<>();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                String id = Items.tag(it, key);
                if (id == null) continue;
                if (id.equals("snk_odm") || id.equals("eren_charge") || id.equals("eren_titans")) inv.setItem(i, null);
                else present.add(id);
            }
            for (String titan : titansOf(p)) {
                String id = "eren_t_" + titan;
                if (present.contains(id)) continue;
                Ability a = cf.character().ability(id);
                if (a == null) continue;
                ItemStack item = a.createItem();
                int slot = -1;
                for (int i = 1; i <= 7 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
                for (int i = 9; i < 36 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
                if (slot >= 0) inv.setItem(slot, item); else inv.addItem(item);
            }
        }

        static void removeTitanItems(Player p) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                String id = Items.tag(inv.getItem(i), com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY);
                if (id != null && id.startsWith("eren_t_")) inv.setItem(i, null);
            }
        }

        static void applyCuirasse(Player p, AbilityState titans) {
            if (titans == null || !isTitan(p)) return;
            boolean speed = "speed".equals(titans.get("cuirasse", "res"));
            Boosts.set(p, "eren_cuirasse", speed ? new Boosts.Boost(0, 1, 0, 0) : new Boosts.Boost(0, 0, 1, 0));
            Msg.actionBar(p, "<gray>Titan Cuirasse : <white>" + (speed ? "Speed" : "Resistance"));
            plates(p, speed);
        }

        /** Une plaque de la cuirasse dans le repere du corps (a l'echelle d'un joueur) : son centre au sol, son pied, sa largeur, sa hauteur, son epaisseur. */
        private record Plate(float x, float y, float z, float w, float h, float d) {}
        /** Resistance : la cuirasse couvre le torse (poitrine, dos, les deux epaules). */
        private static final Plate[] ON_TORSO = {
                new Plate(0, 0.80f, 0.16f, 0.56f, 0.60f, 0.10f), new Plate(0, 0.80f, -0.16f, 0.56f, 0.60f, 0.10f),
                new Plate(-0.36f, 1.22f, 0, 0.30f, 0.22f, 0.34f), new Plate(0.36f, 1.22f, 0, 0.30f, 0.22f, 0.34f)};
        /** Speed : elle descend sur les jambes (les deux tibias, les deux cuisses). */
        private static final Plate[] ON_LEGS = {
                new Plate(-0.13f, 0.05f, 0.14f, 0.24f, 0.36f, 0.08f), new Plate(0.13f, 0.05f, 0.14f, 0.24f, 0.36f, 0.08f),
                new Plate(-0.13f, 0.42f, 0.14f, 0.24f, 0.32f, 0.08f), new Plate(0.13f, 0.42f, 0.14f, 0.24f, 0.32f, 0.08f)};
        private static final java.util.Map<UUID, List<BlockDisplay>> PLATES = new java.util.HashMap<>();
        /** Ticks que mettent les plaques a glisser du torse aux jambes (ou l'inverse). */
        static final int PLATES_MOVE = 10;

        private static Transformation plateShape(Plate pl, float size, float grown) {
            return new Transformation(new Vector3f(pl.x() - pl.w() / 2 * grown, pl.y(), pl.z() - pl.d() / 2 * grown).mul(size), new Quaternionf(),
                    new Vector3f(pl.w(), pl.h(), pl.d()).mul(size * grown), new Quaternionf());
        }

        /**
         * Les plaques du Titan Cuirasse, sur le torse (Resistance) ou sur les jambes (Speed). Elles suivent le Titan
         * tant qu'il l'est ; quand il intervertit, elles glissent d'un endroit a l'autre. Lui-meme ne les voit pas.
         */
        static void plates(Player p, boolean speed) {
            Plate[] layout = speed ? ON_LEGS : ON_TORSO;
            World w = p.getWorld();
            float size = (float) (p.getAttribute(Attribute.SCALE) == null ? 1 : p.getAttribute(Attribute.SCALE).getValue());
            List<BlockDisplay> have = PLATES.get(p.getUniqueId());
            if (have != null && have.stream().allMatch(BlockDisplay::isValid)) {
                // Le transfert : chaque plaque glisse jusqu'a sa nouvelle place, dans un bruit de metal.
                for (int k = 0; k < have.size(); k++) {
                    BlockDisplay d = have.get(k);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(PLATES_MOVE);
                    d.setTransformation(plateShape(layout[k], size, 1f));
                }
                w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.7f);
                w.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.3f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (++t > PLATES_MOVE || !p.isOnline()) { cancel(); if (p.isOnline()) w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f); return; }
                        // Des eclats le long du corps, qui suivent les plaques : ils montent ou descendent avec elles.
                        double f = t / (double) PLATES_MOVE, y = (speed ? 1.1 - 0.8 * f : 0.3 + 0.8 * f) * size;
                        w.spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, y, 0), 6, 0.25 * size, 0.05, 0.25 * size, 0.02);
                    }
                }.runTaskTimer(SNK.plugin(), 0L, 1L);
                return;
            }
            removePlates(p);
            Player self = com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p) ? null : p;
            List<BlockDisplay> made = new ArrayList<>();
            for (Plate pl : layout) {
                BlockDisplay d = w.spawn(plateAt(p), BlockDisplay.class, disp -> {
                    disp.setBlock(Material.BONE_BLOCK.createBlockData());
                    disp.setTransformation(plateShape(pl, size, 0.05f));
                    disp.setTeleportDuration(1);
                    disp.setPersistent(false);
                });
                if (self != null) self.hideEntity(SNK.plugin(), d);
                made.add(d);
                // Elles poussent sur lui en quelques ticks.
                Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                    if (!d.isValid()) return;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(6);
                    d.setTransformation(plateShape(pl, size, 1f));
                }, 2L);
            }
            PLATES.put(p.getUniqueId(), made);
            w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.7f);
            new BukkitRunnable() {
                @Override public void run() {
                    if (PLATES.get(p.getUniqueId()) != made) { cancel(); return; }
                    if (!p.isOnline() || p.isDead() || !isTitan(p) || !owns(p, "cuirasse")) { removePlates(p); cancel(); return; }
                    Location at = plateAt(p);
                    for (BlockDisplay d : made) if (d.isValid()) d.teleport(at);
                }
            }.runTaskTimer(SNK.plugin(), 1L, 1L);
        }

        /** Ou se tiennent les plaques : aux pieds du Titan, tournees comme son corps (leur place sur lui est dans leur forme). */
        private static Location plateAt(Player p) {
            Location at = p.getLocation();
            at.setYaw(p.getBodyYaw());
            at.setPitch(0);
            return at;
        }

        static void removePlates(Player p) {
            List<BlockDisplay> old = PLATES.remove(p.getUniqueId());
            if (old != null) for (BlockDisplay d : old) if (d.isValid()) d.remove();
        }

        static void revert(Player p, CfPlayer cf, AbilityState st) {
            st.set("titan", false);
            Puppet forming = Puppet.of(p);
            if (forming != null) forming.remove();
            if (Skins.has("eren_titan") && p.isOnline()) Skins.apply(p, cf.character() != null && "eren".equals(cf.character().id()) ? cf.character() : null);
            removeTitanItems(p);
            cf.setUltimateLocked(false);
            Boosts.remove(p, "eren_titan");
            Boosts.remove(p, "eren_cuirasse");
            removePlates(p);
            var scale = p.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(1.0);
            var step = p.getAttribute(Attribute.STEP_HEIGHT);
            if (step != null) step.setBaseValue(0.6);
        }

        /** Vol de vie : 15% des degats infliges. */
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (!st.get("titan", false)) return;
            double heal = e.getFinalDamage() * 0.15;
            if (heal > 0) Bukkit.getScheduler().runTask(SNK.plugin(), () -> { if (attacker.isOnline() && !attacker.isDead()) Effects.heal(attacker, heal); });
        }

        /**
         * A la mort du Titan : Eren sort de la nuque sans perdre de vie, la ou le Titan est tombe.
         * Retour a la normale, et la chaleur du Colossal si Eren l'a vole.
         */
        @Override public boolean onDeath(Player p, CfPlayer cf, AbilityState st) {
            if (!st.get("titan", false)) return false;
            Location at = p.getLocation().clone();
            boolean colossal = owns(p, "colossal");
            revert(p, cf, st);
            World w = at.getWorld();
            w.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.5, 0), 150, 1.5, 1.5, 1.5, 0.1);
            w.playSound(at, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.6f);
            SNK.plugin().game().broadcastNear(p, "<dark_red>Le Titan d'Eren s'effondre : <gray>Eren en sort, sans perdre de vie.");
            if (colossal) heatZone(p, at);
            // Deux ticks plus tard : apres la reapparition vanilla declenchee par DeathListener.
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                if (!p.isOnline()) return;
                if (SNK.plugin().game().isTest()) { p.teleport(at); p.setGameMode(org.bukkit.GameMode.SURVIVAL); }
                else SNK.plugin().lives().respawnAt(p, at);
                SNK.invincible(p, 3000L);
                Msg.send(p, "<green>Tu sors de ton Titan : aucune vie perdue.");
            }, 3L);
            return true;
        }

        @Override public void onUnequip(Player p, CfPlayer cf) {
            AbilityState st = SNK.plugin().abilities().state(p, this);
            if (st.get("titan", false)) revert(p, cf, st);
        }

        /** Zone de chaleur de 7 blocs pendant 5 s : 0.5 coeur par seconde et feu. */
        static void heatZone(Player eren, Location c) {
            World w = c.getWorld();
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 5) { cancel(); return; }
                    w.spawnParticle(Particle.CLOUD, c.clone().add(0, 1, 0), 120, 3.5, 1.5, 3.5, 0.03);
                    w.spawnParticle(Particle.FLAME, c.clone().add(0, 0.5, 0), 40, 3.5, 0.5, 3.5, 0.01);
                    w.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
                    for (LivingEntity enemy : Effects.within(c, 7, le -> Effects.isEnemy(eren, le))) {
                        Effects.damage(enemy, 1.0, eren);
                        enemy.setFireTicks(40);
                    }
                }
            }.runTaskTimer(SNK.plugin(), 0L, 20L);
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!isTitan(e.getPlayer())) return;
        Material m = e.getItem().getType();
        if (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "<red>Un Titan ne mange pas de Pommes Dorees.");
        }
    }

    /** Armes du Marteau d'Armes : l'arbalete tire au clic droit, la lance frappe a 5 blocs. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        String kind = weaponKind(it);
        if (kind == null) return;
        Player p = e.getPlayer();
        if (kind.equals("crossbow")) {
            e.setCancelled(true);
            if (!SNK.plugin().game().isRunning()) return;
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 3.0, 0.5, Material.RED_CONCRETE, Color.fromRGB(200, 40, 40));
            a.getPersistentDataContainer().set(BOLT, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1f);
            useWeapon(p, it);
        } else if (kind.equals("spear")) {
            e.setCancelled(true);
            if (!SNK.plugin().game().isRunning()) return;
            if (p.hasCooldown(Material.TRIDENT)) return;
            p.setCooldown(Material.TRIDENT, 10);
            World w = p.getWorld();
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            LivingEntity hit = null;
            for (double d = 0.5; d <= 5 && hit == null; d += 0.5) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                w.spawnParticle(Particle.CRIT, l, 1, 0.05, 0.05, 0.05, 0);
                if (!l.getBlock().isPassable()) break;
                for (LivingEntity enemy : Effects.enemiesWithin(p, l, 1.0)) { hit = enemy; break; }
            }
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
            if (hit != null) {
                Effects.damage(hit, 8.0, p);
                w.spawnParticle(Particle.SWEEP_ATTACK, hit.getLocation().add(0, 1, 0), 1);
                useWeapon(p, it);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent e) {
        Player p = com.kiokouwhite.crossfight.listener.DamageListener.meleeAttackerOf(e.getDamager());
        if (p == null || !(e.getEntity() instanceof LivingEntity)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        String kind = weaponKind(it);
        if (kind == null || kind.equals("crossbow")) return;
        useWeapon(p, it);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        if (a.getPersistentDataContainer().has(ROCK)) {
            for (Entity pass : a.getPassengers()) pass.remove();
            Location c = a.getLocation();
            c.getWorld().spawnParticle(Particle.BLOCK, c, 120, 1.2, 1.2, 1.2, 0.1, Material.COBBLESTONE.createBlockData());
            c.getWorld().spawnParticle(Particle.EXPLOSION, c, 2, 0.6, 0.6, 0.6, 0);
            c.getWorld().playSound(c, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
            c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            if (e.getHitEntity() instanceof LivingEntity target && Effects.isEnemy(p, target)) {
                Effects.damage(target, 4.0, p);
                Bukkit.getScheduler().runTask(SNK.plugin(), () -> Effects.knockbackFrom(target, p.getLocation(), 10));
            }
            Bukkit.getScheduler().runTask(SNK.plugin(), () -> { if (a.isValid()) a.remove(); });
        } else if (a.getPersistentDataContainer().has(BOLT)) {
            if (e.getHitEntity() instanceof LivingEntity target && Effects.isEnemy(p, target)) Effects.damage(target, 8.0, p);
        }
    }
}
