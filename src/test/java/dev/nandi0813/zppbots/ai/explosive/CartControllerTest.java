package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartControllerTest {

    @Test
    void instaDrawTicksScaleWithDistanceAndClamp() {
        assertEquals(4, CartController.instaDrawTicks(0.0));
        assertEquals(8, CartController.instaDrawTicks(3.6));
        assertEquals(12, CartController.instaDrawTicks(20.0));
    }

    @Test
    void placementNeedsSpotNearTargetOutsideBlastAndInReach() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertTrue(CartController.placementInRange(profile, 1.0, 4.0, 3.0));
        assertFalse(CartController.placementInRange(profile, 2.6, 4.0, 3.0));
        assertFalse(CartController.placementInRange(profile, 1.0, 2.9, 3.0));
        assertFalse(CartController.placementInRange(profile, 1.0, 4.6, 3.0));
    }

    @Test
    void changedTargetMaxWidensPlacement() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.CART_TARGET_MAX, 3.0);
        assertTrue(CartController.placementInRange(profile(root), 2.6, 4.0, 3.0));
    }

    @Test
    void cartMaxDistMustStayWithinBounds() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.CART_MAX_DIST, 65);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    @Test
    void fireMustSitOnTheLineToTheCart() {
        Vec3 eye = new Vec3(0.5, 1.5, 0.5);
        Vec3 cart = new Vec3(0.5, 1.5, 4.5);
        assertTrue(CartController.fireIsOnShotLine(eye, cart, new BlockPos(0, 1, 2)));
        assertFalse(CartController.fireIsOnShotLine(eye, cart, new BlockPos(3, 1, 2)));
        assertFalse(CartController.fireIsOnShotLine(eye, null, new BlockPos(0, 1, 2)));
    }

    @Test
    void bowCoolingSpacesFlameBowCarts() {
        assertTrue(CartController.bowCooling(BotSettings.CartMode.PRE_CART, 100, 80, 40));
        assertFalse(CartController.bowCooling(BotSettings.CartMode.PRE_CART, 120, 80, 40));
        assertFalse(CartController.bowCooling(BotSettings.CartMode.XBOW_CART, 100, 80, 40));
        assertFalse(CartController.bowCooling(BotSettings.CartMode.INSTA_CART, 100, Integer.MIN_VALUE / 2, 40));
    }

    @Test
    void railMaterialsAndBackAwayTick() {
        assertTrue(CartController.isRailMaterial(Material.POWERED_RAIL));
        assertFalse(CartController.isRailMaterial(Material.TNT_MINECART));
        assertEquals(9, CartController.lastPlacementTick(BotSettings.CartMode.XBOW_CART));
        assertEquals(0, CartController.lastPlacementTick(BotSettings.CartMode.PRE_CART));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
