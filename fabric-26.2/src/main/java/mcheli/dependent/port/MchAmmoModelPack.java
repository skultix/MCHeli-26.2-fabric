package mcheli.dependent.port;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import mcheli.MCHeli;
import mcheli.agnostic.weapon.MCH_AmmoCaliber;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A synthesized CLIENT_RESOURCES pack serving the client item definition ({@code assets/mcheli/items/<id>.json}) of
 * every calibre ammo item. Which calibres exist is only known once the weapon configs (content packs included) are
 * parsed, so the definitions are generated: each points at the shared sprite model for its size
 * ({@link MCH_AmmoCaliber#texture()}, {@code models/item/ammo_*.json}).
 */
public final class MchAmmoModelPack extends AbstractPackResources {
    private static final String PACK_FORMAT =
        MchPackResources.formatJson(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES));
    private static final String PREFIX = "items/";

    public MchAmmoModelPack(PackLocationInfo location) {
        super(location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PACK_META.equals(path[0])) {
            byte[] meta = bytes("{\"pack\":{\"description\":\"MCHeli ammo items\"," + PACK_FORMAT + "}}");
            return () -> new ByteArrayInputStream(meta);
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier loc) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(loc.getNamespace())) {
            return null;
        }
        String path = loc.getPath();
        if (!path.startsWith(PREFIX) || !path.endsWith(".json")) {
            return null;
        }
        String name = path.substring(PREFIX.length(), path.length() - ".json".length());
        for (MCH_AmmoCaliber cal : MchRegistries.caliberAmmo().keySet()) {
            if (cal.itemPath().equals(name)) {
                byte[] data = bytes(json(cal));
                return () -> new ByteArrayInputStream(data);
            }
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(namespace)
            || !(PREFIX.startsWith(path) || path.startsWith(PREFIX))) {
            return;
        }
        for (MCH_AmmoCaliber cal : MchRegistries.caliberAmmo().keySet()) {
            Identifier id = Identifier.tryBuild(namespace, PREFIX + cal.itemPath() + ".json");
            if (id != null) {
                byte[] data = bytes(json(cal));
                out.accept(id, () -> new ByteArrayInputStream(data));
            }
        }
    }

    private static String json(MCH_AmmoCaliber cal) {
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mcheli:item/" + cal.texture() + "\"}}";
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(MCHeli.MODID) : Set.of();
    }

    @Override
    public void close() {
        // nothing to release: every entry is an in-memory string
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
