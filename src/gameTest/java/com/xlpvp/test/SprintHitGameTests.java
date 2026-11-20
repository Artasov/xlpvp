package com.xlpvp.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
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

        checkImpulse(test, attacker, 0.4, 0.4, "Standing hit");
        attacker.setSprinting(true);
        checkImpulse(test, attacker, 0.8, 0.5, "First sprint hit");
        attacker.setSprinting(true);
        checkImpulse(test, attacker, 0.4, 0.4, "Held sprint");
        attacker.setSprinting(false);
        attacker.setSprinting(true);
        var immune = test.makeMockPlayer(GameType.SURVIVAL);
        immune.setInvulnerable(true);
        immune.setPos(attacker.position().add(0, 0, 1.5));
        attacker.attack(immune);
        test.assertTrue(immune.getHealth() == immune.getMaxHealth(), "Rejected hit dealt damage");
        checkImpulse(test, attacker, 0.8, 0.5, "Sprint restarted between ticks");

        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(attacker.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK), 2);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, sword);
        checkImpulse(test, attacker, 1.4, 0.5, "Knockback II after spending sprint");
        attacker.setSprinting(false);
        attacker.setSprinting(true);
        checkImpulse(test, attacker, 1.8, 0.5, "First sprint hit with Knockback II");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void sprintResetAgainstMovingOpponent(GameTestHelper test) {
        var attacker = test.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);
        attacker.setPos(test.absoluteVec(new Vec3(1.5, 1, 1.5)));
        var victim = test.makeMockPlayer(GameType.SURVIVAL);
        victim.setPos(attacker.position().add(1, 0, 0));
        attacker.attack(victim);
        attacker.setSprinting(true);
        long firstTick = test.getLevel().getServer().getTickCount();
        float health = victim.getHealth();
        victim.setDeltaMovement(Vec3.ZERO);
        attacker.attack(victim);
        test.assertTrue(victim.getHealth() == health && victim.getDeltaMovement().equals(Vec3.ZERO),
            "A swing during hit immunity should not knock the victim back");

        test.runAfterDelay(10, () -> {
            // This mock is not registered in the world. Advance its damage immunity as for a real player.
            for (int i = 0; i < 10; i++) victim.tick();
            victim.setPos(attacker.position().add(1, 0, 0));
            victim.setOnGround(false);
            victim.setDeltaMovement(0.2, -0.3, -0.2);
            attacker.setYRot(0);
            float beforeNextHit = victim.getHealth();
            int immunity = victim.invulnerableTime;
            attacker.attack(victim);
            var expected = new Vec3(0.5, 0.35, 0.3);
            test.assertTrue(victim.getHealth() < beforeNextHit, "The next hit should deal damage after "
                + (test.getLevel().getServer().getTickCount() - firstTick) + " ticks, immunity=" + immunity
                + ", health=" + beforeNextHit + " -> " + victim.getHealth());
            test.assertTrue(expected.distanceTo(victim.getDeltaMovement()) < 0.00001,
                "Side hit should keep half the motion, push away from attacker and add sprint along yaw: " + victim.getDeltaMovement());
            test.succeed();
        });
    }

    private static void checkImpulse(GameTestHelper test, Player attacker, double horizontal, double vertical, String scenario) {
        // A fresh target isolates sprint state from the victim's ten-tick damage immunity.
        var victim = test.makeMockPlayer(GameType.SURVIVAL);
        victim.setPos(attacker.position().add(0, 0, 1.5));
        victim.setOnGround(true);
        attacker.attack(victim);
        test.assertTrue(victim.getHealth() < victim.getMaxHealth(), scenario + ": no damage");
        var expected = new Vec3(0, vertical, horizontal);
        test.assertTrue(expected.distanceTo(victim.getDeltaMovement()) < 0.00001, scenario + ": impulse " + victim.getDeltaMovement());
    }
}
