package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Montre a un joueur, a lui seul, un autre objet dans une case de son inventaire (sans toucher a son vrai
 * inventaire) : cases d'armure masquees pendant une animation, objet invisible porte sur la tete pour
 * dessiner une image sur son ecran... L'API de Paper n'a pas d'equivalent : on passe par les classes du serveur.
 * Cases de l'inventaire du joueur : 5 casque, 6 plastron, 7 jambieres, 8 bottes, 36-44 barre, 45 main gauche.
 */
public final class ClientSlots {

    private ClientSlots() { }

    private static boolean ready, broken;
    private static Method getHandle, send, asNms, getStateId;
    private static Constructor<?> packet;
    private static Object empty;

    private static void init(Player p) throws ReflectiveOperationException {
        if (ready) return;
        getHandle = p.getClass().getMethod("getHandle");
        Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
        empty = stack.getField("EMPTY").get(null);
        asNms = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
        packet = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket").getConstructor(int.class, int.class, int.class, stack);
        ready = true;
    }

    /** Envoie ces objets (null : case vide) dans ces cases de l'inventaire du joueur, pour lui seul. Faux si impossible. */
    public static boolean show(Player p, int[] slots, ItemStack[] items) {
        if (broken) return false;
        try {
            init(p);
            Object handle = getHandle.invoke(p);
            Object menu = handle.getClass().getField("inventoryMenu").get(handle);
            if (getStateId == null) getStateId = menu.getClass().getMethod("getStateId");
            int state = (int) getStateId.invoke(menu);
            Object connection = handle.getClass().getField("connection").get(handle);
            if (send == null) send = connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            for (int i = 0; i < slots.length; i++) {
                Object nms = items[i] == null || items[i].getType().isAir() ? empty : asNms.invoke(null, items[i]);
                send.invoke(connection, packet.newInstance(0, state, slots[i], nms));
            }
            return true;
        } catch (Throwable t) {
            broken = true;
            CrossfightPlugin.get().getLogger().warning("Cases d'inventaire du joueur non modifiables pour lui seul : " + t);
            return false;
        }
    }

    public static boolean show(Player p, int slot, ItemStack item) {
        return show(p, new int[]{slot}, new ItemStack[]{item});
    }

    // ------------------------------------------------------------ cases tenues

    /*
     * Les images du HUD sont des objets montres au joueur seul dans ses cases d'armure (et sa main gauche), renvoyes a
     * chaque tick. Mais le serveur renvoie aussi, de lui-meme, ces cases avec la vraie armure : quand un clic droit est
     * refuse (Paper resynchronise la main et l'armure), quand l'inventaire entier est renvoye (fin d'une animation du
     * pantin), etc. Le client montrait alors la vraie piece jusqu'au tick suivant : le HUD clignotait. On tient donc ces
     * cases : un relais sur la connexion du joueur remplace, dans tout paquet de son inventaire qui partirait vers lui,
     * le contenu d'une case tenue par l'objet du HUD.
     */

    /** Les cases tenues : 5 a 8 (armure) et 45 (main gauche). */
    private static final int[] HELD = {5, 6, 7, 8, 45};
    /** Par joueur, l'objet (du serveur) tenu dans chaque case de HELD, ou null. Lu depuis le fil de la connexion. */
    private static final java.util.Map<java.util.UUID, Object[]> PINNED = new java.util.concurrent.ConcurrentHashMap<>();
    /** Les objets deja convertis (les images du HUD sont toujours les memes instances). */
    private static final java.util.Map<ItemStack, Object> NMS = new java.util.IdentityHashMap<>();
    private static final String RELAY = "crossfight_cases";

    /**
     * Tient ces cases chez ce joueur (null : la case n'est plus tenue, le vrai contenu repasse). Les cases absentes
     * de {@code slots} ne changent pas.
     */
    public static void pin(Player p, int[] slots, ItemStack[] items) {
        if (broken || !relayed) return;
        try {
            init(p);
            Object[] old = PINNED.get(p.getUniqueId());
            Object[] now = old == null ? new Object[HELD.length] : old.clone();
            for (int i = 0; i < slots.length; i++) {
                int k = java.util.Arrays.binarySearch(HELD, slots[i]);
                if (k < 0) continue;
                ItemStack it = items[i];
                now[k] = it == null ? null : NMS.computeIfAbsent(it, s -> {
                    try { return asNms.invoke(null, s); } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                });
            }
            if (old == null || !java.util.Arrays.equals(old, now)) {
                boolean any = false;
                for (Object o : now) any |= o != null;
                if (any) PINNED.put(p.getUniqueId(), now); else PINNED.remove(p.getUniqueId());
            }
            if (NMS.size() > 512) NMS.clear();
        } catch (Throwable t) {
            PINNED.remove(p.getUniqueId());
        }
    }

    /** Plus aucune case tenue chez ce joueur. */
    public static void unpinAll(Player p) {
        PINNED.remove(p.getUniqueId());
    }

    private static boolean relayed = true;
    private static Class<?> setSlot, setContent, ctxType;
    private static Method slotOf, containerOf, stateOf, itemOf, contentContainer, contentState, contentItems, contentCarried;
    private static Constructor<?> contentPacket;
    private static final java.util.Map<String, Method> FORWARD = new java.util.concurrent.ConcurrentHashMap<>();

