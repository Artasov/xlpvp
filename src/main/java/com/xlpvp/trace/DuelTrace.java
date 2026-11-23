package com.xlpvp.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.xlpvp.Core;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Server-only observations. The Bukkit bridge calls start/end on the server thread. */
@EventBusSubscriber(modid = Core.MODID)
public final class DuelTrace {
    private DuelTrace() {}

    private static volatile Recording current;
    private record LastFile(UUID duel, Path path, String reason) {}
    private static volatile LastFile last;
    private static long tickStart;

    public static boolean active(Player player) {
        Recording r = current;
        return r != null && player instanceof ServerPlayer && r.contains(player.getUUID());
    }

    public static boolean pair(Player player, Entity other) {
        Recording r = current;
        return r != null && player instanceof ServerPlayer && other instanceof ServerPlayer
                && player != other && r.contains(player.getUUID()) && r.contains(other.getUUID());
    }

    public static synchronized String start(UUID duel, UUID first, UUID second, String kit) {
        if (current != null) {
            if (current.duel.equals(duel)) return "Запись уже идёт: " + current.path.getFileName();
            throw new IllegalStateException("Уже записывается другая дуэль.");
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) throw new IllegalStateException("Сервер ещё не готов.");
        ServerPlayer a = server.getPlayerList().getPlayer(first);
        ServerPlayer b = server.getPlayerList().getPlayer(second);
        ServerPlayer levelActor = null;
        // Practice bots are registered in the duel level and have no PlayerList entry.
        if (a == null && b != null && b.serverLevel().getEntity(first) instanceof ServerPlayer participant) {
            a = levelActor = participant;
        } else if (b == null && a != null && a.serverLevel().getEntity(second) instanceof ServerPlayer participant) {
            b = levelActor = participant;
        }
        if (a == null || b == null || first.equals(second)) throw new IllegalStateException("Оба участника должны быть в игре.");
        try {
            Path dir = Files.createDirectories(FMLPaths.GAMEDIR.get().resolve("logs/duel-traces"));
            var attrs = dir.getFileSystem().supportedFileAttributeViews().contains("posix")
                    ? new java.nio.file.attribute.FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                    : new java.nio.file.attribute.FileAttribute<?>[0];
            String prefix = Instant.now().toString().replace(':', '-') + "_" + duel + "_";
            Path file = Files.createTempFile(dir, prefix, ".jsonl", attrs);
            JsonObject header = new JsonObject();
            header.addProperty("format", 1);
            header.addProperty("utc", Instant.now().toString());
            header.addProperty("duel", duel.toString());
            header.addProperty("kit", kit);
            JsonArray players = new JsonArray();
            for (ServerPlayer player : new ServerPlayer[]{a, b}) {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", player.getUUID().toString());
                entry.addProperty("name", player.getGameProfile().getName());
                entry.addProperty("ip", player.getIpAddress());
                if (player == levelActor) entry.addProperty("level_actor", true);
                players.add(entry);
            }
            header.add("players", players);
            Recording r = new Recording(server, duel, first, second, levelActor, file);
            header.addProperty("trace", r.trace.toString());
            r.append("start", header);
            r.worker.start();
            current = r;
            r.signalClients(true);
            return "Запись начата: logs/duel-traces/" + file.getFileName();
        } catch (IOException ex) {
            LogUtils.getLogger().error("Could not open duel trace", ex);
            throw new IllegalStateException("Не удалось открыть файл записи.", ex);
        }
    }

    public static boolean isRecording(UUID duel) {
        Recording r = current;
        return r != null && r.duel.equals(duel);
    }

    public static String status(UUID duel) {
        Recording r = current;
        if (r != null && r.duel.equals(duel)) return "Идёт запись: logs/duel-traces/" + r.path.getFileName();
        LastFile file = last;
        if (file != null && file.duel().equals(duel)) {
            return "Запись завершена (" + file.reason() + "): logs/duel-traces/" + file.path().getFileName();
        }
        return "Запись не ведётся.";
    }

    public static void marker(UUID duel, String kind, String detail) {
        Recording r = current;
        if (r != null && r.duel.equals(duel)) {
            JsonObject fields = new JsonObject();
            fields.addProperty("detail", detail);
            r.append(kind, fields);
        }
    }

    public static synchronized void end(UUID duel, String reason) {
        Recording r = current;
        if (r != null && r.duel.equals(duel)) {
            r.finish(reason);
            last = new LastFile(duel, r.path, reason);
            current = null;
            r.server.executeIfPossible(() -> r.signalClients(false));
        }
    }

    static void clientReady(ServerPlayer player, UUID trace) {
        Recording r = current;
        if (r != null && r.trace.equals(trace) && r.contains(player.getUUID()) && r.ready.add(player.getUUID())) {
            event(player, null, "client_trace_ready", "trace", trace);
        }
    }

