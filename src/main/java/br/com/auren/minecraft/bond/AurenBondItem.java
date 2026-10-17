package br.com.auren.minecraft.bond;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Cosmetic one-of-one token: it contains no dragon state, entity ID or
 * redeemable value. Only the SavedData owner/dragon record can deploy Auren.
 */
public final class AurenBondItem extends Item {
    public AurenBondItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user instanceof ServerPlayer player)
            AurenBondService.deploy(player);
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player)
            AurenBondService.deploy(player);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }
}
