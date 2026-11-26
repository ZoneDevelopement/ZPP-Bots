package dev.nandi0813.zppbots.ai.gear;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Carry targets per item; one instance per squad instead of static maps. Main thread only. */
public final class RestockPlan {

    private final Map<Material, Integer> global = new HashMap<>();
    private final Map<String, Map<Material, Integer>> teams = new HashMap<>();

    public int carryTarget(String teamName, ItemStack stack) {
        Material item = stack.getType();
        if (teamName != null && !teamName.isBlank()) {
            Map<Material, Integer> t = teams.get(teamName.toLowerCase(Locale.ROOT));
            if (t != null) {
                Integer v = t.get(item);
                if (v != null) {
                    return v;
                }
            }
        }

        Integer g = global.get(item);
        return g == null ? -1 : g;
    }

    public void set(String teamName, Material item, int count) {
        Map<Material, Integer> target = teamName != null && !teamName.isBlank()
                ? teams.computeIfAbsent(teamName.toLowerCase(Locale.ROOT), k -> new HashMap<>())
                : global;
        if (count <= 0) {
            target.remove(item);
        } else {
            target.put(item, count);
        }
    }

    public void clear(String teamName) {
        if (teamName != null && !teamName.isBlank()) {
            teams.remove(teamName.toLowerCase(Locale.ROOT));
        } else {
            global.clear();
        }
    }

    public Map<String, Integer> view(String teamName) {
        Map<Material, Integer> src = teamName != null && !teamName.isBlank()
                ? teams.getOrDefault(teamName.toLowerCase(Locale.ROOT), Map.of())
                : global;
        Map<String, Integer> out = new LinkedHashMap<>();
        src.forEach((item, n) -> out.put(item.getKey().toString(), n));
        return out;
    }

    public void load(String teamName, Map<String, Integer> saved) {
        clear(teamName);
        saved.forEach((id, n) -> {
            Material item = resolve(id);
            if (item != null) {
                set(teamName, item, n);
            }
        });
    }

    public static Material resolve(String id) {
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        Material material = Material.getMaterial(path.toUpperCase(Locale.ROOT));
        return material != null && material.isItem() ? material : null;
    }
}
