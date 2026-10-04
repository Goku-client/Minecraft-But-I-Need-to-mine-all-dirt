package com.dirtworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DirtWorldMod implements ModInitializer {
	public static final String MOD_ID = "dirtworld";
	private static final String STARTED_TAG = "dirtworld_started";
	private static final Map<UUID, Long> LAST_PORTAL = new HashMap<>();

	@Override
	public void onInitialize() {
		ModContent.register();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			entries.accept(ModContent.DIRT_TOKEN);
			entries.accept(ModContent.SHOP_PORTAL_ITEM);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Clearing.tick(server);
			ShopWorld.tick(server);
		});

		// Starter Shop Portal on first join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (!p.getTags().contains(STARTED_TAG)) {
				p.addTag(STARTED_TAG);
				p.getInventory().placeItemBackInInventory(new ItemStack(ModContent.SHOP_PORTAL_ITEM));
				p.sendSystemMessage(Component.literal(
						"Mine DIRT and GRASS to earn Dirt Tokens! Place your Shop Portal anywhere and right-click it to visit the shop.")
						.withStyle(ChatFormatting.GOLD));
			}
		});

		// /shopportal  (get a new portal if you lost yours)   /dirtbank  (see your balance)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("shopportal").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				if (hasPortal(p)) {
					p.sendSystemMessage(Component.literal("You already have a Shop Portal.").withStyle(ChatFormatting.YELLOW));
				} else {
					p.getInventory().placeItemBackInInventory(new ItemStack(ModContent.SHOP_PORTAL_ITEM));
					p.sendSystemMessage(Component.literal("Here is a new Shop Portal.").withStyle(ChatFormatting.GREEN));
				}
				return 1;
			}));
			dispatcher.register(Commands.literal("dirtbank").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				p.sendSystemMessage(Component.literal("Bank: " + String.format("%,d", Bank.get(p))
						+ " tokens (+ any tokens in your inventory)").withStyle(ChatFormatting.GOLD));
				return 1;
			}));
		});

		// Mining dirt/grass gives a Dirt Token instead of the block. The shop room can't be broken.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return true;
			if (ShopWorld.inside(level, pos)) return sp.isCreative();
			if (sp.isCreative() || sp.isSpectator()) return true;
			if (!Clearing.isDirt(state)) return true;

			level.destroyBlock(pos, false, sp);
			ItemStack tool = sp.getMainHandItem();
			if (!tool.isEmpty()) tool.hurtAndBreak(1, sp, EquipmentSlot.MAINHAND);
			sp.getInventory().placeItemBackInInventory(new ItemStack(ModContent.DIRT_TOKEN));
			sp.displayClientMessage(Component.literal("+1 Dirt Token").withStyle(ChatFormatting.GOLD), true);
			return false;
		});

		// Right-click the Shop Portal block to go to / leave the shop (sneak to build against it instead).
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			if (!world.getBlockState(pos).is(ModContent.SHOP_PORTAL)) return InteractionResult.PASS;
			if (player.isShiftKeyDown()) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

			long now = level.getGameTime();
			if (now - LAST_PORTAL.getOrDefault(sp.getUUID(), -100L) < 20) return InteractionResult.SUCCESS;
			LAST_PORTAL.put(sp.getUUID(), now);

			if (ShopWorld.inside(level, pos)) {
				ShopWorld.exit(level, sp);
			} else {
				ShopWorld.enter(level, sp, pos);
			}
			return InteractionResult.SUCCESS;
		});

		// Right-click the Dirt Merchant to open the shop.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!ShopWorld.isKeeper(entity)) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
			if (!ShopWorld.inside(level, entity.blockPosition())) return InteractionResult.PASS;
			sp.openMenu(new SimpleMenuProvider(
					(id, inv, p) -> ShopMenu.create(id, inv, sp),
					Component.literal("Dirt Shop")));
			return InteractionResult.SUCCESS;
		});

		// Using one of the dirt-erasing items.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			String key = DirtTools.toolKey(stack);
			if (key == null) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
			return DirtTools.use(level, sp, stack, key);
		});
	}

	private static boolean hasPortal(Player p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ModContent.SHOP_PORTAL_ITEM)) return true;
		}
		return false;
	}
}
