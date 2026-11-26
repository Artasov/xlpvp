package com.xlpvp.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.xlpvp.Core;
import com.xlpvp.client.ClientDuelTraceView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Local observations only. The client sends a start marker, never its recorded rows. */
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class ClientDuelTrace {
    private static volatile Recording current;

    private ClientDuelTrace() {}

    public static boolean active() {
        Recording recording = current;
        return recording != null && recording.endReason == null;
    }

    public static UUID opponent() {
        Recording recording = current;
        return recording == null ? null : recording.opponent;
    }

    static void control(DuelTracePackets.Control signal) {
        control(signal.trace(), signal.duel(), signal.opponent(), "duel", signal.start());
    }

    static void control(DuelTracePackets.ArenaControl signal) {
        control(signal.match(), signal.match(), null, signal.mode(), signal.start());
    }

    private static void control(UUID trace, UUID match, UUID opponent, String mode, boolean start) {
        Recording recording = current;
        boolean duel = opponent != null;
        String relativeDirectory = duel ? "logs/duel-traces" : "logs/arena-traces";
        if (!start) {
            if (recording != null && recording.trace.equals(trace)) stop("server_stop");
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null || recording != null && recording.trace.equals(trace)) return;
        stop("replaced");
        try {
            Path directory = Files.createDirectories(FMLPaths.GAMEDIR.get().resolve(relativeDirectory));
            Path file = Files.createTempFile(directory, "client-" + trace + "-", ".jsonl");
            Recording next = new Recording(trace, opponent, player.level().dimension(), file);
            current = next;
            event("start", "format", 1, "utc", Instant.now(), "trace", trace, duel ? "duel" : "matchId", match,
                    "mode", mode, "actor", player.getUUID(), "opponent", opponent);
            next.worker.start();
            event("trace_ready", "trace", trace);
            PacketDistributor.sendToServer(new DuelTracePackets.Ready(trace));
            player.displayClientMessage(Component.literal("Клиентская запись боя: " + relativeDirectory + "/" + file.getFileName()), false);
        } catch (IOException error) {
            LogUtils.getLogger().error("Could not open client duel trace", error);
            player.displayClientMessage(Component.literal("Не удалось открыть файл клиентской записи боя."), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop("disconnect");
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Recording recording = current;
        var player = Minecraft.getInstance().player;
        if (recording != null && recording.opponent == null && player != null
                && !recording.dimension.equals(player.level().dimension())) stop("left_arena");
    }

    private static void stop(String reason) {
        Recording recording = current;
        if (recording != null) {
            current = null;
            ClientDuelTraceView.clear();
            recording.finish(reason);
        }
    }

    public static void event(String kind, Object... pairs) {
        Recording recording = current;
        if (recording == null || recording.endReason != null) return;
        JsonObject row = new JsonObject();
        var player = Minecraft.getInstance().player;
        if (player != null) row.addProperty("clientTick", player.tickCount);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Number number && Double.isFinite(number.doubleValue())) row.addProperty(key, number);
            else if (value instanceof Boolean bool) row.addProperty(key, bool);
            else if (value instanceof double[] coordinates) {
                JsonArray array = new JsonArray(coordinates.length);
                for (double coordinate : coordinates) array.add(coordinate);
                row.add(key, array);
            }
            else if (value != null) row.addProperty(key, value.toString());
        }
        recording.append(kind, row);
    }

    public static void rod(String kind, FishingHook hook, int target) {
        if (!active() || hook.getOwner() == null) return;
        UUID owner = hook.getOwner().getUUID();
        var player = Minecraft.getInstance().player;
        if (player == null || !owner.equals(player.getUUID()) && !owner.equals(opponent())) return;
        event(kind, "owner", owner, "hook", hook.getId(), "targetEntityId", target >= 0 ? target : null,
                "ageTicks", hook.tickCount, "x", hook.getX(), "y", hook.getY(), "z", hook.getZ());
    }

    private static final class Recording {
        final UUID trace, opponent;
        final ResourceKey<Level> dimension;
        final Path path;
        final BufferedWriter out;
        final Thread worker;
        final ArrayBlockingQueue<JsonObject> rows = new ArrayBlockingQueue<>(4096);
        final long start = System.nanoTime();
        private long sequence, dropped, attackOrdinal, clickOrdinal;
        private volatile String endReason;

        Recording(UUID trace, UUID opponent, ResourceKey<Level> dimension, Path path) throws IOException {
            this.trace = trace;
            this.opponent = opponent;
            this.dimension = dimension;
            this.path = path;
            out = Files.newBufferedWriter(path);
            worker = new Thread(this::write, "xlpvp-client-duel-trace");
        }

        synchronized void append(String kind, JsonObject row) {
            if (endReason != null) return;
            if ("attack_start".equals(kind)) row.addProperty("clickOrdinal", ++clickOrdinal);
            if ("out_attack".equals(kind)) {
                row.addProperty("attackOrdinal", ++attackOrdinal);
                row.addProperty("clickOrdinal", clickOrdinal);
            }
            stamp(kind, row);
            if (!rows.offer(row)) dropped++;
        }

        synchronized void finish(String reason) { endReason = reason; }

        private void stamp(String kind, JsonObject row) {
            row.addProperty("seq", ++sequence);
            row.addProperty("us", (System.nanoTime() - start) / 1000);
            row.addProperty("event", kind);
        }

        private void write() {
            long flushed = System.nanoTime();
            try (out) {
                while (endReason == null || !rows.isEmpty()) {
                    JsonObject row = rows.poll(200, TimeUnit.MILLISECONDS);
                    if (row != null) {
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
                stamp("end", footer);
                out.write(footer.toString());
                out.newLine();
            } catch (IOException | InterruptedException error) {
                finish("write_failed");
                LogUtils.getLogger().error("Client duel trace write failed: {}", path, error);
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
    }
}
