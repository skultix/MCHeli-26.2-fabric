package mcheli.dependent.item;

import java.util.function.Consumer;
import mcheli.agnostic.weapon.MCH_AmmoCaliber;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A vehicle ammo item. A calibre item ({@link MCH_AmmoCaliber}, one per gun calibre the weapon configs use) is named
 * from its calibre ("20mm Rounds", "105mm Shell") and says what it holds; the shared items (missiles, rockets, bombs,
 * torpedoes) use their own {@code item.mcheli.<id>} name and {@code .desc} line.
 */
public class MchAmmoItem extends Item {
    private final MCH_AmmoCaliber caliber;

    public MchAmmoItem(MCH_AmmoCaliber caliber, Properties props) {
        super(props);
        this.caliber = caliber;
    }

    /** The calibre this item holds rounds of, or null for a shared item. */
    public MCH_AmmoCaliber caliber() {
        return this.caliber;
    }

    @Override
    public Component getName(ItemStack stack) {
        return this.caliber == null ? super.getName(stack)
            : Component.translatable(this.caliber.nameKey(), this.caliber.label());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        Component desc = this.caliber == null ? Component.translatable(getDescriptionId() + ".desc")
            : Component.translatable(this.caliber.descKey(), this.caliber.descArgs());
        lines.accept(desc.copy().withStyle(ChatFormatting.GRAY));
    }
}
