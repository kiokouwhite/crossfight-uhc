package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.MannequinPlayer;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mode test en troisieme personne : au milieu d'une plateforme a part, un mannequin incarne le personnage
 * choisi (/cf change). Le testeur recoit les objets de ce personnage, mais c'est le mannequin qui s'en sert,
 * et il vise le testeur : on voit les animations de l'exterieur et on encaisse les effets.
 *
 * Le mannequin est pilote par un {@link MannequinPlayer} : pour les capacites, c'est un joueur comme un autre,
 * dans une equipe a lui, dont le seul ennemi est son testeur.
 * <ul>
 *   <li>clic droit sur un objet de capacite : le mannequin la lance (accroupi : sa variante) ;</li>
 *   <li>clic gauche avec l'arme (meme sur le mannequin) : le mannequin frappe (a portee) ou tire a l'arc ;</li>
 *   <li>frapper le mannequin a mains nues ou avec un objet de capacite : ses capacites qui reagissent aux
 *       coups (contres...) se declenchent.</li>
 * </ul>
 */
public final class ThirdPerson implements Listener {

    public static final String WORLD = "crossfight_test_3p";
    /** Distance entre le mannequin (au centre) et le point d'arrivee du testeur. */
    private static final double SPOT_DISTANCE = 7.0;
    /** Portee de la frappe du mannequin (distance a la boite du testeur). */
    private static final double REACH = 4.0;
    /** Apres 10 secondes immobile loin de sa place, le mannequin y retourne. */
    private static final int IDLE_RETURN_TICKS = 200;

    private final CrossfightPlugin plugin;
    private final TestMode test;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    private static final class Session {
        final UUID tester;
        Mannequin body;
        Player pilot;
        Team team;
        Location home;
        int idle;
        boolean sneaking;
        long lastUse, lastAttack;
        int lastUseSlot = -1;

        Session(UUID tester) { this.tester = tester; }
    }

    ThirdPerson(CrossfightPlugin plugin, TestMode test) {
        this.plugin = plugin;
        this.test = test;
    }

