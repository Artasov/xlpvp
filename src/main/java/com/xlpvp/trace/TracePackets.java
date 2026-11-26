package com.xlpvp.trace;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.xlpvp.mixin.ClientDuelTraceInteractAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;

public final class TracePackets {
    private TracePackets() {}

    // Identity keys: distinct packets with identical coordinates still have distinct receipt times.
    // A cancelled, replaced or disconnected packet must never retain its connection or grow this cache indefinitely.
    private static final Cache<Packet<?>, Long> ARRIVALS = CacheBuilder.newBuilder().weakKeys().maximumSize(2048).build();

    private static boolean recordedInput(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundSwingPacket || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundSetCarriedItemPacket
                || packet instanceof ServerboundPlayerCommandPacket || packet instanceof ServerboundAcceptTeleportationPacket;
    }

    public static void received(ServerPlayer player, Packet<?> packet) {
        if (recordedInput(player, packet)) ARRIVALS.put(packet, System.nanoTime());
    }

    private static boolean recordedInput(ServerPlayer player, Packet<?> packet) {
        return DuelTrace.duelActive(player) ? recordedInput(packet)
                : packet instanceof ServerboundInteractPacket || packet instanceof ServerboundSwingPacket;
    }

    public static void inbound(ServerPlayer actor, Packet<?> packet) {
        if (!recordedInput(actor, packet)) return;
        Long received = ARRIVALS.asMap().remove(packet);
        // Missing for a synthetic packet, an upstream replacement, or an evicted timestamp.
        Long queueUs = received == null ? null : (System.nanoTime() - received) / 1000L;
        if (packet instanceof ServerboundMovePlayerPacket move) {
            DuelTrace.event(actor, null, "in_move", "netToMainUs", queueUs, "hasPos", move.hasPosition(), "hasRot", move.hasRotation(),
                    "x", move.hasPosition() ? move.getX(0) : null, "y", move.hasPosition() ? move.getY(0) : null,
                    "z", move.hasPosition() ? move.getZ(0) : null, "yaw", move.hasRotation() ? move.getYRot(0) : null,
                    "pitch", move.hasRotation() ? move.getXRot(0) : null, "onGround", move.isOnGround(),
                    "serverXBefore", actor.getX(), "serverYBefore", actor.getY(), "serverZBefore", actor.getZ());
        } else if (packet instanceof ServerboundInteractPacket interact) {
            // Vanilla exposes the action via this visitor. Dispatching it here has no game effects.
            interact.dispatch(new ServerboundInteractPacket.Handler() {
                public void onInteraction(InteractionHand hand) {}
                public void onInteraction(InteractionHand hand, Vec3 position) {}
                public void onAttack() {
                    Entity target = interact.getTarget(actor.serverLevel());
                    if (DuelTrace.duelActive(actor) && target != null && !DuelTrace.pair(actor, target)) return;
                    String gate = target == null ? "unresolved"
                            : !actor.serverLevel().getWorldBorder().isWithinBounds(target.blockPosition()) ? "world_border"
                            : !actor.canInteractWithEntity(target.getBoundingBox(), 1.0) ? "interaction_range" : "open";
                    DuelTrace.event(actor, target, "in_attack", "netToMainUs", queueUs, "targetResolved", target != null,
                            "targetEntityId", ((ClientDuelTraceInteractAccessor) (Object) interact).xlpvp$targetId(),
                            "packetGate", gate, "pingMs", actor.connection.latency(),
                            "distanceToBox", target == null ? null : Math.sqrt(target.getBoundingBox().distanceToSqr(actor.getEyePosition())),
                            "reach", actor.entityInteractionRange(),
                            "sneaking", interact.isUsingSecondaryAction(), "usingItem", actor.isUsingItem());
                }
            });
        } else if (packet instanceof ServerboundSwingPacket swing) {
            DuelTrace.event(actor, null, "in_swing", "netToMainUs", queueUs, "hand", swing.getHand());
        } else if (packet instanceof ServerboundUseItemPacket use) {
            recordRodUse(actor, use.getHand(), use.getSequence(), queueUs, "in_use_rod");
        } else if (packet instanceof ServerboundUseItemOnPacket useOn) {
            recordRodUse(actor, useOn.getHand(), useOn.getSequence(), queueUs, "in_use_rod_on_block");
        } else if (packet instanceof ServerboundSetCarriedItemPacket hotbar) {
            DuelTrace.event(actor, null, "in_hotbar", "netToMainUs", queueUs, "slot", hotbar.getSlot());
        } else if (packet instanceof ServerboundPlayerCommandPacket command) {
            DuelTrace.event(actor, null, "in_player_command", "netToMainUs", queueUs, "action", command.getAction());
        } else if (packet instanceof ServerboundAcceptTeleportationPacket confirm) {
            DuelTrace.event(actor, null, "in_confirm_position", "netToMainUs", queueUs, "teleportId", confirm.getId());
        }
    }

    private static void recordRodUse(ServerPlayer actor, InteractionHand hand, int sequence, Long queueUs, String event) {
        if (actor.getItemInHand(hand).getItem() instanceof FishingRodItem) {
            DuelTrace.event(actor, null, event, "netToMainUs", queueUs, "hand", hand, "sequence", sequence,
                    "hookBefore", actor.fishing == null ? null : actor.fishing.getId());
        }
    }

    public static void outbound(ServerPlayer recipient, Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> part : bundle.subPackets()) outbound(recipient, part);
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            Entity entity = recipient.serverLevel().getEntity(motion.getId());
            if (entity == recipient || DuelTrace.pair(recipient, entity)) {
                DuelTrace.event(recipient, entity, "out_velocity", "vx", motion.getXa(), "vy", motion.getYa(), "vz", motion.getZa());
            }
        } else if (packet instanceof ClientboundMoveEntityPacket move) {
            Entity entity = move.getEntity(recipient.serverLevel());
            if (DuelTrace.pair(recipient, entity)) {
                // Wire deltas use units of 1/4096 block; the tracker can send rotation without position.
                DuelTrace.event(recipient, entity, "out_track_move", "hasPos", move.hasPosition(), "hasRot", move.hasRotation(),
                        "dx4096", move.getXa(), "dy4096", move.getYa(), "dz4096", move.getZa(), "onGround", move.isOnGround(),
                        "yawByte", move.getyRot(), "pitchByte", move.getxRot());
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            Entity entity = recipient.serverLevel().getEntity(teleport.getId());
            if (DuelTrace.pair(recipient, entity)) {
                DuelTrace.event(recipient, entity, "out_track_position", "x", teleport.getX(), "y", teleport.getY(),
                        "z", teleport.getZ(), "onGround", teleport.isOnGround());
            }
        } else if (packet instanceof ClientboundAnimatePacket animation
                && (animation.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
                || animation.getAction() == ClientboundAnimatePacket.SWING_OFF_HAND)) {
            Entity entity = recipient.serverLevel().getEntity(animation.getId());
            if (DuelTrace.pair(recipient, entity)) {
                InteractionHand hand = animation.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
                        ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                DuelTrace.event(recipient, entity, "out_track_swing", "hand", hand);
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket correction) {
            DuelTrace.event(recipient, null, "out_self_position", "teleportId", correction.getId(),
                    "x", correction.getX(), "y", correction.getY(), "z", correction.getZ(), "yaw", correction.getYRot(),
                    "pitch", correction.getXRot(), "relative", correction.getRelativeArguments());
        }
    }
}
