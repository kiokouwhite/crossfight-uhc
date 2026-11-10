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
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
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
        if (isTitan(p) && owned.contains("cuirasse")) TitanAssaillant.applyCuirasse(p, titansState(p));
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
        Effects.noFallUntil(p, 5000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                try {
                    if (!p.isOnline() || p.isDead() || target.isDead() || t > 25 || gearSt.get("stop", false)) { stop(); return; }
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
                    SNK.OdmGear.cable(p, target.getLocation().add(0, 1, 0));
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0, SNK.GAS);
                } catch (Exception ex) { stop(); }
            }
            void stop() { gearSt.setEnabled(false); gearSt.set("stop", false); if (p.isOnline()) Effects.noFallUntil(p, 5000); cancel(); }
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
            SNK.rush(p, to, 4, Particle.CLOUD);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.3f);
            return true;
        }
    }

    static final class EliminationMorale extends PassiveAbility {
        EliminationMorale() {
            super("eren_morale", "Elimination Morale", "Eren gagne 0.2 point de Force supplementaire par kill de l'adversaire qu'il frappe.");
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (!(victim instanceof Player v)) return;
            int kills = SNK.plugin().players().get(v).kills();
            if (kills > 0) e.setDamage(e.getDamage() + 0.6 * kills);
        }
    }

    /** Les pouvoirs des titans voles : un menu, utilisable sous forme de Titan seulement. */
    static final class TitansPrimordiaux extends PassiveAbility implements CustomUse {
        TitansPrimordiaux() {
            super("eren_titans", "Titans primordiaux", "4 adversaires sont choisis au hasard au debut de la partie : chaque fois qu'Eren en tue un, il vole les pouvoirs d'un des 7 titans primordiaux. Clic droit sous forme de Titan pour les utiliser.", Material.NETHER_STAR);
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit (Titan) : pouvoirs voles"); }

        @Override public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = SNK.plugin().abilities().state(p, this);
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
            if (!names.isEmpty()) Msg.send(p, "<gold>Titans primordiaux : tes cibles sont <white>" + String.join(", ", names) + "</white>.");
        }

        @Override public void onKill(Player killer, Player victim, AbilityState st) {
            List<String> targets = st.get("targets", null);
            boolean primordial = SNK.plugin().game().isTest() || (targets != null && targets.contains(victim.getUniqueId().toString()));
            if (!primordial) return;
            List<String> owned = titansOf(killer);
            List<String> left = new ArrayList<>();
            for (String id : TITAN_IDS) if (!owned.contains(id)) left.add(id);
            if (left.isEmpty()) return;
            String id = left.get((int) (Math.random() * left.size()));
            owned.add(id);
            Msg.send(killer, "<gold>Tu as devore " + victim.getName() + " et vole le pouvoir du <white>" + TITAN_NAMES[index(id)] + "</white> !");
            killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
            if (isTitan(killer) && id.equals("cuirasse")) TitanAssaillant.applyCuirasse(killer, st);
        }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!isTitan(p)) { Msg.actionBar(p, "<red>Les pouvoirs des titans ne s'utilisent que sous forme de Titan."); return true; }
            List<String> owned = titansOf(p);
            if (owned.isEmpty()) { Msg.actionBar(p, "<red>Aucun pouvoir de titan vole pour l'instant."); return true; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (String id : owned) {
                int i = index(id);
                List<String> lore = new ArrayList<>(Items.wrap(TITAN_DESC[i]));
                if (id.equals("cuirasse")) lore.add("<yellow>Mode actuel : <white>" + ("speed".equals(st.get("cuirasse", "res")) ? "Speed" : "Resistance"));
                else if (!cdReady(st, "cd_" + id)) lore.add("<red>Encore " + cdLeft(st, "cd_" + id) + "s");
                else lore.add("<green>Pret");
                options.add(new ChoiceMenu.Option(TITAN_ICONS[i], TITAN_NAMES[i], lore, () -> usePower(p, cf, st, id)));
            }
            ChoiceMenu.open(p, "<dark_red><bold>Pouvoirs de Titan", options);
            return true;
        }

        void usePower(Player p, CfPlayer cf, AbilityState st, String id) {
            if (!isTitan(p)) return;
            int i = index(id);
            if (!id.equals("cuirasse") && !cdReady(st, "cd_" + id)) { Msg.actionBar(p, "<red>" + TITAN_NAMES[i] + " : encore " + cdLeft(st, "cd_" + id) + "s"); return; }
            boolean ok = switch (id) {
                case "colossal" -> colossal(p);
                case "feminin" -> feminin(p);
                case "cuirasse" -> { st.set("cuirasse", "speed".equals(st.get("cuirasse", "res")) ? "res" : "speed"); TitanAssaillant.applyCuirasse(p, st); yield false; }
                case "bestial" -> bestial(p);
                case "machoire" -> machoire(p);
                case "charrette" -> charrette(p);
                case "marteau" -> marteau(p);
                default -> false;
            };
            if (ok) startCd(p, st, "cd_" + id, TITAN_CD[i]);
        }

        static boolean colossal(Player p) {
            World w = p.getWorld();
            Location c = p.getLocation();
            w.spawnParticle(Particle.CLOUD, c.clone().add(0, 1, 0), 160, 2.5, 1.2, 2.5, 0.05);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 1, 0), 40, 2.5, 1.0, 2.5, 0.02);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            w.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
            for (LivingEntity enemy : Effects.enemiesWithin(p, 5)) { Effects.damage(enemy, 2.0, p); enemy.setFireTicks(100); }
            return true;
        }

        static boolean feminin(Player p) {
            SNK.invincible(p, 10000);
            Effects.give(p, PotionEffectType.SPEED, 0, 200);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
            Particle.DustOptions crystal = new Particle.DustOptions(Color.fromRGB(230, 240, 255), 1.3f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 5;
                    if (!p.isOnline() || t > 200) { cancel(); return; }
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1.2, 0), 6, 0.5, 0.9, 0.5, 0, crystal);
                }
            }.runTaskTimer(SNK.plugin(), 0L, 5L);
            Msg.actionBar(p, "<aqua>Titan Feminin : cristallise pendant 10 s");
            return true;
        }

        static boolean bestial(Player p) {
            Vector dir = p.getEyeLocation().getDirection();
            Arrow a = Effects.shootInstantArrow(p, dir, 1.7, 0.0);
            a.setInvisible(true);
            a.getPersistentDataContainer().set(ROCK, PersistentDataType.BYTE, (byte) 1);
            BlockDisplay rock = a.getWorld().spawn(a.getLocation(), BlockDisplay.class, d -> {
                d.setBlock(Material.COBBLESTONE.createBlockData());
                d.setTransformation(new Transformation(new Vector3f(-0.45f, -0.45f, -0.45f), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                d.setPersistent(false);
            });
            a.addPassenger(rock);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { rock.remove(); if (a.isValid()) a.remove(); }, 80L);
            return true;
        }

        static boolean machoire(Player p) {
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
            return true;
        }

        static boolean charrette(Player p) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Location to = p.getLocation().add(dir.normalize().multiply(10));
            SNK.rush(p, to, 4, Particle.CLOUD);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> Effects.give(p, PotionEffectType.SPEED, 1, 100), 5L);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            return true;
        }

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
            var left = p.getInventory().addItem(it);
            for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
            Msg.actionBar(p, "<gold>Marteau d'Armes : " + (r == 0 ? "epee" : r == 1 ? "arbalete" : "lance") + " forgee !");
            return true;
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
            transform(p, cf, st);
            return true;
        }

        static void transform(Player p, CfPlayer cf, AbilityState st) {
            st.set("titan", true);
            Bukkit.getScheduler().runTask(SNK.plugin(), () -> cf.setUltimateLocked(true));
            Boosts.set(p, "eren_titan", new Boosts.Boost(1, 1, 0, 0));
            var scale = p.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(2.0);
            var step = p.getAttribute(Attribute.STEP_HEIGHT);
            if (step != null) step.setBaseValue(1.1);
            if (owns(p, "cuirasse")) applyCuirasse(p, titansState(p));
            World w = p.getWorld();
            w.strikeLightningEffect(p.getLocation());
            w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.8f);
            w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
            w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1.5, 0), 120, 1.5, 1.5, 1.5, 0.1);
            w.spawnParticle(Particle.FLASH, p.getLocation().add(0, 1.5, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 240, 200));
            Msg.send(p, "<dark_red><bold>Tu es le Titan Assaillant.</bold> <gray>Plus de Pommes Dorees, d'equipement ni de Charge. Clic droit sur l'ultime : regeneration.");
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

        static void applyCuirasse(Player p, AbilityState titans) {
            if (titans == null || !isTitan(p)) return;
            boolean speed = "speed".equals(titans.get("cuirasse", "res"));
            Boosts.set(p, "eren_cuirasse", speed ? new Boosts.Boost(0, 1, 0, 0) : new Boosts.Boost(0, 0, 1, 0));
            Msg.actionBar(p, "<gray>Titan Cuirasse : <white>" + (speed ? "Speed" : "Resistance"));
        }

        static void revert(Player p, CfPlayer cf, AbilityState st) {
            st.set("titan", false);
            cf.setUltimateLocked(false);
            Boosts.remove(p, "eren_titan");
            Boosts.remove(p, "eren_cuirasse");
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

        /** A la mort du Titan : retour a la normale, et la chaleur du Colossal si Eren l'a vole. */
        @Override public boolean onDeath(Player p, CfPlayer cf, AbilityState st) {
            if (!st.get("titan", false)) return false;
            Location at = p.getLocation().clone();
            boolean colossal = owns(p, "colossal");
            revert(p, cf, st);
            if (colossal) heatZone(p, at);
            return false;
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
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity)) return;
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
            c.getWorld().spawnParticle(Particle.BLOCK, c, 40, 0.6, 0.6, 0.6, 0.1, Material.COBBLESTONE.createBlockData());
            c.getWorld().playSound(c, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
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
