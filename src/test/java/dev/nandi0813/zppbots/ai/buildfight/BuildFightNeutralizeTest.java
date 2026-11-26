package dev.nandi0813.zppbots.ai.buildfight;

import dev.nandi0813.zppbots.mc.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BuildFightNeutralizeTest {

    @Test
    void ownSourcesAreNotForeign() {
        BlockPos water = new BlockPos(1, 100, 1);
        BlockPos lava = new BlockPos(2, 100, 2);
        assertFalse(BuildFightController.foreign(water, water, lava));
        assertFalse(BuildFightController.foreign(lava, water, lava));
        assertTrue(BuildFightController.foreign(new BlockPos(3, 100, 3), water, lava));
        assertTrue(BuildFightController.foreign(new BlockPos(3, 100, 3), null, null));
    }
}
