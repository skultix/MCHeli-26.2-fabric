package mcheli.dependent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.weapon.MCH_DamageSettings;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * {@code config/mcheli-damage.json}: the server's damage tuning for MCHeli weapons ({@link MCH_DamageSettings}).
 * Written with neutral values on first start; {@code /mcheli damage reload} re-reads it without a restart. Every
 * weapon's unmodified config values are rewritten to {@code config/mcheli-damage-defaults.json} on each load, as a
 * reference to copy overrides from. See {@code docs/configuring-damage.md}.
 */
public final class MchDamageConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE = "mcheli-damage.json";
    public static final String DEFAULTS_FILE = "mcheli-damage-defaults.json";

    private static volatile MCH_DamageSettings settings = MCH_DamageSettings.defaults();

    private MchDamageConfig() {}

    public static MCH_DamageSettings settings() {
        return settings;
    }

    /** Reads (or creates) the config and applies it to the loaded weapons. Returns the problems found, for the command. */
    public static List<String> load() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve(FILE);
        List<String> problems = new ArrayList<>();
        MCH_DamageSettings s;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                s = GSON.fromJson(r, MCH_DamageSettings.class);
                if (s == null) {
                    s = MCH_DamageSettings.defaults();
                }
            } catch (Exception e) {
                // Keep the user's broken file for them to fix, and keep the previous settings meanwhile.
                problems.add(FILE + " could not be read (" + e.getMessage() + "); keeping the previous settings");
                problems.forEach(p -> LOGGER.error("MCHeli damage config: {}", p));
                return problems;
            }
        } else {
            s = MCH_DamageSettings.defaults();
            write(file, GSON.toJson(s));
        }
        problems.addAll(s.validate());
        problems.forEach(p -> LOGGER.warn("MCHeli damage config: {}", p));
        List<MCH_WeaponInfo> weapons = weapons();
        s.apply(weapons);
        settings = s;
        writeDefaults(dir.resolve(DEFAULTS_FILE), weapons);
        LOGGER.info("MCHeli damage config: loaded {} ({} weapon override(s))", file, s.weapons.size());
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static List<MCH_WeaponInfo> weapons() {
        List<MCH_WeaponInfo> out = new ArrayList<>();
        for (Object o : ((Map<String, Object>) MCH_WeaponInfoManager.getInstance().getMap()).values()) {
            if (o instanceof MCH_WeaponInfo wi) {
                out.add(wi);
            }
        }
        out.sort(Comparator.comparing(wi -> wi.name));
        return out;
    }

    /** Every loaded weapon's config-file damage values, shaped like a {@code weapons} entry plus its type. */
    private static void writeDefaults(Path file, List<MCH_WeaponInfo> weapons) {
        JsonObject root = new JsonObject();
        root.addProperty("_readme", "Generated on every load; edits here are ignored. These are each weapon's own "
            + "config values. Copy an entry into \"weapons\" in " + FILE + " to override it.");
        JsonObject all = new JsonObject();
        for (MCH_WeaponInfo wi : weapons) {
            MCH_DamageSettings.Original o = MCH_DamageSettings.original(wi);
            JsonObject w = new JsonObject();
            w.addProperty("type", wi.type);
            w.addProperty("power", o.power);
            w.addProperty("explosion", o.explosion);
            w.addProperty("explosionBlock", o.explosionBlock);
            w.addProperty("explosionInWater", o.explosionInWater);
            if (o.damageFactor != null && !o.damageFactor.entries().isEmpty()) {
                JsonObject f = new JsonObject();
                o.damageFactor.entries().forEach((role, v) -> f.addProperty(MCH_DamageSettings.targetName(role), v));
                w.add("damageFactor", f);
            }
            all.add(wi.name, w);
        }
        root.add("weapons", all);
        write(file, GSON.toJson(root));
    }

    private static void write(Path file, String json) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(json);
                w.write('\n');
            }
        } catch (IOException e) {
            LOGGER.error("MCHeli damage config: could not write {}", file, e);
        }
    }

    /** {@code /mcheli damage reload} (operators). */
    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
            Commands.literal("mcheli")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("damage").then(Commands.literal("reload").executes(c -> {
                    List<String> problems = load();
                    for (String p : problems) {
                        c.getSource().sendFailure(Component.literal(p));
                    }
                    c.getSource().sendSuccess(() -> Component.literal("Reloaded " + FILE
                        + (problems.isEmpty() ? "" : " with " + problems.size() + " problem(s)")), true);
                    return problems.isEmpty() ? 1 : 0;
                })))));
    }
}
