package com.kiokouwhite.crossfight.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * Etat d'une capacite pour un joueur donne : temps de chargement, barre, mode,
 * et un petit sac de donnees libre pour les capacites qui en ont besoin.
 */
public final class AbilityState {

    private long cooldownEndMillis;
    private boolean enabled;
    private double gauge = 100.0;
    private int mode;
    /** Nombre de temps de chargement lances : permet au moteur de savoir qu'une capacite a reussi. */
    private int activations;
    private final Map<String, Object> data = new HashMap<>();

    public boolean onCooldown() { return System.currentTimeMillis() < cooldownEndMillis; }

    public int cooldownLeftSeconds() {
        long left = cooldownEndMillis - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public int activations() { return activations; }

    public void startCooldown(int seconds) {
        activations++;
        // En mode test, aucune capacite n'a de temps de chargement.
        var plugin = com.kiokouwhite.crossfight.CrossfightPlugin.get();
        if (plugin != null && plugin.game() != null && plugin.game().isTest()) { cooldownEndMillis = 0; return; }
        cooldownEndMillis = System.currentTimeMillis() + seconds * 1000L;
    }
    public void clearCooldown() { cooldownEndMillis = 0; }

    /** Reduit le temps de chargement restant d'un pourcentage (utile pour Tutorat de Byleth). */
    public void reduceCooldown(double fraction) {
        long left = cooldownEndMillis - System.currentTimeMillis();
        if (left > 0) cooldownEndMillis = System.currentTimeMillis() + (long) (left * (1.0 - fraction));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double gauge() { return gauge; }
    public void setGauge(double gauge) { this.gauge = Math.max(0.0, Math.min(100.0, gauge)); }

    public int mode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T def) {
        Object v = data.get(key);
        return v == null ? def : (T) v;
    }

    public void set(String key, Object value) { data.put(key, value); }
    public void remove(String key) { data.remove(key); }
}
