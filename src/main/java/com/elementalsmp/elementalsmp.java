// ADVANCED Elemental SMP Plugin (FULL FEATURED + COOLDOWNS + FULL CONFIG)
package com.elementalsmp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class ElementalSMP extends JavaPlugin implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> critCount = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    // ================= JOIN =================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FileConfiguration config = getConfig();

        if (!config.contains("players." + p.getUniqueId())) {
            List<String> elements = config.getStringList("settings.available-elements");
            String chosen = elements.get(new Random().nextInt(elements.size()));

            config.set("players." + p.getUniqueId(), chosen);
            saveConfig();
        }

        applyPassive(p);
    }

    // ================= PASSIVE =================
    public void applyPassive(Player p) {
        String element = getConfig().getString("players." + p.getUniqueId());
        if (element == null) return;

        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));

        switch (element) {
            case "earth" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, getConfig().getInt("earth.passive.resistance")));
                Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(getConfig().getDouble("earth.passive.health"));
            }
            case "lightning" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 999999, getConfig().getInt("lightning.passive.haste")));
            }
            case "star" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, getConfig().getInt("star.passive.speed")));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, getConfig().getInt("star.passive.strength")));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 999999, getConfig().getInt("star.passive.regen")));
            }
            case "fire" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0));
            }
            case "water" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 999999, getConfig().getInt("water.passive.dolphin")));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 999999, 0));
            }
        }
    }

    // ================= ABILITIES =================
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;

        Player p = e.getPlayer();
        String element = getConfig().getString("players." + p.getUniqueId());
        if (element == null) return;

        long now = System.currentTimeMillis();
        long cd = getConfig().getLong(element + ".cooldown") * 1000;

        if (cooldowns.containsKey(p.getUniqueId()) && now - cooldowns.get(p.getUniqueId()) < cd) {
            p.sendMessage("§cAbility on cooldown!");
            return;
        }

        cooldowns.put(p.getUniqueId(), now);

        switch (element) {

            case "wind" -> {
                double power = getConfig().getDouble("wind.dash_power");
                Vector dash = p.getLocation().getDirection().multiply(power);
                p.setVelocity(dash);
            }

            case "lightning" -> {
                Player target = getNearestPlayer(p, getConfig().getDouble("lightning.radius"));
                if (target != null) {
                    target.getWorld().strikeLightning(target.getLocation());
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, getConfig().getInt("lightning.invis_duration"), 0));
                }
            }

            case "star" -> {
                Player target = getNearestPlayer(p, getConfig().getDouble("star.radius"));
                if (target != null) {
                    Location loc = target.getLocation().add(0, getConfig().getDouble("star.height"), 0);
                    target.getWorld().createExplosion(loc, (float) getConfig().getDouble("star.damage"));
                }
            }

            case "water" -> {
                double force = getConfig().getDouble("water.push_force");
                for (Entity ent : p.getNearbyEntities(5,5,5)) {
                    if (ent instanceof Player t && !t.equals(p)) {
                        Vector push = t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(force);
                        t.setVelocity(push);
                    }
                }
            }

            case "fire" -> {
                int burn = getConfig().getInt("fire.burn_ticks");
                for (Entity ent : p.getNearbyEntities(5,5,5)) {
                    if (ent instanceof Player t && !t.equals(p)) {
                        t.setFireTicks(burn);
                    }
                }
            }

            case "earth" -> {
                double height = p.getFallDistance();
                double multiplier = getConfig().getDouble("earth.multiplier");
                double cap = getConfig().getDouble("earth.max_damage");
                double damage = Math.min(height * multiplier, cap);

                for (Entity ent : p.getNearbyEntities(getConfig().getDouble("earth.radius"),4,getConfig().getDouble("earth.radius"))) {
                    if (ent instanceof Player t && !t.equals(p)) {
                        t.damage(damage);
                        t.setVelocity(new Vector(0,getConfig().getDouble("earth.knockup"),0));
                    }
                }
            }
        }
    }

    // ================= LIGHTNING CRIT =================
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;

        String element = getConfig().getString("players." + p.getUniqueId());
        if (!"lightning".equals(element)) return;

        if (p.getFallDistance() > 0) {
            int count = critCount.getOrDefault(p.getUniqueId(), 0) + 1;
            critCount.put(p.getUniqueId(), count);

            if (count >= getConfig().getInt("lightning.crit_required")) {
                e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
                critCount.put(p.getUniqueId(), 0);
            }
        }
    }

    // ================= COMMAND =================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("element")) {
            if (args.length != 2) return false;

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) return false;

            getConfig().set("players." + target.getUniqueId(), args[1].toLowerCase());
            saveConfig();
            applyPassive(target);

            sender.sendMessage("Element updated!");
            return true;
        }
        return false;
    }

    // ================= UTILS =================
    public Player getNearestPlayer(Player p, double radius) {
        Player nearest = null;
        double dist = Double.MAX_VALUE;

        for (Entity ent : p.getNearbyEntities(radius,radius,radius)) {
            if (ent instanceof Player t && !t.equals(p)) {
                double d = t.getLocation().distance(p.getLocation());
                if (d < dist) {
                    dist = d;
                    nearest = t;
                }
            }
        }
        return nearest;
    }
}

// plugin.yml
name: ElementalSMP
version: 6.0
main: com.elementalsmp.ElementalSMP
api-version: 1.21
commands:
  element:
    description: Change player element

// config.yml
settings:
  available-elements: [fire, water, earth, wind, lightning, star]

wind:
  dash_power: 2
  cooldown: 5

earth:
  radius: 4
  multiplier: 2
  max_damage: 12
  knockup: 1
  cooldown: 6
  passive:
    resistance: 0
    health: 24

fire:
  burn_ticks: 120
  cooldown: 5

water:
  push_force: 1.5
  cooldown: 5
  passive:
    dolphin: 0

lightning:
  radius: 10
  invis_duration: 200
  crit_required: 7
  cooldown: 8
  passive:
    haste: 0

star:
  radius: 10
  height: 15
  damage: 4
  cooldown: 10
  passive:
    speed: 0
    strength: 1
    regen: 0
