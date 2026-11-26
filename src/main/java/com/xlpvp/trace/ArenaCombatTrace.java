package com.xlpvp.trace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lightweight combat events for an opt-in HG or Sky Wars match. The plugin owns the file. */
public final class ArenaCombatTrace {
    private static final Map<UUID, Session> BY_ACTOR = new ConcurrentHashMap<>();

    private ArenaCombatTrace() {}

    public static Runnable start(UUID match, String mode, String world, Set<UUID> roster,
                                 Consumer<Map<String, Object>> write) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("Minecraft server is unavailable");
        Session session = new Session(match, world, Set.copyOf(roster), write, System.nanoTime(), new ConcurrentHashMap<>());
        Map<UUID, ServerPlayer> players = new HashMap<>();
        for (UUID id : roster) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && session.inWorld(player)) {
                players.put(id, player);
                BY_ACTOR.put(id, session);
            }
        }
        try {
            for (ServerPlayer player : players.values()) {
                boolean supported = new DuelTracePackets.ArenaControl(match, mode, true).sendTo(player);
                event(player, null, "client_trace_requested", "supported", supported);
            }
        } catch (RuntimeException | LinkageError error) {
            players.keySet().forEach(id -> BY_ACTOR.remove(id, session));
            throw error;
        }
        return () -> players.forEach((id, player) -> {
            if (BY_ACTOR.remove(id, session)) new DuelTracePackets.ArenaControl(match, mode, false).sendTo(player);
        });
    }

    static boolean active(Player player) {
        return BY_ACTOR.containsKey(player.getUUID());
    }

    static boolean pair(Player player, Entity target) {
        Session session = BY_ACTOR.get(player.getUUID());
        return session != null && target instanceof ServerPlayer && target != player && target.level() == player.level()
                && session.inWorld(player) && session.roster().contains(target.getUUID());
    }

    static void ready(ServerPlayer player, UUID trace) {
        Session session = BY_ACTOR.get(player.getUUID());
        if (session != null && session.match().equals(trace) && session.attacks().putIfAbsent(player.getUUID(), 0L) == null) {
            event(player, null, "client_trace_ready", "trace", trace);
        }
    }

    static void event(Player player, Entity target, String kind, Object... pairs) {
        if (!kind.startsWith("melee_") && !kind.startsWith("client_trace_")
                && !kind.equals("in_attack") && !kind.equals("in_swing")) return;
        Session session = BY_ACTOR.get(player.getUUID());
        if (session == null || !session.inWorld(player)) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event", kind);
        row.put("actorUuid", player.getUUID().toString());
        row.put("combatUs", (System.nanoTime() - session.start()) / 1000);
        if ("in_attack".equals(kind)) {
            Long attack = session.attacks().computeIfPresent(player.getUUID(), (id, previous) -> previous + 1L);
            if (attack != null) row.put("attackOrdinal", attack);
        }
        if (target != null) row.put("targetEntityId", target.getId());
        if (target instanceof ServerPlayer) row.put("targetUuid", target.getUUID().toString());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value instanceof Number number && !Double.isFinite(number.doubleValue())) continue;
            if (value != null) row.put((String) pairs[i], value instanceof Number || value instanceof Boolean
                    ? value : value.toString());
        }
        session.write().accept(row);
    }

    private record Session(UUID match, String world, Set<UUID> roster, Consumer<Map<String, Object>> write, long start,
                           Map<UUID, Long> attacks) {
        boolean inWorld(Player player) {
            return player.level().dimension().location().getPath().equals(world);
        }
    }
}
