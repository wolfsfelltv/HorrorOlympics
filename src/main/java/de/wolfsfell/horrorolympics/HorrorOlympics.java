package de.wolfsfell.horrorolympics;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class HorrorOlympics extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();
    private UUID hunterId;
    private String activeGame = "";
    private int elapsed = 0;
    private boolean teacherWatching = false;
    private final Map<UUID, Integer> roundScore = new HashMap<>();
    private final Map<UUID, Set<String>> collected = new HashMap<>();
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final Set<UUID> finished = new HashSet<>();
    private final Set<UUID> scored = new HashSet<>();
    private final List<String> bingoItems = List.of("skull", "bone", "candle", "beer", "eye");
    private static final int GAME_LENGTH = 480;
    private boolean running = false;
    private BukkitTask roundTask;
    private int secondsLeft;
    private static final int MAX_PLAYERS = 25;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[HorrorOlympics] " + ChatColor.GRAY;
    private static final List<String> ARENAS = List.of("apored", "hotel", "supermarkt", "schule", "friedhof");

    @Override public void onEnable() {
        saveDefaultConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("points");
        if (section != null) for (String key : section.getKeys(false)) {
            try { points.put(UUID.fromString(key), section.getInt(key)); } catch (IllegalArgumentException ignored) { }
        }
        Objects.requireNonNull(getCommand("ho")).setExecutor(this);
        Objects.requireNonNull(getCommand("ho")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("HorrorOlympics Alpha 1 enabled. Use /ho help");
    }
    @Override public void onDisable() { stopRound(false); savePoints(); }

    private void savePoints() {
        getConfig().set("points", null);
        points.forEach((id, value) -> getConfig().set("points." + id, value));
        saveConfig();
    }
    private void tell(CommandSender s, String message) { s.sendMessage(PREFIX + message); }
    private boolean admin(CommandSender s) { if (s.hasPermission("horrorolympics.admin")) return true; tell(s, ChatColor.RED + "Nur Event-Admins dürfen das."); return false; }
    private Location readLocation(String path) { return getConfig().getLocation(path); }
    private void setLocation(String path, Player p) { getConfig().set(path, p.getLocation()); saveConfig(); }
    private Player find(String name) { return Bukkit.getPlayerExact(name); }
    private boolean isArena(String name) { return ARENAS.contains(name.toLowerCase(Locale.ROOT)); }
    private void broadcast(String message) { Bukkit.broadcastMessage(PREFIX + message); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            tell(sender, "/ho join | leave | lobby | points [Spieler]");
            if (sender.hasPermission("horrorolympics.admin")) {
                tell(sender, "/ho setlobby | arena <name> <pos1|pos2|spawn|hunter|spectator|show>");
                tell(sender, "/ho hunter <Spieler> | start <apored|hotel|supermarkt|schule|friedhof> | stop | status");
                tell(sender, "/ho marker <hotel closet|supermarkt crate|supermarkt checkout|schule finish|friedhof skull|bone|candle|beer|eye|delivery> [add]");
                tell(sender, "/ho revive <Spieler>");
                tell(sender, "/ho points <set|add|remove> <Spieler> <Zahl>");
            }
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Nur ingame."); return true; }
                if (running) { tell(sender, "Eine Runde läuft bereits."); return true; }
                if (participants.contains(p.getUniqueId())) { tell(sender, "Du bist schon dabei."); return true; }
                if (participants.size() >= MAX_PLAYERS) { tell(sender, "Das Event ist voll (25/25)."); return true; }
                participants.add(p.getUniqueId());
                tell(sender, "Du bist dabei! (" + participants.size() + "/25)");
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) return true;
                if (running) { tell(sender, "Während der Runde bitte Admin um /ho stop."); return true; }
                participants.remove(p.getUniqueId()); tell(sender, "Du hast das Event verlassen.");
            }
            case "lobby" -> {
                if (!(sender instanceof Player p)) return true;
                Location loc = readLocation("lobby");
                if (loc == null) { tell(sender, "Lobby ist noch nicht markiert."); return true; }
                p.teleport(loc); tell(sender, "Zur Lobby teleportiert.");
            }
            case "setlobby" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) return true;
                setLocation("lobby", p); tell(sender, "Lobby gespeichert.");
            }
            case "arena" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) return true;
                if (a.length < 3 || !isArena(a[1])) { tell(sender, "Nutze /ho arena <apored|hotel|supermarkt|schule|friedhof> <pos1|pos2|spawn|hunter|spectator|show>"); return true; }
                String base = "arenas." + a[1].toLowerCase(Locale.ROOT) + ".";
                String field = a[2].toLowerCase(Locale.ROOT);
                if (List.of("pos1", "pos2", "spawn", "hunter", "spectator").contains(field)) {
                    if (running) { tell(sender, "Markierungen nicht während einer Runde ändern."); return true; }
                    setLocation(base + field, p); tell(sender, a[1] + ": " + field + " gespeichert.");
                } else if (field.equals("show")) {
                    Location p1 = readLocation(base + "pos1"), p2 = readLocation(base + "pos2");
                    if (p1 == null || p2 == null) { tell(sender, "Zuerst pos1 und pos2 setzen."); return true; }
                    if (!Objects.equals(p1.getWorld(), p2.getWorld())) { tell(sender, "Die Positionen müssen in derselben Welt sein."); return true; }
                    tell(sender, "Grenzen: " + coords(p1) + " bis " + coords(p2) + " (" + p1.getWorld().getName() + ")");
                    drawCorners(p, p1, p2);
                } else tell(sender, "Unbekannte Arena-Aktion.");
            }
            case "hunter" -> {
                if (!admin(sender)) return true;
                if (running) { tell(sender, "Jäger nicht während einer Runde wechseln."); return true; }
                if (a.length < 2 || find(a[1]) == null) { tell(sender, "Nutze /ho hunter <Online-Spieler>"); return true; }
                Player target = find(a[1]); hunterId = target.getUniqueId();
                tell(sender, target.getName() + " ist jetzt ApoRed.");
            }
            case "start" -> {
                if (!admin(sender)) return true;
                if (a.length < 2 || !isArena(a[1])) { tell(sender, "Nutze /ho start <apored|hotel|supermarkt|schule|friedhof>."); return true; }
                if (a[1].equalsIgnoreCase("apored")) startApoRed(sender);
                else startGame(sender, a[1].toLowerCase(Locale.ROOT));
            }
            case "revive" -> {
                if (!admin(sender)) return true;
                if (!running || a.length < 2 || find(a[1]) == null) { tell(sender, "Nutze während einer Runde /ho revive <Online-Spieler>."); return true; }
                Player p = find(a[1]); UUID id = p.getUniqueId();
                if (!eliminated.remove(id)) { tell(sender, "Spieler ist nicht ausgeschieden."); return true; }
                Location spawn = readLocation("arenas." + activeGame + ".spawn");
                if (spawn != null) p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE); tell(sender, p.getName() + " ist wieder dabei.");
            }
            case "marker" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) return true;
                if (running) { tell(sender, "Nicht während einer Runde markieren."); return true; }
                if (a.length < 3 || !isArena(a[1])) { tell(sender, "/ho marker <arena> <typ> [add]"); return true; }
                String arena = a[1].toLowerCase(Locale.ROOT), type = a[2].toLowerCase(Locale.ROOT);
                if (!validMarker(arena, type)) { tell(sender, "Ungültiger Marker für diese Arena."); return true; }
                String path = "markers." + arena + "." + type;
                if (a.length > 3 && a[3].equalsIgnoreCase("add")) {
                    List<Location> list = new ArrayList<>(getConfig().getLocationList(path));
                    list.add(p.getLocation()); getConfig().set(path, list); saveConfig();
                    tell(sender, arena + "/" + type + ": Punkt " + list.size() + " gespeichert.");
                } else { setLocation(path, p); tell(sender, arena + "/" + type + " gespeichert."); }
            }
            case "stop" -> { if (admin(sender)) { stopRound(true); tell(sender, "Runde beendet und Spieler zurückgesetzt."); } }
            case "status" -> {
                if (!admin(sender)) return true;
                tell(sender, "Teilnehmer: " + participants.size() + "/25 | ApoRed läuft: " + running + " | Zeit: " + secondsLeft + "s");
            }
            case "points" -> {
                if (a.length >= 2 && List.of("set", "add", "remove").contains(a[1].toLowerCase(Locale.ROOT))) {
                    if (!admin(sender)) return true;
                    if (a.length < 4) { tell(sender, "/ho points <set|add|remove> <Online-Spieler> <Zahl>"); return true; }
                    Player target = find(a[2]); if (target == null) { tell(sender, "Spieler muss online sein."); return true; }
                    int n; try { n = Integer.parseInt(a[3]); if (n < 0) throw new NumberFormatException(); } catch (NumberFormatException e) { tell(sender, "Bitte eine positive ganze Zahl (oder 0) eingeben."); return true; }
                    UUID id = target.getUniqueId(); int old = points.getOrDefault(id, 0);
                    int value = switch (a[1].toLowerCase(Locale.ROOT)) { case "set" -> n; case "add" -> old + n; default -> Math.max(0, old - n); };
                    points.put(id, value); savePoints(); tell(sender, target.getName() + " hat jetzt " + value + " Punkte.");
                } else {
                    Player target = a.length > 1 ? find(a[1]) : sender instanceof Player p ? p : null;
                    if (target == null) { tell(sender, "Nutze /ho points <Online-Spieler>."); return true; }
                    tell(sender, target.getName() + ": " + points.getOrDefault(target.getUniqueId(), 0) + " Punkte.");
                }
            }
            default -> tell(sender, "Unbekannter Befehl. /ho help");
        }
        return true;
    }

    private String coords(Location l) { return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }
    private void drawCorners(Player p, Location a, Location b) {
        int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
        int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
        int[] xs = {x1, x2}, ys = {y1, y2}, zs = {z1, z2};
        for (int x : xs) for (int y : ys) for (int z : zs)
            p.spawnParticle(Particle.END_ROD, new Location(a.getWorld(), x + .5, y + .5, z + .5), 30, .3, .3, .3, .01);
    }
    private boolean inside(Location l, Location a, Location b) {
        if (a == null || b == null || !Objects.equals(l.getWorld(), a.getWorld()) || !Objects.equals(a.getWorld(), b.getWorld())) return false;
        return l.getX() >= Math.min(a.getX(), b.getX()) && l.getX() <= Math.max(a.getX(), b.getX()) + 1
            && l.getY() >= Math.min(a.getY(), b.getY()) && l.getY() <= Math.max(a.getY(), b.getY()) + 1
            && l.getZ() >= Math.min(a.getZ(), b.getZ()) && l.getZ() <= Math.max(a.getZ(), b.getZ()) + 1;
    }
    private void startApoRed(CommandSender sender) {
        if (running) { tell(sender, "Es läuft schon eine Runde."); return; }
        Player hunter = hunterId == null ? null : Bukkit.getPlayer(hunterId);
        if (hunter == null) { tell(sender, "Zuerst /ho hunter <Spieler> festlegen."); return; }
        Location spawn = readLocation("arenas.apored.spawn"), hunterSpawn = readLocation("arenas.apored.hunter");
        Location corner1 = readLocation("arenas.apored.pos1"), corner2 = readLocation("arenas.apored.pos2");
        if (spawn == null || hunterSpawn == null || corner1 == null || corner2 == null) { tell(sender, "Markiere zuerst apored pos1, pos2, spawn und hunter."); return; }
        if (!Objects.equals(spawn.getWorld(), hunterSpawn.getWorld()) || !Objects.equals(spawn.getWorld(), corner1.getWorld()) || !Objects.equals(spawn.getWorld(), corner2.getWorld())) { tell(sender, "Alle ApoRed-Markierungen müssen in derselben Welt sein."); return; }
        if (participants.isEmpty()) { tell(sender, "Noch keine Teilnehmer: /ho join."); return; }
        if (participants.contains(hunterId)) { tell(sender, "Der Jäger darf nicht gleichzeitig Teilnehmer sein. Erst /ho leave."); return; }
        List<Player> players = new ArrayList<>();
        for (UUID id : participants) { Player p = Bukkit.getPlayer(id); if (p != null) players.add(p); }
        if (players.isEmpty()) { tell(sender, "Kein Teilnehmer ist online."); return; }
        savedStates.clear(); eliminated.clear();
        savedStates.put(hunterId, new PlayerState(hunter));
        for (Player p : players) savedStates.put(p.getUniqueId(), new PlayerState(p));
        running = true; activeGame = "apored"; elapsed = 0; roundScore.clear(); scored.clear(); secondsLeft = 8 * 60;
        for (Player p : players) { p.teleport(spawn); p.setGameMode(GameMode.ADVENTURE); p.sendTitle(ChatColor.DARK_RED + "VERSTECK DICH!", "30 Sekunden Vorsprung", 10, 60, 10); }
        hunter.teleport(hunterSpawn); hunter.setGameMode(GameMode.ADVENTURE);
        hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30 * 20, 1, false, false));
        hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 * 20, 10, false, false));
        hunter.getInventory().setItemInMainHand(sneaker());
        AttributeInstance scale = hunter.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(1.5);
        hunter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 60 * 20, 1, false, true));
        broadcast(ChatColor.DARK_RED + "APORED FINDET DICH! " + ChatColor.GRAY + "30 Sekunden verstecken!");
        final int[] ticks = {0};
        roundTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!running) return;
            ticks[0]++; elapsed = ticks[0];
            if (ticks[0] == 30) {
                hunter.removePotionEffect(PotionEffectType.BLINDNESS);
                hunter.removePotionEffect(PotionEffectType.SLOWNESS);
                broadcast(ChatColor.RED + "APORED IST FREI! LAUFT!");
            }
            if (ticks[0] > 30) {
                secondsLeft--;
                if (secondsLeft % 60 == 0 && secondsLeft > 0) broadcast("Noch " + secondsLeft / 60 + " Minuten!");
                if (secondsLeft <= 0) { broadcast(ChatColor.GREEN + "Zeit abgelaufen! Überlebende gewinnen."); awardSurvivors(); stopRound(false); }
            }
        }, 20L, 20L);
    }
    private ItemStack sneaker() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "ApoReds Sneaker");
        meta.setLore(List.of(ChatColor.GRAY + "Ein Treffer. Ein Ende."));
        item.setItemMeta(meta);
        return item;
    }
    @EventHandler(ignoreCancelled = true) public void onDamage(EntityDamageByEntityEvent e) {
        if (!running || !(e.getEntity() instanceof Player victim)) return;
        if (!activeGame.equals("apored")) { if (participants.contains(victim.getUniqueId())) e.setCancelled(true); return; }
        if (!(e.getDamager() instanceof Player attacker)) {
            if (participants.contains(victim.getUniqueId())) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (!attacker.getUniqueId().equals(hunterId) || !participants.contains(victim.getUniqueId()) || eliminated.contains(victim.getUniqueId())) return;
        if (!attacker.getInventory().getItemInMainHand().isSimilar(sneaker())) return;
        if (!inside(victim.getLocation(), readLocation("arenas.apored.pos1"), readLocation("arenas.apored.pos2"))) return;
        eliminate(victim);
        victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 30, .5, .5, .5, .2);
        broadcast(ChatColor.RED + victim.getName() + " wurde von ApoReds Sneaker erwischt! " + ChatColor.GRAY + "(" + (participants.size() - eliminated.size()) + " übrig)");
        if (aliveCount() == 0) { broadcast("Alle gefunden! ApoRed gewinnt!"); stopRound(false); }
    }
    private int onlineParticipants() { int n = 0; for (UUID id : participants) if (Bukkit.getPlayer(id) != null) n++; return n; }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (!running) return;
        UUID id = e.getPlayer().getUniqueId();
        if (activeGame.equals("apored") && id.equals(hunterId)) { broadcast("ApoRed hat den Server verlassen. Runde abgebrochen."); stopRound(false); }
        else if (participants.contains(id)) {
            eliminated.add(id);
            if (aliveCount() == 0) { broadcast("Keine Teilnehmer mehr aktiv."); stopRound(false); }
        }
    }
    private void stopRound(boolean announce) {
        if (roundTask != null) { roundTask.cancel(); roundTask = null; }
        if (!running && savedStates.isEmpty()) return;
        running = false; activeGame = ""; hidden.clear(); collected.clear(); finished.clear(); lastSafe.clear();
        for (Map.Entry<UUID, PlayerState> entry : savedStates.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) entry.getValue().restore(p);
        }
        savedStates.clear(); eliminated.clear(); roundScore.clear(); scored.clear();
        if (announce) broadcast("Runde gestoppt.");
    }

    private boolean validMarker(String arena, String type) {
        return switch (arena) {
            case "hotel" -> type.equals("closet");
            case "supermarkt" -> List.of("crate", "checkout").contains(type);
            case "schule" -> type.equals("finish");
            case "friedhof" -> bingoItems.contains(type) || type.equals("delivery");
            default -> false;
        };
    }
    private List<Location> markerLocations(String arena, String type) {
        String path = "markers." + arena + "." + type;
        List<Location> result = new ArrayList<>(getConfig().getLocationList(path));
        Location single = readLocation(path);
        if (single != null) result.add(single);
        return result;
    }
    private boolean near(Location player, Location marker, double radius) {
        return marker != null && Objects.equals(player.getWorld(), marker.getWorld()) && player.distanceSquared(marker) <= radius * radius;
    }
    private boolean nearAny(Location location, String arena, String type, double radius) {
        for (Location marker : markerLocations(arena, type)) if (near(location, marker, radius)) return true;
        return false;
    }
    private int aliveCount() {
        int n = 0;
        for (UUID id : participants) if (Bukkit.getPlayer(id) != null && !eliminated.contains(id)) n++;
        return n;
    }
    private void score(Player p, int amount) {
        UUID id = p.getUniqueId();
        points.merge(id, amount, Integer::sum); savePoints();
        tell(p, ChatColor.LIGHT_PURPLE + "+" + amount + " Punkte! Gesamt: " + points.get(id));
    }
    private void eliminate(Player p) {
        if (!eliminated.add(p.getUniqueId())) return;
        hidden.remove(p.getUniqueId());
        if (activeGame.equals("apored")) score(p, Math.max(0, elapsed - 30) / 20);
        p.setGameMode(GameMode.ADVENTURE);
        Location lobby = readLocation("lobby"); if (lobby != null) p.teleport(lobby);
        p.sendTitle(ChatColor.RED + "AUSGESCHIEDEN", "Nächste Runde wieder dabei", 5, 50, 10);
    }
    private void awardSurvivors() {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !eliminated.contains(id) && scored.add(id)) score(p, 30);
        }
    }
    private void startGame(CommandSender sender, String game) {
        if (running) { tell(sender, "Es läuft schon eine Runde."); return; }
        Location spawn = readLocation("arenas." + game + ".spawn");
        if (spawn == null || readLocation("lobby") == null) { tell(sender, "Arena-Spawn oder Lobby fehlt."); return; }
        if (participants.isEmpty()) { tell(sender, "Erst /ho join benutzen."); return; }
        if (game.equals("hotel") && markerLocations("hotel", "closet").isEmpty()) { tell(sender, "Markiere Schränke mit /ho marker hotel closet add."); return; }
        if (game.equals("supermarkt") && (markerLocations(game, "crate").isEmpty() || markerLocations(game, "checkout").isEmpty())) { tell(sender, "Markiere Kisten und Kasse."); return; }
        if (game.equals("schule") && markerLocations(game, "finish").isEmpty()) { tell(sender, "Markiere die Ziellinie."); return; }
        if (game.equals("friedhof")) {
            for (String type : bingoItems) if (markerLocations(game, type).isEmpty()) { tell(sender, "Fehlt: /ho marker friedhof " + type + " add"); return; }
            if (markerLocations(game, "delivery").isEmpty()) { tell(sender, "Fehlt: /ho marker friedhof delivery"); return; }
        }
        savedStates.clear(); eliminated.clear(); hidden.clear(); finished.clear(); collected.clear(); roundScore.clear(); scored.clear();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            savedStates.put(id, new PlayerState(p)); p.teleport(spawn); p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
        }
        if (savedStates.isEmpty()) { tell(sender, "Keine Teilnehmer online."); return; }
        running = true; activeGame = game; elapsed = 0; secondsLeft = GAME_LENGTH; teacherWatching = false;
        broadcast(ChatColor.DARK_PURPLE + game.toUpperCase(Locale.ROOT) + " beginnt!");
        roundTask = Bukkit.getScheduler().runTaskTimer(this, () -> tickGame(game), 20L, 20L);
    }
    private void tickGame(String game) {
        if (!running || !activeGame.equals(game)) return;
        elapsed++; secondsLeft--;
        if (game.equals("hotel")) {
            int phase = elapsed % 45;
            if (phase == 25) { hidden.clear(); broadcast(ChatColor.RED + "MONSTER KOMMT IN 10 SEKUNDEN! Rechtsklick am Schrank!"); }
            if (phase == 35) {
                for (UUID id : participants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && !eliminated.contains(id) && !hidden.contains(id)) eliminate(p);
                }
                broadcast(ChatColor.RED + "Das Monster ist vorbeigezogen!");
            }
            if (phase == 40) hidden.clear();
        }
        if (game.equals("schule")) {
            if (elapsed % 12 == 0) {
                teacherWatching = !teacherWatching;
                broadcast(teacherWatching ? ChatColor.RED + "LEHRER SCHAUT! STILLSTEHEN!" : ChatColor.GREEN + "LEHRER SCHAUT WEG! LAUFEN!");
            }
        }
        if (game.equals("supermarkt") && elapsed == 120) broadcast(ChatColor.DARK_RED + "LICHT AUS! Der Hausmeister kommt!");
        if (game.equals("friedhof") && elapsed == 90) broadcast(ChatColor.DARK_RED + "Der Totengräber ist jetzt unterwegs!");
        if (secondsLeft % 60 == 0 && secondsLeft > 0) broadcast("Noch " + secondsLeft / 60 + " Minuten.");
        if (secondsLeft <= 0 || aliveCount() == 0) {
            if (game.equals("hotel") || game.equals("schule")) awardSurvivors();
            broadcast(ChatColor.GOLD + "Runde beendet!"); stopRound(false);
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!running || !participants.contains(e.getPlayer().getUniqueId()) || eliminated.contains(e.getPlayer().getUniqueId())) return;
        if (e.getTo() == null || e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer(); UUID id = p.getUniqueId();
        if (activeGame.equals("hotel") && hidden.contains(id)) { e.setTo(e.getFrom()); return; }
        if (activeGame.equals("schule")) {
            if (teacherWatching) { eliminate(p); return; }
            if (nearAny(e.getTo(), "schule", "finish", 2.5) && finished.add(id)) {
                score(p, 40); eliminate(p); broadcast(ChatColor.GREEN + p.getName() + " hat das Ziel erreicht!");
            }
        }
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (!running || !participants.contains(e.getPlayer().getUniqueId()) || eliminated.contains(e.getPlayer().getUniqueId())) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer(); UUID id = p.getUniqueId(); Location loc = p.getLocation();
        if (activeGame.equals("hotel") && elapsed % 45 >= 25 && elapsed % 45 < 40 && nearAny(loc, "hotel", "closet", 2.5)) {
            if (hidden.add(id)) tell(p, ChatColor.GREEN + "Du bist im Schrank versteckt. Nicht bewegen!");
            e.setCancelled(true);
        }
        if (activeGame.equals("supermarkt")) {
            if (nearAny(loc, "supermarkt", "crate", 2.5) && !collected.computeIfAbsent(id, k -> new HashSet<>()).contains("carrying")) {
                collected.get(id).add("carrying"); tell(p, "Bierkasten aufgenommen! Bring ihn zur Kasse."); e.setCancelled(true);
            } else if (nearAny(loc, "supermarkt", "checkout", 2.5) && collected.computeIfAbsent(id, k -> new HashSet<>()).remove("carrying")) {
                roundScore.merge(id, 1, Integer::sum); score(p, 5); tell(p, "Kasten abgegeben: " + roundScore.get(id)); e.setCancelled(true);
            }
        }
        if (activeGame.equals("friedhof")) {
            Set<String> bag = collected.computeIfAbsent(id, k -> new HashSet<>());
            for (String type : bingoItems) if (!bag.contains(type) && nearAny(loc, "friedhof", type, 2.5)) {
                bag.add(type); tell(p, "Gefunden: " + type + " (" + bag.size() + "/5)"); e.setCancelled(true); break;
            }
            if (bag.size() == 5 && nearAny(loc, "friedhof", "delivery", 2.5) && finished.add(id)) {
                score(p, 50); broadcast(ChatColor.GOLD + p.getName() + " hat Friedhof-Bingo geschafft!"); eliminate(p);
            }
        }
    }
    @EventHandler public void onEnvironmentalDamage(EntityDamageEvent e) {
        if (!running || !(e.getEntity() instanceof Player p) || !participants.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }
    @EventHandler public void onRejoin(PlayerJoinEvent e) {
        if (running && eliminated.contains(e.getPlayer().getUniqueId())) {
            Location lobby = readLocation("lobby"); if (lobby != null) Bukkit.getScheduler().runTask(this, () -> e.getPlayer().teleport(lobby));
        }
    }

    private static final class PlayerState {
        private final GameMode mode; private final Location location;
        private final ItemStack[] inventory, armor; private final ItemStack offHand;
        private final Map<PotionEffectType, PotionEffect> effects = new HashMap<>();
        private final double scale; private final float walkSpeed;
        PlayerState(Player p) {
            mode = p.getGameMode(); location = p.getLocation().clone();
            inventory = p.getInventory().getStorageContents().clone(); armor = p.getInventory().getArmorContents().clone();
            offHand = p.getInventory().getItemInOffHand().clone();
            for (PotionEffect effect : p.getActivePotionEffects()) effects.put(effect.getType(), effect);
            AttributeInstance attr = p.getAttribute(Attribute.SCALE); scale = attr == null ? 1.0 : attr.getBaseValue();
            walkSpeed = p.getWalkSpeed();
        }
        void restore(Player p) {
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
            effects.values().forEach(p::addPotionEffect);
            AttributeInstance attr = p.getAttribute(Attribute.SCALE); if (attr != null) attr.setBaseValue(scale);
            p.setWalkSpeed(walkSpeed);
            p.getInventory().setStorageContents(inventory); p.getInventory().setArmorContents(armor);
            p.getInventory().setItemInOffHand(offHand);
            p.setGameMode(mode); p.teleport(location);
        }
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (a.length == 1) return match(a[0], s.hasPermission("horrorolympics.admin") ? List.of("help", "join", "leave", "lobby", "setlobby", "arena", "marker", "hunter", "revive", "start", "stop", "status", "points") : List.of("help", "join", "leave", "lobby", "points"));
        if (a.length == 2 && a[0].equalsIgnoreCase("arena")) return match(a[1], ARENAS);
        if (a.length == 3 && a[0].equalsIgnoreCase("arena")) return match(a[2], List.of("pos1", "pos2", "spawn", "hunter", "spectator", "show"));
        if (a.length == 2 && a[0].equalsIgnoreCase("points")) return match(a[1], List.of("set", "add", "remove"));
        if (a.length == 2 && a[0].equalsIgnoreCase("start")) return match(a[1], ARENAS);
        if (a.length == 2 && a[0].equalsIgnoreCase("marker")) return match(a[1], ARENAS);
        if (a.length == 3 && a[0].equalsIgnoreCase("marker")) return match(a[2], switch (a[1].toLowerCase(Locale.ROOT)) {
            case "hotel" -> List.of("closet"); case "supermarkt" -> List.of("crate", "checkout");
            case "schule" -> List.of("finish"); case "friedhof" -> List.of("skull", "bone", "candle", "beer", "eye", "delivery"); default -> List.of(); });
        if (a.length == 2 && a[0].equalsIgnoreCase("revive")) return match(a[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (a.length == 2 && a[0].equalsIgnoreCase("hunter")) return match(a[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (a.length == 3 && a[0].equalsIgnoreCase("points")) return match(a[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }
    private List<String> match(String prefix, List<String> options) { return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList(); }
}
