package com.xlpvp.main;

import com.xlpvp.Core;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Vector3f;

/** Relative to the target's body direction and current hitbox dimensions. */
@EventBusSubscriber(modid = Core.MODID, bus = EventBusSubscriber.Bus.MOD)
public record HookContact(int hookId, int targetId, Vector3f point) implements CustomPacketPayload {
    private static final Type<HookContact> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Core.MODID, "hook_contact"));
    private static final StreamCodec<ByteBuf, HookContact> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HookContact::hookId,
            ByteBufCodecs.VAR_INT, HookContact::targetId,
            ByteBufCodecs.VECTOR3F, HookContact::point, HookContact::new);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (contact, context) -> {
            if (context.player().level().getEntity(contact.hookId()) instanceof FishingHook hook) {
                ((FishingHookAttachment) hook).xlpvp$setContact(contact);
            }
        });
    }

    public void sendTo(ServerPlayer player) {
        if (player.connection.hasChannel(TYPE)) player.connection.send(this);
    }

    @Override
    public Type<HookContact> type() {
        return TYPE;
    }

    public static HookContact capture(FishingHook hook, Entity target) {
        AABB box = target.getBoundingBox();
        Vec3 start = hook.position();
        Vec3 end = start.add(hook.getDeltaMovement());
        // ProjectileUtil uses a 0.3-block margin and its EntityHitResult drops the ray intersection.
        Vec3 hit = box.contains(start) ? start : box.clip(start, end).orElseGet(() ->
                box.inflate(0.3F).clip(start, end).orElse(start));
        Vec3 relative = new Vec3(Mth.clamp(hit.x, box.minX, box.maxX) - target.getX(),
                Mth.clamp(hit.y, box.minY, box.maxY) - box.minY,
                Mth.clamp(hit.z, box.minZ, box.maxZ) - target.getZ()).yRot(bodyYaw(target));
        float width = Math.max(target.getBbWidth(), 1.0e-6F);
        float height = Math.max(target.getBbHeight(), 1.0e-6F);
        return new HookContact(hook.getId(), target.getId(),
                relative.multiply(1.0 / width, 1.0 / height, 1.0 / width).toVector3f());
    }

    public Vec3 positionOn(Entity target) {
        Vec3 offset = new Vec3(point).multiply(target.getBbWidth(), target.getBbHeight(), target.getBbWidth())
                .yRot(-bodyYaw(target));
        return offset.add(target.getX(), target.getBoundingBox().minY, target.getZ());
    }

    private static float bodyYaw(Entity target) {
        return (target instanceof LivingEntity living ? living.yBodyRot : target.getYRot()) * Mth.DEG_TO_RAD;
    }
}