    /** Pose le relais sur la connexion de ce joueur (a son arrivee). Sans effet si le serveur ne s'y prete pas. */
    public static void relay(Player p) {
        if (!relayed) return;
        try {
            init(p);
            Object handle = getHandle.invoke(p);
            Object listener = handle.getClass().getField("connection").get(handle);
            Object connection = listener.getClass().getField("connection").get(listener);
            Object channel = connection.getClass().getField("channel").get(connection);
            Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
            Class<?> pipeType = Class.forName("io.netty.channel.ChannelPipeline");
            Class<?> handlerType = Class.forName("io.netty.channel.ChannelHandler");
            if (pipeType.getMethod("get", String.class).invoke(pipeline, RELAY) != null) pipeType.getMethod("remove", String.class).invoke(pipeline, RELAY);
            if (pipeType.getMethod("get", String.class).invoke(pipeline, "packet_handler") == null) return;
            if (setSlot == null) {
                setSlot = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");
                containerOf = setSlot.getMethod("getContainerId");
                stateOf = setSlot.getMethod("getStateId");
                slotOf = setSlot.getMethod("getSlot");
                itemOf = setSlot.getMethod("getItem");
                setContent = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket");
                contentContainer = setContent.getMethod("containerId");
                contentState = setContent.getMethod("stateId");
                contentItems = setContent.getMethod("items");
                contentCarried = setContent.getMethod("carriedItem");
                Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
                contentPacket = setContent.getConstructor(int.class, int.class, java.util.List.class, stack);
                ctxType = Class.forName("io.netty.channel.ChannelHandlerContext");
            }
            Class<?> outbound = Class.forName("io.netty.channel.ChannelOutboundHandler");
            java.util.UUID id = p.getUniqueId();
            Object relay = java.lang.reflect.Proxy.newProxyInstance(outbound.getClassLoader(), new Class<?>[]{outbound}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return RELAY;
                    case "handlerAdded", "handlerRemoved": return null;
                    case "exceptionCaught": return ctxType.getMethod("fireExceptionCaught", Throwable.class).invoke(args[0], args[1]);
                    case "write": args[1] = rewrite(id, args[1]); break;
                    default: break;
                }
                // Le reste passe tel quel au maillon suivant (ctx.write, ctx.flush, ctx.close...).
                Class<?>[] types = m.getParameterTypes();
                Method next = FORWARD.computeIfAbsent(m.getName() + types.length, k -> {
                    try { return ctxType.getMethod(m.getName(), java.util.Arrays.copyOfRange(types, 1, types.length)); }
                    catch (NoSuchMethodException ex) { throw new IllegalStateException(ex); }
                });
                return next.invoke(args[0], java.util.Arrays.copyOfRange(args, 1, args.length));
            });
            pipeType.getMethod("addBefore", String.class, String.class, handlerType).invoke(pipeline, "packet_handler", RELAY, relay);
        } catch (Throwable t) {
            relayed = false;
            CrossfightPlugin.get().getLogger().warning("Cases du HUD non tenues (le HUD peut clignoter) : " + t);
        }
    }

    /** Retire le relais de chaque joueur (arret du plugin). */
    public static void unrelayAll() {
        PINNED.clear();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                Object handle = getHandle == null ? null : getHandle.invoke(p);
                if (handle == null) return;
                Object listener = handle.getClass().getField("connection").get(handle);
                Object connection = listener.getClass().getField("connection").get(listener);
                Object channel = connection.getClass().getField("channel").get(connection);
                Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
                Class<?> pipeType = Class.forName("io.netty.channel.ChannelPipeline");
                if (pipeType.getMethod("get", String.class).invoke(pipeline, RELAY) != null) pipeType.getMethod("remove", String.class).invoke(pipeline, RELAY);
            } catch (Throwable ignored) { }
        }
    }

    /** Le paquet tel qu'il doit partir : ses cases tenues remplacees par l'objet du HUD. */
    private static Object rewrite(java.util.UUID id, Object msg) {
        Object[] pins = PINNED.get(id);
        if (pins == null || msg == null) return msg;
        try {
            if (msg.getClass() == setSlot) {
                if ((int) containerOf.invoke(msg) != 0) return msg;
                int k = java.util.Arrays.binarySearch(HELD, (int) slotOf.invoke(msg));
                if (k < 0 || pins[k] == null || itemOf.invoke(msg) == pins[k]) return msg;
                return packet.newInstance(0, (int) stateOf.invoke(msg), HELD[k], pins[k]);
            }
            if (msg.getClass() == setContent) {
                if ((int) contentContainer.invoke(msg) != 0) return msg;
                @SuppressWarnings("unchecked") java.util.List<Object> items = new java.util.ArrayList<>((java.util.List<Object>) contentItems.invoke(msg));
                boolean changed = false;
                for (int k = 0; k < HELD.length; k++) {
                    if (pins[k] != null && HELD[k] < items.size()) { items.set(HELD[k], pins[k]); changed = true; }
                }
                return changed ? contentPacket.newInstance(0, (int) contentState.invoke(msg), items, contentCarried.invoke(msg)) : msg;
            }
        } catch (Throwable ignored) { }
        return msg;
    }
}
