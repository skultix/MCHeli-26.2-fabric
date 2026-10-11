package mcheli.dependent.registry;

import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * A registered object + its id. Fabric registers eagerly (no DeferredRegister), but keeping a {@code Supplier}-shaped
 * holder keeps the NeoForge-era {@code .get()} call sites unchanged across the port.
 */
public final class MchHolder<T> implements Supplier<T> {
    private final Identifier id;
    private final T value;

    MchHolder(Identifier id, T value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }

    public Identifier getId() {
        return id;
    }
}