    public static void event(Player actor, Entity target, String kind, Object... keyValues) {
        Recording r = current;
        if (r == null || !(actor instanceof ServerPlayer) || !r.contains(actor.getUUID())) return;
        JsonObject fields = new JsonObject();
        fields.addProperty("actor", actor.getUUID().toString());
        if (target != null) fields.addProperty("target", target.getUUID().toString());
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) fields.addProperty(key, n);
            else if (value instanceof Boolean b) fields.addProperty(key, b);
            else if (value != null) fields.addProperty(key, value.toString());
        }
        r.append(kind, fields);
    }

    @SubscribeEvent
    public static void tickStart(ServerTickEvent.Pre e) {
        if (current != null) tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void tickEnd(ServerTickEvent.Post e) {
        Recording r = current;
        if (r == null) return;
        long now = System.nanoTime();
        JsonObject tick = new JsonObject();
        if (tickStart >= r.start) tick.addProperty("work_us", (now - tickStart) / 1000);
        if (r.previousTick != 0) tick.addProperty("interval_us", (now - r.previousTick) / 1000);
        r.previousTick = now;
        r.append("tick", tick);
        for (UUID id : r.players) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p == null && r.levelActor != null && r.levelActor.getUUID().equals(id) && !r.levelActor.isRemoved()) {
                p = r.levelActor;
            }
            if (p == null) continue;
            var v = p.getDeltaMovement();
            event(p, null, "state", "dimension", p.level().dimension().location(), "x", p.getX(), "y", p.getY(), "z", p.getZ(),
                    "vx", v.x, "vy", v.y, "vz", v.z, "yaw", p.getYRot(), "pitch", p.getXRot(), "ground", p.onGround(),
                    "sprint", p.isSprinting(), "hp", p.getHealth(), "hurtTicks", p.hurtTime, "invulnerableTicks", p.invulnerableTime,
                    "mainHand", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()), "ping_ms", p.connection.latency(),
                    "local_forward", p == r.levelActor ? p.zza : null, "local_strafe", p == r.levelActor ? p.xxa : null);
        }
    }

    @SubscribeEvent
    public static void onShutdown(ServerStoppingEvent e) {
        Recording r = current;
        if (r != null) end(r.duel, "server_stopping");
    }

    private static final class Recording {
        final MinecraftServer server;
        final UUID duel;
        final UUID trace = UUID.randomUUID();
        final UUID[] players;
        final Set<UUID> ready = new HashSet<>();
        final ServerPlayer levelActor;
        final Path path;
        final long start = System.nanoTime();
        final BufferedWriter out;
        final Thread worker;
        final ArrayBlockingQueue<JsonObject> rows = new ArrayBlockingQueue<>(4096);
        long previousTick;
        long sequence;
        long dropped;
        volatile String endReason;

        Recording(MinecraftServer server, UUID duel, UUID a, UUID b, ServerPlayer levelActor, Path file) throws IOException {
            this.server = server;
            this.duel = duel;
            players = new UUID[]{a, b};
            this.levelActor = levelActor;
            path = file;
            out = Files.newBufferedWriter(file);
            worker = new Thread(this::write, "xlpvp-duel-trace");
        }

        boolean contains(UUID id) { return players[0].equals(id) || players[1].equals(id); }

        void signalClients(boolean start) {
            for (int i = 0; i < players.length; i++) {
                ServerPlayer player = server.getPlayerList().getPlayer(players[i]);
                if (player == null) continue;
                boolean sent = new DuelTracePackets.Control(trace, duel, players[1 - i], start).sendTo(player);
                if (start) event(player, null, "client_trace_requested", "supported", sent);
            }
        }

        synchronized void append(String kind, JsonObject fields) {
            if (endReason != null) return;
            stamp(kind, fields);
            if (!rows.offer(fields)) dropped++;
        }

        synchronized void finish(String reason) { endReason = reason; }

        private void stamp(String kind, JsonObject row) {
            row.addProperty("seq", ++sequence);
            row.addProperty("us", (System.nanoTime() - start) / 1000);
            row.addProperty("tick", server.getTickCount());
            row.addProperty("event", kind);
        }

        private void write() {
            long flushed = System.nanoTime();
            JsonObject combatCounts = new JsonObject();
            try (out) {
                while (endReason == null || !rows.isEmpty()) {
                    JsonObject row = rows.poll(200, TimeUnit.MILLISECONDS);
                    if (row != null) {
                        countCombatEvent(row, combatCounts);
                        out.write(row.toString());
                        out.newLine();
                    }
                    if (System.nanoTime() - flushed >= 500_000_000L) {
                        out.flush();
                        flushed = System.nanoTime();
                    }
                }
                JsonObject footer = new JsonObject();
                footer.addProperty("reason", endReason);
                footer.addProperty("dropped", dropped);
                footer.add("combat_counts_by_actor", combatCounts);
                stamp("end", footer);
                out.write(footer.toString());
                out.newLine();
            } catch (IOException | InterruptedException ex) {
                LogUtils.getLogger().error("Duel trace write failed: {}", path, ex);
                synchronized (DuelTrace.class) {
                    if (current == this) end(duel, "write_failed");
                    if (last != null && path.equals(last.path())) last = new LastFile(duel, path, "ошибка записи");
                }
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }

        private static void countCombatEvent(JsonObject row, JsonObject counts) {
            if (!row.has("actor")) return;
            String kind = row.get("event").getAsString();
            switch (kind) {
                case "melee_attempt", "melee_accepted", "rod_entity_impact", "rod_motion_sent" -> { }
                case "melee_rejected", "rod_rejected" -> {
                    if (row.has("reason")) kind += "." + row.get("reason").getAsString();
                }
                case "melee_damage_result" -> kind += "." + row.get("accepted").getAsBoolean();
                default -> { return; }
            }
            String actor = row.get("actor").getAsString();
            if (!counts.has(actor)) counts.add(actor, new JsonObject());
            JsonObject player = counts.getAsJsonObject(actor);
            int previous = player.has(kind) ? player.get(kind).getAsInt() : 0;
            player.addProperty(kind, previous + 1);
        }
    }
}
