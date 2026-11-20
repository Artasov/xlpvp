package com.xlpvp.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("xlpvp")
@PrefixGameTestTemplate(false)
public final class PlayerFireContactGameTests {
    @GameTest(template = "empty")
    public static void briefContactIgnitesBothFireTypes(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = test.absolutePos(new BlockPos(1, 1, 1));
        for (var fire : new net.minecraft.world.level.block.Block[]{Blocks.FIRE, Blocks.SOUL_FIRE}) {
            player.setRemainingFireTicks(-20);
            fire.defaultBlockState().entityInside(test.getLevel(), pos, player);
            test.assertTrue(player.getRemainingFireTicks() == 160, "First contact must start the usual eight seconds of fire");
        }
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        player.setRemainingFireTicks(-20);
        Blocks.FIRE.defaultBlockState().entityInside(test.getLevel(), pos, player);
        test.assertTrue(player.getRemainingFireTicks() <= 0, "A brief touch ignited a fire-resistant player");
        test.succeed();
    }
}
