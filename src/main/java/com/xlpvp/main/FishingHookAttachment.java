package com.xlpvp.main;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public interface FishingHookAttachment {
    @Nullable HookContact xlpvp$getContact();
    void xlpvp$setContact(HookContact contact);
    @Nullable Vec3 xlpvp$getVisualPosition(float partialTick);
    boolean xlpvp$isVisuallyAttachedTo(Entity target);
}
