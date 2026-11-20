package com.xlpvp.test;

import com.xlpvp.main.FishingHookAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("xlpvp")
@PrefixGameTestTemplate(false)
public final class FishingContactGameTests {
    @GameTest(template = "empty")
    public static void hookFollowsLowAndHighHits(GameTestHelper test) {
        var owner = test.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.SURVIVAL);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
        var target = test.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        target.setNoAi(true);
        owner.setPos(target.position().add(0, 0, -2));
        for (double height : new double[]{0.15, 1.5}) {
            target.yBodyRot = 0;
            var hook = new FishingHook(owner, test.getLevel(), 0, 0);
            hook.setPos(target.position().add(0.1, height, -1.5));
            hook.setDeltaMovement(0, 0, 2);
            hook.tick();
            test.assertTrue(hook.getHookedIn() == target, "Missed the target");
            Vec3 offset = new Vec3(0.1, height, -target.getBbWidth() / 2);
            test.assertTrue(hook.position().distanceTo(target.position().add(offset)) < 1.0e-4,
                    "Hook moved away from the initial contact at height " + height);
            target.setPos(target.position().add(1, 0.25, 0));
            target.yBodyRot = 90;
            hook.tick();
            hook.tick();
            Vec3 rotated = offset.yRot((float) (-Math.PI / 2));
            test.assertTrue(hook.position().distanceTo(target.position().add(rotated)) < 1.0e-4,
                    "Hook lost contact when the target moved or turned");
            test.assertTrue(((FishingHookAttachment) hook).xlpvp$getContact().targetId() == target.getId(),
                    "Missing synchronized contact for a new tracker");
            Vec3 vanillaPosition = new Vec3(target.getX(), target.getY(0.8), target.getZ());
            Vec3 expectedPull = owner.position().subtract(vanillaPosition).scale(0.1);
            target.setDeltaMovement(Vec3.ZERO);
            hook.retrieve(owner.getMainHandItem());
            test.assertTrue(target.getDeltaMovement().distanceTo(expectedPull) < 1.0e-4,
                    "The visual contact changed the retrieval impulse");
        }
        test.succeed();
    }
}
