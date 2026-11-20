package com.xlpvp.test;

import com.xlpvp.main.FishingHookVisual;
import com.xlpvp.main.HookContact;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

@GameTestHolder("xlpvp")
@PrefixGameTestTemplate(false)
public final class FishingVisualGameTests {
    @GameTest(template = "empty")
    public static void confirmedFlyPastReleasesPredictedContact(GameTestHelper test) {
        replayDelayedContact(test, true);
    }

    @GameTest(template = "empty")
    public static void lateConfirmationReturnsToOriginalContactOnMovingTarget(GameTestHelper test) {
        replayDelayedContact(test, false);
    }

    @GameTest(template = "empty")
    public static void earlyConfirmationFinishesVisibleFlightAtTheLeg(GameTestHelper test) {
        // The small empty template is surrounded by barriers. This shot starts outside its footprint.
        var target = test.spawn(EntityType.ZOMBIE, new BlockPos(1, 20, 1));
        target.setOldPosAndRot();
        target.yBodyRot = target.yBodyRotO = 0;
        var hook = new FishingHook(test.makeMockServerPlayerInLevel(), test.getLevel(), 0, 0);
        Vec3 start = target.position().add(0.08, 0.42, -2.65);
        Vec3 velocity = new Vec3(0, 0, 1.3);
        Vec3 firstEnd = start.add(velocity);
        Vec3 nextVelocity = velocity.add(0, -0.04F, 0).scale(0.92F);
        Vec3 leg = target.getBoundingBox().clip(firstEnd, firstEnd.add(nextVelocity)).orElseThrow();
        var torso = new HookContact(hook.getId(), target.getId(), new Vector3f(0, 0.72F, -0.5F));
        for (boolean metadataFirst : new boolean[]{true, false}) {
            hook.setPos(start);
            hook.setOldPosAndRot();
            hook.setDeltaMovement(velocity);
            var visual = new FishingHookVisual();
            visual.confirmedDuringFlight(hook, target, velocity);
            if (metadataFirst) visual.syncedTarget(hook, target.getId(), target);
            hook.setPos(torso.positionOn(target));
            for (int tick = 0; tick < 4; tick++) {
                boolean metadata = metadataFirst || tick >= 2;
                if (!metadataFirst && tick == 2) visual.syncedTarget(hook, target.getId(), target);
                visual.tick(hook, metadataFirst && tick < 2 ? null : torso, metadata ? target.getId() : -1);
                Vec3 from = tick == 0 ? start : tick == 1 ? firstEnd : leg;
                Vec3 to = tick == 0 ? firstEnd : leg;
                for (float frame : new float[]{0, 0.5F, 1}) {
                    test.assertTrue(visual.position(frame).distanceTo(from.lerp(to, frame)) < 1.0e-5,
                            "Early confirmation changed the flight path on tick " + tick + " frame " + frame);
                }
                hook.tickCount++;
            }
        }
        test.succeed();
    }

    private static void replayDelayedContact(GameTestHelper test, boolean serverMiss) {
        var target = test.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        target.yBodyRot = target.yBodyRotO = 0;
        var hook = new FishingHook(test.makeMockServerPlayerInLevel(), test.getLevel(), 0, 0);
        hook.setPos(target.position().add(0.08, 0.30, -1.0));
        hook.setDeltaMovement(0, 0, 1.3);
        hook.getPositionCodec().setBase(hook.position());
        var visual = new FishingHookVisual();
        visual.predict(hook, target);
        var localOffset = new Vec3(0.08, 0.30, -target.getBbWidth() / 2);
        // The server sampled the target at a different height. Its payload arrives several ticks after the ID.
        var packet = new HookContact(hook.getId(), target.getId(), new Vector3f(0, 0.8F, 0));
        Vec3 lastFrame = hook.position();
        for (int tick = 0; tick <= 19; tick++) {
            target.setOldPosAndRot();
            if (tick > 0) target.setPos(target.position().add(0.12, 0, 0));
            hook.setOldPosAndRot();
            hook.setPos(hook.position().add(0, 0, 1));
            if (serverMiss && tick == 2) hook.getPositionCodec().setBase(hook.position());
            int syncedId = !serverMiss && tick >= 14 ? target.getId() : -1;
            if (!serverMiss && tick == 14) visual.syncedTarget(hook, syncedId, target);
            boolean keepState = visual.tick(hook, !serverMiss && tick >= 16 ? packet : null, syncedId);
            test.assertTrue(keepState || serverMiss, "Lost the local contact before delayed confirmation");
            Vec3 start = visual.position(0);
            test.assertTrue((start == null ? hook.getPosition(0) : start).distanceTo(lastFrame) < 1.0e-5,
                    "Visual position jumped between ticks at " + tick);
            Vec3 end = visual.position(1);
            lastFrame = end == null ? hook.position() : end;
            if (tick == 1 || !serverMiss && tick == 19) {
                test.assertTrue(lastFrame.distanceTo(target.position().add(localOffset)) < 1.0e-5,
                        "Bobber left the original low contact on the moving target");
                test.assertTrue(visual.position(0.5F).distanceTo(target.getPosition(0.5F).add(localOffset)) < 1.0e-5,
                        "Bobber drifted off the rendered target between ticks");
            }
            if (tick == (serverMiss ? 6 : 13)) {
                test.assertTrue(end == null, "Unconfirmed contact is still holding the bobber");
                if (serverMiss) break;
            }
            hook.tickCount++;
        }
        test.succeed();
    }
}
