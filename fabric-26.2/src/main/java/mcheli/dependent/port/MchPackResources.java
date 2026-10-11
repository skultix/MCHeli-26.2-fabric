package mcheli.dependent.port;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcheli.MCHeli;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A {@link net.minecraft.server.packs.PackResources} that exposes one content pack's CLIENT assets (textures + sound
 * {@code .ogg}s + a {@code sounds.json}) to the vanilla resource manager, mapping the {@code mcheli} namespace directly
 * to the pack's CONTENT ROOT. That root is whatever {@link MchContentPacks} detected — the pack dir itself for a FLAT
 * pack ({@code <pack>/textures/...}) or its {@code assets/mcheli/} sub-dir for a resource-pack-style pack — so BOTH
 * layouts serve identically here, without requiring the {@code assets/mcheli/} nesting {@code PathPackResources} needs.
 *
 * <p>{@code pack.mcmeta} and {@code sounds.json} are SYNTHESIZED in memory (no files written into the user's pack dir):
 * the mcmeta carries the running client-resource pack format (via {@link AbstractPackResources}'s
 * {@code getMetadataSection}, which reads our generated {@code getRootResource("pack.mcmeta")}); the sounds.json is
 * built from the pack's {@code sounds/*.ogg} basenames (mirroring the reference {@code MCH_SoundsJson}) unless the pack
 * ships its own. Only {@link PackType#CLIENT_RESOURCES}/{@code mcheli} is served — models + configs come through the
 * separate {@link mcheli.agnostic.spi.ResourceSource} channel, and the dedicated server needs none of this.
 */
public final class MchPackResources extends AbstractPackResources {

    // 26.x pack.mcmeta: min_format/max_format as [major, minor] (pack_format is gone).
    private static final String PACK_FORMAT = formatJson(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES));

    static String formatJson(net.minecraft.server.packs.metadata.pack.PackFormat f) {
        String v = "[" + f.major() + "," + f.minor() + "]";
        return "\"min_format\":" + v + ",\"max_format\":" + v;
    }

    private final Path contentRoot;
    private final String packName;
    private final List<String> soundBasenames;

    public MchPackResources(PackLocationInfo location, Path contentRoot, String packName, List<String> soundBasenames) {
        super(location);
        this.contentRoot = contentRoot.normalize();
        this.packName = packName;
        this.soundBasenames = soundBasenames;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PACK_META.equals(path[0])) {
            byte[] meta = bytes("{\"pack\":{\"description\":\"MCHeli content pack: " + this.packName
                + "\"," + PACK_FORMAT + "}}");
            return () -> new ByteArrayInputStream(meta);
        }
        Path p = resolveSafe(String.join("/", path));
        return p != null && Files.isRegularFile(p) ? IoSupplier.create(p) : null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier loc) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(loc.getNamespace())) {
            return null;
        }
        String path = loc.getPath();
        if ("sounds.json".equals(path)) {
            return soundsJsonSupplier();
        }
        IoSupplier<InputStream> item = itemDefinitionSupplier(path);
        if (item != null) {
            return item;
        }
        Path p = resolveSafe(path);
        return p != null && Files.isRegularFile(p) ? IoSupplier.create(p) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(namespace)) {
            return;
        }
        if ("items".equals(path) || "items/".equals(path)) {
            for (String name : vehicleItemNames()) {
                Identifier id = Identifier.tryBuild(namespace, "items/" + name + ".json");
                IoSupplier<InputStream> def = id != null ? itemDefinitionSupplier(id.getPath()) : null;
                if (def != null) {
                    out.accept(id, def);
                }
            }
        }
        Path base = resolveSafe(path);
        if (base == null || !Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).forEach(f -> {
                String rel = this.contentRoot.relativize(f).toString().replace('\\', '/');
                Identifier id = Identifier.tryBuild(namespace, rel);
                if (id != null) {
                    out.accept(id, IoSupplier.create(f));
                }
            });
        } catch (IOException ignored) {
            // an unreadable pack subtree yields no listed resources (never throws into the resource manager)
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(MCHeli.MODID) : Set.of();
    }

    @Override
    public void close() {
        // nothing to release (files are opened lazily per IoSupplier)
    }

    /** Serve the pack's own {@code sounds.json} if it ships one; otherwise synthesize one from the {@code .ogg}s. */
    private IoSupplier<InputStream> soundsJsonSupplier() {
        Path shipped = resolveSafe("sounds.json");
        if (shipped != null && Files.isRegularFile(shipped)) {
            return IoSupplier.create(shipped);
        }
        if (this.soundBasenames.isEmpty()) {
            return null;
        }
        byte[] json = bytes(this.soundBasenames.stream()
            .map(n -> "  \"" + n + "\": { \"sounds\": [\"mcheli:" + n + "\"] }")
            .collect(Collectors.joining(",\n", "{\n", "\n}\n")));
        return () -> new ByteArrayInputStream(json);
    }

    /** 26.x client item definitions: every pack vehicle that became a spawn item renders its 3D model icon through the
     *  {@code mcheli:vehicle} special model, exactly like a bundled vehicle's static {@code items/<name>.json}. A pack may
     *  ship its own definition file, which wins. */
    private IoSupplier<InputStream> itemDefinitionSupplier(String path) {
        if (!path.startsWith("items/") || !path.endsWith(".json")) {
            return null;
        }
        Path shipped = resolveSafe(path);
        if (shipped != null && Files.isRegularFile(shipped)) {
            return IoSupplier.create(shipped);
        }
        String name = path.substring("items/".length(), path.length() - ".json".length());
        if (!vehicleItemNames().contains(name)) {
            return null;
        }
        byte[] json = bytes("{\"model\":{\"type\":\"minecraft:special\",\"base\":\"mcheli:item/vehicle\","
            + "\"model\":{\"type\":\"mcheli:vehicle\"}}}");
        return () -> new ByteArrayInputStream(json);
    }

    /** Lower-cased config names in this pack's vehicle category dirs that registered as spawn items. */
    private Set<String> vehicleItemNames() {
        Set<String> out = new java.util.TreeSet<>();
        for (mcheli.dependent.registry.MchRegistries.Category cat : mcheli.dependent.registry.MchRegistries.Category.values()) {
            Path dir = resolveSafe(cat.dir);
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".txt"))
                    .map(f -> f.substring(0, f.length() - 4).toLowerCase(java.util.Locale.ROOT))
                    .filter(n -> mcheli.dependent.registry.MchRegistries.spawnItemFor(n) != null)
                    .forEach(out::add);
            } catch (IOException ignored) {
                // unreadable dir -> no synthesized definitions
            }
        }
        return out;
    }

    /** Resolve a namespace-relative path under the content root, rejecting any {@code ..} traversal outside it. */
    private Path resolveSafe(String rel) {
        Path p = this.contentRoot.resolve(rel).normalize();
        return p.startsWith(this.contentRoot) ? p : null;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