    public void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (UUID u : new ArrayList<>(sessions.keySet())) close(u, false);
    }

    public static World world() {
        return TestMode.built(ArenaWorlds.get(WORLD));
    }

    /** La place du mannequin : le centre, tourne vers le sud (d'ou arrive le testeur). */
    static Location home() {
        Location c = ArenaWorlds.center(world());
        c.setYaw(0);
        c.setPitch(0);
        return c;
    }

    /** Le point d'arrivee du testeur, face a son mannequin. */
    public Location spotOf(Player tester) {
        Session s = sessions.get(tester.getUniqueId());
        return spot(s == null ? home() : s.home);
    }

    private static Location spot(Location home) {
        Location s = home.clone().add(0, 0, SPOT_DISTANCE);
        s.setYaw(180);
        s.setPitch(0);
        return s;
    }

    public boolean isActive(Player p) {
        return p != null && sessions.containsKey(p.getUniqueId());
    }

    /** Le joueur pilote du mannequin de ce testeur, ou null. */
    public Player pilotOf(Player tester) {
        Session s = sessions.get(tester.getUniqueId());
        return s == null ? null : s.pilot;
    }

    /** Celui que visent les commandes de test (/cf tome, /cf kills...) : le mannequin en troisieme personne, sinon le joueur. */
    public Player actor(Player sender) {
        Player pilot = pilotOf(sender);
        return pilot != null ? pilot : sender;
    }

    /** Le personnage du mannequin d'un testeur, ou null. */
    public Character characterOf(Player tester) {
        Player pilot = pilotOf(tester);
        return pilot == null ? null : plugin.players().get(pilot).character();
    }

    // ---------------------------------------------------------------- entree

    /**
     * Envoie le testeur en troisieme personne (en ouvrant le mode test si besoin). S'il incarnait un
     * personnage, c'est le mannequin qui le reprend.
     */
    public boolean enter(Player p, Character wanted) {
        if (!test.isTester(p)) {
            test.skipNextMenu(p);
            if (!test.enter(p)) return false;
        }
        if (test.isInDuoZone(p)) test.leaveDuoQuietly(p);
        Character mine = plugin.players().get(p).character();
        Character chosen = wanted != null ? wanted : mine;
        // Le testeur n'incarne plus personne : il sert de cible.
        test.clearCharacter(p);
        Session s = sessions.get(p.getUniqueId());
        if (s == null) s = open(p);
        if (s == null) return false;
        p.teleport(spot(s.home));
        p.setFallDistance(0);
        test.gear(p);
        Msg.send(p, "<aqua><bold>Troisieme personne.</bold> <gray>Le mannequin du centre incarne le personnage (<white>/cf change</white>) et lance ses capacites sur toi : "
                + "<white>clic droit</white> sur un objet = il l'utilise (accroupi : sa variante), <white>clic gauche</white> avec l'arme = il te frappe ou te tire dessus, "
                + "et tu peux le frapper (a mains nues ou avec un objet de capacite) pour voir ses contres. <white>/cf test third-person</white> pour reprendre le personnage toi-meme, <white>/cf test</white> pour quitter.");
        if (chosen != null) setCharacter(p, chosen);
        else Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isActive(p)) return;
            com.kiokouwhite.crossfight.util.Sounds.afterPack(p, () -> { if (isActive(p) && characterOf(p) == null) test.openMenu(p, c -> true, "<dark_aqua>Troisieme personne : quel personnage pour le mannequin ?"); });
        });
        test.save();
        return true;
    }

    /** Retour au terrain de test : le testeur reprend lui-meme le personnage du mannequin. */
    public void leave(Player p) {
        Character c = characterOf(p);
        close(p.getUniqueId(), true);
        p.teleport(ArenaWorlds.center(TestMode.world()));
        p.setFallDistance(0);
        if (c != null) test.change(p, c);
        Msg.send(p, "<gray>Retour au terrain de test" + (c == null ? "." : " : tu incarnes de nouveau " + c.name() + "."));
        test.save();
    }

    /** Fin du mode pour ce testeur (sortie du mode test, deconnexion) : le mannequin disparait. */
    public void close(Player p) {
        close(p.getUniqueId(), true);
    }

    private Session open(Player p) {
        World w = world();
        Location home = home();
        // Une seule place au centre : un second testeur a la sienne un peu plus loin.
        int others = sessions.size();
        if (others > 0) home.add(others % 2 == 1 ? -12 * ((others + 1) / 2) : 12 * (others / 2), 0, 0);
        Mannequin body = (Mannequin) w.spawnEntity(home, EntityType.MANNEQUIN);
        body.addScoreboardTag(Mannequins.TAG);
        body.addScoreboardTag(Mannequins.PILOT_TAG);
        body.setImmovable(false);
        body.setPersistent(false);
        body.setCollidable(false);
        body.setCustomNameVisible(true);
        body.setDescription(Msg.mm("<gray>Troisieme personne"));
        var mh = body.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(20.0);
        body.setHealth(20.0);

        Session s = new Session(p.getUniqueId());
        s.body = body;
        s.home = home;
        s.pilot = MannequinPlayer.create(body, p, "Mannequin");
        CfPlayer cf = plugin.players().get(s.pilot);
        cf.resetForNewGame(99);
        cf.setUltimateCharge(100);
        s.team = plugin.teams().create();
        plugin.teams().forceJoin(s.pilot, s.team);
        sessions.put(p.getUniqueId(), s);
        Mannequins.refreshName(body);
        start();
        return s;
    }

    private void close(UUID testerId, boolean cleanTester) {
        Session s = sessions.remove(testerId);
        if (s == null) return;
        Player pilot = s.pilot;
        CfPlayer cf = plugin.players().get(pilot);
        try {
            if (cf.character() != null) plugin.abilities().unequip(pilot);
        } catch (Exception ex) {
            plugin.getLogger().warning("Troisieme personne, fin du mannequin : " + ex);
        }
        Puppet pu = Puppet.of(pilot);
        if (pu != null) pu.remove();
        plugin.abilities().clearStates(pilot);
        com.kiokouwhite.crossfight.ability.Borrowed.clear(pilot);
        Boosts.clear(pilot);
        plugin.teams().leave(pilot);
        MannequinPlayer.release(pilot);
        plugin.players().remove(s.body.getUniqueId());
        if (s.body.isValid()) s.body.remove();
        Player tester = Bukkit.getPlayer(testerId);
        if (cleanTester && tester != null) {
            AbilityEngine.removeAbilityItems(tester.getInventory());
            tester.setExp(0);
            tester.setLevel(0);
        }
        if (sessions.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    // ------------------------------------------------------------ personnage

    /** /cf change en troisieme personne : c'est le mannequin qui change de personnage. */
    public void setCharacter(Player tester, Character c) {
        Session s = sessions.get(tester.getUniqueId());
        if (s == null) return;
        // Un menu de l'ancien personnage encore ouvert agirait sur le nouveau : on le ferme.
        if (tester.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) tester.closeInventory();
        Player pilot = s.pilot;
        CfPlayer cf = plugin.players().get(pilot);
        if (cf.character() != null) plugin.abilities().unequip(pilot);
        Puppet pu = Puppet.of(pilot);
        if (pu != null) pu.remove();
        plugin.abilities().clearStates(pilot);
        com.kiokouwhite.crossfight.ability.Borrowed.clear(pilot);
        Boosts.clear(pilot);
        Effects.unroot(pilot);
        pilot.setAllowFlight(false);
        for (PotionEffect e : s.body.getActivePotionEffects()) s.body.removePotionEffect(e.getType());
        Effects.resetMaxHealth(pilot);
        s.body.setHealth(Mannequins.maxHealthOf(s.body));
        s.body.setFireTicks(0);
        s.body.setInvisible(false);
        s.body.setGravity(true);
        cf.resetUltimate();
        cf.setUltimateLocked(false);
        cf.setCooldownFactor(1.0);
        cf.setUltimateCharge(100);
        MannequinPlayer.clearInventory(pilot);
        AbilityEngine.removeAbilityItems(tester.getInventory());
        test.resetTarget(tester);
        s.team.setLicence(c.licence());
        cf.setCharacter(c);
        MannequinPlayer.rename(pilot, c.name());
        com.kiokouwhite.crossfight.util.Skins.apply(pilot, c);
        s.body.teleport(s.home);
        s.body.setVelocity(new Vector());
        s.idle = 0;
        MannequinPlayer.setHeldSlot(pilot, tester.getInventory().getHeldItemSlot());
        plugin.abilities().equip(pilot);
        syncItems(s, tester);
        Mannequins.refreshName(s.body);
        test.gear(tester);
        Msg.send(tester, "<green>Le mannequin incarne <gold><bold>" + c.name() + "</bold></gold> <gray>(" + c.licence().name() + ")");
        com.kiokouwhite.crossfight.util.Sounds.selection(tester, c.id());
        test.save();
    }

    // ------------------------------------------------------------ commandes

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        ItemStack item = e.getItem();
        boolean ability = AbilityEngine.isAbilityItem(item), weapon = AbilityEngine.isWeaponItem(item);
        if (!ability && !weapon) return;
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        // Clic droit sur un coffre, une porte... : on laisse faire sauf accroupi (comme pour un joueur).
        if (right && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && AbilityEngine.isInteractable(e.getClickedBlock().getType()) && !p.isSneaking()) return;
        if (left && !weapon) return;
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        command(p, s, item, right);
    }

    /** Clic droit en visant le mannequin : selon le client, c'est ce paquet qui arrive au lieu (ou en plus) du clic dans le vide. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractBody(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !MannequinPlayer.isBody(e.getRightClicked())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (AbilityEngine.isAbilityItem(item) || AbilityEngine.isWeaponItem(item)) command(p, s, item, true);
    }

    /**
     * Le testeur frappe son mannequin. A mains nues ou avec un objet de capacite, le coup porte (et declenche
     * les contres). Avec l'arme du personnage, c'est un ordre : le mannequin frappe (ou tire) a sa place.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHitBody(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !MannequinPlayer.isBody(e.getEntity())) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.body != e.getEntity()) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!AbilityEngine.isWeaponItem(item)) return;
        e.setCancelled(true);
        command(p, s, item, false);
    }

    /** Le testeur donne un ordre au mannequin avec un de ses objets. */
    private void command(Player p, Session s, ItemStack item, boolean rightClick) {
        Player pilot = s.pilot;
        CfPlayer cf = plugin.players().get(pilot);
        Character ch = cf.character();
        if (ch == null) { Msg.actionBar(p, "<red>Le mannequin n'incarne personne : /cf change"); return; }
        int slot = p.getInventory().getHeldItemSlot();
        long now = System.currentTimeMillis();
        // Le meme clic peut arriver deux fois (vise sur le mannequin) : on ne le compte qu'une fois.
        if (rightClick && slot == s.lastUseSlot && now - s.lastUse < 150) return;
        s.lastUse = now;
        s.lastUseSlot = slot;
        MannequinPlayer.setHeldSlot(pilot, slot);
        face(s, p);
        String id = Items.tag(item, AbilityEngine.ABILITY_KEY);
        try {
            if (rightClick && id != null) {
                Ability a = ch.ability(pilot, id);
                if (a == null) return;
                if (a.vanillaUse()) shootBow(s, item);
                else plugin.abilities().use(pilot, cf, a);
            } else if (AbilityEngine.isWeaponItem(item)) {
                if (isBow(item)) shootBow(s, item);
                else if (!rightClick) strike(s, p);
                else Msg.actionBar(p, "<gray>Clic gauche avec l'arme : le mannequin te frappe.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Troisieme personne (" + ch.id() + ", " + (id == null ? "arme" : id) + ") : " + ex);
            ex.printStackTrace();
            Msg.actionBar(p, "<red>Cette capacite a plante sur le mannequin (voir la console).");
        }
    }

    private static boolean isBow(ItemStack it) {
        return it != null && (it.getType() == Material.BOW || it.getType() == Material.CROSSBOW);
    }

    /** Le mannequin frappe le testeur avec son arme, s'il est a portee. */
    private void strike(Session s, Player tester) {
        long now = System.currentTimeMillis();
        if (now - s.lastAttack < 400) return;
        if (!tester.getWorld().equals(s.body.getWorld()) || Effects.distanceToBox(s.body.getEyeLocation(), tester) > REACH) {
            Msg.actionBar(tester, "<gray>Le mannequin ne t'atteint pas : approche-toi a moins de " + (int) REACH + " blocs.");
            return;
        }
        s.lastAttack = now;
        s.body.swingMainHand();
        var atk = s.body.getAttribute(Attribute.ATTACK_DAMAGE);
        double dmg = atk == null ? 1.0 : atk.getValue();
        Effects.damage(tester, dmg, s.pilot);
    }

    /**
     * Le mannequin tire a l'arc, bande a fond, vers le testeur. Les capacites qui ecoutent les tirs
     * (fleches enchantees, arcs electriques...) le voient comme un tir du joueur pilote.
     */
    private void shootBow(Session s, ItemStack bow) {
        Mannequin body = s.body;
        Vector dir = body.getEyeLocation().getDirection();
        Arrow arrow = body.launchProjectile(Arrow.class, dir.multiply(3.0));
        MannequinPlayer.setShooter(arrow, s.pilot);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        int power = bow.getEnchantmentLevel(Enchantment.POWER);
        if (power > 0) arrow.setDamage(arrow.getDamage() + power * 0.5 + 0.5);
        if (bow.getEnchantmentLevel(Enchantment.FLAME) > 0) arrow.setFireTicks(100);
        EntityShootBowEvent ev = new EntityShootBowEvent(s.pilot, bow, new ItemStack(Material.ARROW), arrow, EquipmentSlot.HAND, 1.0f, false);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.getProjectile() != arrow) {
            arrow.remove();
            if (ev.isCancelled()) return;
        }
        body.getWorld().playSound(body.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
    }

    // -------------------------------------------------------------- chaque tick

    private void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            Player tester = Bukkit.getPlayer(s.tester);
            if (tester == null) { close(s.tester, false); continue; }
            if (!s.body.isValid()) {
                // Le mannequin a disparu (commande /kill...) : on le refait, avec le meme personnage.
                Character c = characterOf(tester);
                close(s.tester, true);
                if (enter(tester, c)) Msg.actionBar(tester, "<gray>Le mannequin a ete remis en place.");
                continue;
            }
            Player pilot = s.pilot;
            face(s, tester);
            MannequinPlayer.setHeldSlot(pilot, tester.getInventory().getHeldItemSlot());
            if (tester.isSneaking() != s.sneaking) {
                s.sneaking = tester.isSneaking();
                if (s.body.getPose() == Pose.STANDING || s.body.getPose() == Pose.SNEAKING) s.body.setPose(s.sneaking ? Pose.SNEAKING : Pose.STANDING, true);
            }
            syncItems(s, tester);
            CfPlayer cf = plugin.players().get(pilot);
            if (!cf.isUltimateLocked()) cf.setUltimateCharge(100);
            returnHome(s);
        }
    }

    /** Le mannequin regarde le testeur (sa poitrine) : les capacites qui visent devant elles le visent. */
    private void face(Session s, Player tester) {
        if (!tester.getWorld().equals(s.body.getWorld())) return;
        Location eye = s.body.getEyeLocation();
        Vector d = tester.getLocation().add(0, 1.1, 0).toVector().subtract(eye.toVector());
        if (d.lengthSquared() < 0.01) return;
        Location look = eye.clone();
        look.setDirection(d);
        s.body.setRotation(look.getYaw(), look.getPitch());
    }

    /** Hors de la plateforme, ou immobile loin de sa place pendant 10 secondes : retour au centre. */
    private void returnHome(Session s) {
        Location at = s.body.getLocation();
        // Emmene ailleurs par une capacite (dimension de Byleth...) : c'est elle qui le ramene.
        if (!at.getWorld().equals(s.home.getWorld())) { s.idle = 0; return; }
        boolean out = Math.abs(at.getX()) > 43 || Math.abs(at.getZ()) > 43 || at.getY() < ArenaWorlds.FLOOR_Y - 3;
        boolean away = at.distanceSquared(s.home) > 4.0;
        boolean still = s.body.isOnGround() && s.body.getVelocity().lengthSquared() < 0.01 && Puppet.of(s.pilot) == null;
        s.idle = away && still ? s.idle + 1 : 0;
        if (out || s.idle >= IDLE_RETURN_TICKS) {
            s.idle = 0;
            s.body.getWorld().spawnParticle(Particle.PORTAL, s.body.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
            s.body.teleport(s.home);
            s.body.setVelocity(new Vector());
            s.body.setFallDistance(0);
        }
    }

    /**
     * Les objets du testeur suivent ceux du mannequin, case par case : ce que la capacite range, retire ou
     * modifie (fleches, arc lance au sol, objets qui changent d'apparence) se voit aussi chez le testeur.
     * Ses pommes dorees de test restent la ou il y a de la place.
     */
    private void syncItems(Session s, Player tester) {
        ItemStack[] want = MannequinPlayer.contents(s.pilot);
        PlayerInventory inv = tester.getInventory();
        java.util.List<ItemStack> displaced = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack have = inv.getItem(i);
            ItemStack w = want[i];
            if (TestMode.isGear(have)) {
                if (w == null) continue;
                displaced.add(have);
                have = null;
                inv.setItem(i, null);
            }
            if (w == null) {
                if (have != null) inv.setItem(i, null);
                continue;
            }
            if (have == null || !have.isSimilar(w) || have.getAmount() != w.getAmount()) inv.setItem(i, w.clone());
        }
        for (ItemStack g : displaced) {
            for (int i = 35; i >= 9; i--) {
                if (want[i] == null && inv.getItem(i) == null) { inv.setItem(i, g); break; }
            }
        }
    }

    // ------------------------------------------------------------- terrain

    /** Apres un nettoyage du terrain : les mannequins pilotes retournent a leur place. */
    void afterClear() {
        for (Session s : sessions.values()) {
            if (!s.body.isValid()) continue;
            s.body.teleport(s.home);
            s.body.setVelocity(new Vector());
            s.body.setHealth(Mannequins.maxHealthOf(s.body));
            Player tester = Bukkit.getPlayer(s.tester);
            if (tester != null) tester.teleport(spot(s.home));
        }
    }

    /** Testeurs partis en troisieme personne : uuid -> personnage du mannequin ("" : aucun). Ils la retrouvent en revenant. */
    private final Map<UUID, String> rejoin = new HashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (sessions.containsKey(p.getUniqueId())) {
            Character c = characterOf(p);
            rejoin.put(p.getUniqueId(), c == null ? "" : c.id());
        }
        // Le mannequin part avec lui ; ses objets (copies de ceux du mannequin) aussi.
        close(p.getUniqueId(), true);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = rejoin.remove(p.getUniqueId());
        if (id == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !test.isTester(p) || isActive(p)) return;
            enter(p, id.isEmpty() ? null : plugin.characters().character(id));
        }, 20L);
    }

    /** Pour la sauvegarde du mode test : "3p:<personnage>" pour un testeur en troisieme personne (meme deconnecte), sinon null. */
    String savedState(UUID tester) {
        Player p = Bukkit.getPlayer(tester);
        if (p != null && isActive(p)) {
            Character c = characterOf(p);
            return "3p:" + (c == null ? "" : c.id());
        }
        String id = rejoin.get(tester);
        return id == null ? null : "3p:" + id;
    }
}
