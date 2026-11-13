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
}
