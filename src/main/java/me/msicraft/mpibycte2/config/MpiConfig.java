package me.msicraft.mpibycte2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MpiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("mpi_config.json");

    private static final List<String> DEFAULT_KEYS = List.of(
            "common", "uncommon", "rare", "epic", "legendary", "mythic"
    );
    private static final Map<String, Double> DEFAULT_WEIGHTS;
    static {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("common", 35.0);
        m.put("uncommon", 40.0);
        m.put("rare", 25.0);
        m.put("epic", 10.0);
        m.put("legendary", 0.1);
        m.put("mythic", 0.001);
        DEFAULT_WEIGHTS = Collections.unmodifiableMap(m);
    }

    private static Map<String, Map<String, Double>> rarityTables = new LinkedHashMap<>();
    private static Map<String, NavigableMap<Double, String>> cumulativeMaps = new HashMap<>();
    private static String defaultTableId = null;

    private static double defaultGearDamagePercent = 0.2;
    private static double defaultWarningThreshold = 0.10;
    private static double gearDamagePercent = defaultGearDamagePercent;
    private static double warningThreshold = defaultWarningThreshold;

    public static void loadConfig() {
        rarityTables.clear();
        cumulativeMaps.clear();
        try {
            File file = CONFIG_PATH.toFile();
            if (!file.exists()) {
                rarityTables.put("0", new LinkedHashMap<>(DEFAULT_WEIGHTS));
                defaultTableId = "0";
                buildCumulativeMap("0");
                gearDamagePercent = defaultGearDamagePercent;
                warningThreshold = defaultWarningThreshold;
                saveConfig();
                return;
            }
            JsonObject root = GSON.fromJson(Files.newBufferedReader(CONFIG_PATH), JsonObject.class);
            if (root == null || !root.isJsonObject()) {
                rarityTables.put("0", new LinkedHashMap<>(DEFAULT_WEIGHTS));
                defaultTableId = "0";
                buildCumulativeMap("0");
                gearDamagePercent = defaultGearDamagePercent;
                warningThreshold = defaultWarningThreshold;
                saveConfig();
                return;
            }
            JsonObject rarityWeightsObj = root.has("RarityWeights") && root.get("RarityWeights").isJsonObject()
                    ? root.getAsJsonObject("RarityWeights") : null;
            if (rarityWeightsObj == null) {
                rarityTables.put("0", new LinkedHashMap<>(DEFAULT_WEIGHTS));
            } else {
                for (Map.Entry<String, JsonElement> entry : rarityWeightsObj.entrySet()) {
                    String tableId = entry.getKey();
                    JsonElement elem = entry.getValue();
                    if (!elem.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = elem.getAsJsonObject();
                    Map<String, Double> weightMap = new LinkedHashMap<>();
                    for (String key : DEFAULT_KEYS) {
                        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                            try {
                                double v = obj.get(key).getAsDouble();
                                if (v >= 0) {
                                    weightMap.put(key, v);
                                    continue;
                                }
                            } catch (Exception ex) {}
                        }
                        weightMap.put(key, DEFAULT_WEIGHTS.get(key));
                    }
                    rarityTables.put(tableId, weightMap);
                }
            }
            if (rarityTables.isEmpty()) {
                rarityTables.put("0", new LinkedHashMap<>(DEFAULT_WEIGHTS));
            }
            if (rarityTables.containsKey("0")) {
                defaultTableId = "0";
            } else {
                defaultTableId = rarityTables.keySet().iterator().next();
            }
            for (String tableId : rarityTables.keySet()) {
                buildCumulativeMap(tableId);
            }
            if (root.has("Settings") && root.get("Settings").isJsonObject()) {
                JsonObject es = root.getAsJsonObject("Settings");
                // gearDamagePercent
                if (es.has("gearDamagePercent") && es.get("gearDamagePercent").isJsonPrimitive()) {
                    try {
                        double v = es.get("gearDamagePercent").getAsDouble();
                        if (v >= 0 && v <= 1) {
                            gearDamagePercent = v;
                        } else {
                            gearDamagePercent = defaultGearDamagePercent;
                        }
                    } catch (Exception ex) {
                        gearDamagePercent = defaultGearDamagePercent;
                    }
                } else {
                    gearDamagePercent = defaultGearDamagePercent;
                }
                // warningThreshold
                if (es.has("warningThreshold") && es.get("warningThreshold").isJsonPrimitive()) {
                    try {
                        double v = es.get("warningThreshold").getAsDouble();
                        if (v >= 0 && v <= 1) {
                            warningThreshold = v;
                        } else {
                            warningThreshold = defaultWarningThreshold;
                        }
                    } catch (Exception ex) {
                        warningThreshold = defaultWarningThreshold;
                    }
                } else {
                    warningThreshold = defaultWarningThreshold;
                }
            } else {
                gearDamagePercent = defaultGearDamagePercent;
                warningThreshold = defaultWarningThreshold;
            }
        } catch (Exception e) {
            rarityTables.clear();
            cumulativeMaps.clear();
            rarityTables.put("0", new LinkedHashMap<>(DEFAULT_WEIGHTS));
            defaultTableId = "0";
            buildCumulativeMap("0");
            gearDamagePercent = defaultGearDamagePercent;
            warningThreshold = defaultWarningThreshold;
            saveConfig();
            System.err.println("[MpiConfig] 설정 로드 중 오류, 기본값으로 초기화");
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            JsonObject rarityWeightsObj = new JsonObject();
            for (Map.Entry<String, Map<String, Double>> tableEntry : rarityTables.entrySet()) {
                String tableId = tableEntry.getKey();
                JsonObject obj = new JsonObject();
                Map<String, Double> weightMap = tableEntry.getValue();
                for (String key : DEFAULT_KEYS) {
                    Double v = weightMap.get(key);
                    if (v != null) obj.addProperty(key, v);
                }
                rarityWeightsObj.add(tableId, obj);
            }
            root.add("RarityWeights", rarityWeightsObj);
            JsonObject es = new JsonObject();
            es.addProperty("gearDamagePercent", gearDamagePercent);
            es.addProperty("warningThreshold", warningThreshold);
            root.add("Settings", es);
            File file = CONFIG_PATH.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.err.println("[MpiConfig] 설정 저장 중 오류");
            e.printStackTrace();
        }
    }

    private static void buildCumulativeMap(String tableId) {
        Map<String, Double> weightMap = rarityTables.get(tableId);
        NavigableMap<Double, String> map = new TreeMap<>();
        double total = 0.0;
        for (String key : DEFAULT_KEYS) {
            double w = weightMap.getOrDefault(key, 0.0);
            if (w <= 0) continue;
            total += w;
            map.put(total, key);
        }
        if (map.isEmpty()) {
            total = 0.0;
            for (String key : DEFAULT_KEYS) {
                double w = DEFAULT_WEIGHTS.get(key);
                total += w;
                map.put(total, key);
            }
        }
        cumulativeMaps.put(tableId, map);
    }

    public static Set<String> getTableIds() {
        return Collections.unmodifiableSet(rarityTables.keySet());
    }

    public static Map<String, Double> getRarityWeights(String tableId) {
        Map<String, Double> m = rarityTables.get(tableId);
        if (m == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(m);
    }

    public static String getDefaultTableId() {
        return defaultTableId;
    }

    public static String pickRarityWeighted(Random random, String tableId) {
        NavigableMap<Double, String> map = cumulativeMaps.get(tableId);
        if (map == null) {
            map = cumulativeMaps.get(defaultTableId);
        }
        double totalWeight = map.lastKey();
        double r = random.nextDouble() * totalWeight;
        Map.Entry<Double, String> entry = map.higherEntry(r);
        if (entry != null) {
            return entry.getValue();
        }
        return map.lastEntry().getValue();
    }

    public static boolean setRarityWeight(String tableId, String rarity, double weight) {
        Map<String, Double> weightMap = rarityTables.get(tableId);
        if (weightMap == null) return false;
        if (!DEFAULT_KEYS.contains(rarity) || weight < 0) return false;
        weightMap.put(rarity, weight);
        buildCumulativeMap(tableId);
        saveConfig();
        return true;
    }

    public static boolean addTable(String tableId) {
        if (rarityTables.containsKey(tableId)) return false;
        rarityTables.put(tableId, new LinkedHashMap<>(DEFAULT_WEIGHTS));
        buildCumulativeMap(tableId);
        saveConfig();
        return true;
    }

    public static boolean removeTable(String tableId) {
        if (!rarityTables.containsKey(tableId)) return false;
        if (tableId.equals(defaultTableId)) return false;
        rarityTables.remove(tableId);
        cumulativeMaps.remove(tableId);
        saveConfig();
        return true;
    }

    public static double getGearDamagePercent() {
        return gearDamagePercent;
    }
    public static boolean setGearDamagePercent(double v) {
        if (v < 0 || v > 1) return false;
        gearDamagePercent = v;
        saveConfig();
        return true;
    }

    public static double getWarningThreshold() {
        return warningThreshold;
    }
    public static boolean setWarningThreshold(double v) {
        if (v < 0 || v > 1) return false;
        warningThreshold = v;
        saveConfig();
        return true;
    }

    public static void reloadConfig() {
        loadConfig();
    }

    public static Map<String, String> getRarityProbabilities(String tableId) {
        Map<String, Double> weights = rarityTables.get(tableId);
        if (weights == null || weights.isEmpty()) return Map.of();

        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double prob = (entry.getValue() / total) * 100.0;
            result.put(entry.getKey(), String.format("weight=%.2f, prob=%.2f%%", entry.getValue(), prob));
        }
        return result;
    }

}

