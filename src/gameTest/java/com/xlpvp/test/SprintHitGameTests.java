package com.xlpvp.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("xlpvp")
@PrefixGameTestTemplate(false)
public final class SprintHitGameTests {
    @GameTest(template = "empty")
    public static void sprintBonusAppliesOncePerSprint(GameTestHelper test) {
        var attacker = test.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);
        attacker.setPos(test.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        attacker.setYRot(0);

        checkImpulse(test, attacker, 0.408, "Standing hit");
        attacker.setSprinting(true);
        checkImpulse(test, attacker, 0.816, "First sprint hit");
        attacker.setSprinting(true);
        checkImpulse(test, attacker, 0.408, "Held sprint");
        attacker.setSprinting(false);
        attacker.setSprinting(true);
        var immune = test.makeMockPlayer(GameType.SURVIVAL);
        immune.setInvulnerable(true);
        immune.setPos(attacker.position().add(0, 0, 1.5));
        attacker.attack(immune);
        test.assertTrue(immune.getHealth() == immune.getMaxHealth(), "Rejected hit dealt damage");
        checkImpulse(test, attacker, 0.816, "Sprint restarted between ticks");
        test.succeed();
    }

    private static void checkImpulse(GameTestHelper test, Player attacker, double expected, String scenario) {
        // A fresh target isolates sprint state from the victim's ten-tick damage immunity.
        var victim = test.makeMockPlayer(GameType.SURVIVAL);
        victim.setPos(attacker.position().add(0, 0, 1.5));
        victim.setOnGround(true);
        attacker.attack(victim);
        double impulse = victim.getDeltaMovement().horizontalDistance();
        test.assertTrue(victim.getHealth() < victim.getMaxHealth(), scenario + ": no damage");
        test.assertTrue(Math.abs(impulse - expected) < 0.00001, scenario + ": horizontal impulse " + impulse);
    }
}
