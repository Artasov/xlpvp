package com.xlpvp.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("xlpvp")
@PrefixGameTestTemplate(false)
public final class FishingCastGameTests {
    @GameTest(template = "empty")
    public static void classicCastFromEyeAtEveryPitch(GameTestHelper test) {
        ServerPlayer caster = caster(test);
        for (float yaw : new float[]{0, 90}) {
            caster.setYRot(yaw);
            double angle = Math.toRadians(yaw);
            Vec3 origin = new Vec3(caster.getX() - 0.16 * Math.cos(angle),
                    caster.getEyeY() - 0.1, caster.getZ() - 0.16 * Math.sin(angle));
            for (float pitch : new float[]{0, -45, -89, 89}) {
                caster.setXRot(pitch);
                Vec3 mean = Vec3.ZERO;
                // Average independent casts to keep the original Gaussian spread in the test.
                for (int i = 0; i < 64; i++) {
                    FishingHook hook = new FishingHook(caster, test.getLevel(), 0, 0);
                    test.assertTrue(hook.position().distanceTo(origin) < 0.0001, "Incorrect cast origin");
                    mean = mean.add(hook.getDeltaMovement());
                }
                Vec3 expected = caster.getLookAngle().scale(1.5);
                test.assertTrue(mean.scale(1.0 / 64).distanceTo(expected) < 0.015,
                        "Incorrect launch velocity at yaw " + yaw + ", pitch " + pitch);
            }
        }
        double standingEye = caster.getEyeY();
        caster.setPose(Pose.CROUCHING);
        FishingHook crouched = new FishingHook(caster, test.getLevel(), 0, 0);
        test.assertTrue(caster.getEyeY() < standingEye, "Caster did not crouch");
        test.assertTrue(Math.abs(crouched.getY() - (standingEye - 0.1)) < 0.0001,
                "A crouched cast starts at classic standing eye height");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void classicAirTrajectory(GameTestHelper test) {
        ServerPlayer caster = caster(test);
        FishingHook hook = new FishingHook(caster, test.getLevel(), 0, 0);
        Vec3 start = caster.position().add(0, 2, 1.5);
        hook.setPos(start);
        hook.setDeltaMovement(0, 0, 1.5);
        hook.tick();
        checkPosition(test, hook, start.add(0, 0, 1.5), 1);
        for (int i = 2; i <= 10; i++) {
            hook.tick();
            if (i == 5) checkPosition(test, hook, start.add(0, -0.339719, 6.392222), i);
        }
        checkPosition(test, hook, start.add(0, -1.347734, 10.605217), 10);
        test.succeed();
    }

    private static void checkPosition(GameTestHelper test, FishingHook hook, Vec3 expected, int tick) {
        test.assertTrue(hook.position().distanceTo(expected) < 0.0001,
                "Dry flight at tick " + tick + " was " + hook.position() + ", expected " + expected);
    }

    @GameTest(template = "empty")
    public static void playerHitMargin(GameTestHelper test) {
        ServerPlayer caster = caster(test);
        ServerPlayer target = test.makeMockServerPlayerInLevel();
        target.setPos(caster.position().add(0, 0, 4));
        for (double margin : new double[]{0.29, 0.31}) {
            FishingHook hook = new FishingHook(caster, test.getLevel(), 0, 0);
            hook.setPos(target.getBoundingBox().maxX + margin, target.getY() + 0.6, target.getZ() - 1.5);
            hook.setDeltaMovement(0, 0, 2);
            hook.tick();
            test.assertTrue((hook.getHookedIn() == target) == (margin < 0.30),
                    "Unexpected result " + margin + " blocks outside the player's hitbox");
        }
        test.succeed();
    }

    private static ServerPlayer caster(GameTestHelper test) {
        ServerPlayer caster = test.makeMockServerPlayerInLevel();
        caster.setGameMode(GameType.SURVIVAL);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
        caster.setPos(test.absoluteVec(new Vec3(1.5, 32, 1.5)));
        return caster;
    }
}
