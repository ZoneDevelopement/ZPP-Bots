package dev.nandi0813.zppbots.ai.mace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitalStrikeControllerTest {

    @Test
    void extractsCustomDataFromComponentString() {
        String components = "[minecraft:custom_name='\"Rod\"',minecraft:custom_data={OrbitalMode:\"nuke\",nested:{a:1,b:\"x}\"}}]";
        String snbt = OrbitalStrikeController.customDataSnbt(components);
        assertEquals("{OrbitalMode:\"nuke\",nested:{a:1,b:\"x}\"}}", snbt);
        Map<String, String> tag = OrbitalStrikeController.parseCompound(snbt);
        assertEquals(2, tag.size());
        assertEquals("nuke", OrbitalStrikeController.orbitalPlusMode(tag));
    }

    @Test
    void missingOrUnknownModeIsNull() {
        assertNull(OrbitalStrikeController.customDataSnbt("[minecraft:damage=3]"));
        assertTrue(OrbitalStrikeController.parseCompound(null).isEmpty());
        assertNull(OrbitalStrikeController.orbitalPlusMode(OrbitalStrikeController.parseCompound("{OrbitalMode:\"other\"}")));
        assertNull(OrbitalStrikeController.orbitalPlusMode(Map.of()));
    }

    @Test
    void quotedKeysAndCannonNames() {
        Map<String, String> tag = OrbitalStrikeController.parseCompound("{\"Orbital_Cannon\":1b,stab_x:2}");
        assertTrue(tag.containsKey("Orbital_Cannon"));
        assertTrue(tag.containsKey("stab_x"));
        assertEquals("Stab Shot", OrbitalStrikeController.cannonName("Stab Shot"));
        assertNull(OrbitalStrikeController.cannonName("Something"));
    }
}
