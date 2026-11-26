package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.mc.BlockPos;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class FireEscapeControllerTest {

    private static final BlockPos FEET = new BlockPos(0, 64, 0);

    @Test
    void nearestExitPicksClosestClearColumnWithSolidGround() {
        Set<BlockPos> lava = new HashSet<>();
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                solid.add(new BlockPos(x, 63, z));
                lava.add(new BlockPos(x, 64, z));
                lava.add(new BlockPos(x, 65, z));
            }
        }
        assertNull(FireEscapeController.nearestExit(FEET, lava::contains, solid::contains));

        lava.remove(new BlockPos(2, 64, 0));
        lava.remove(new BlockPos(2, 65, 0));
        assertEquals(new BlockPos(2, 64, 0), FireEscapeController.nearestExit(FEET, lava::contains, solid::contains));

        lava.remove(new BlockPos(1, 64, 1));
        lava.remove(new BlockPos(1, 65, 1));
        assertEquals(new BlockPos(1, 64, 1), FireEscapeController.nearestExit(FEET, lava::contains, solid::contains));

        lava.add(new BlockPos(1, 63, 1));
        solid.remove(new BlockPos(1, 63, 1));
        assertEquals(new BlockPos(2, 64, 0), FireEscapeController.nearestExit(FEET, lava::contains, solid::contains));
    }
}
