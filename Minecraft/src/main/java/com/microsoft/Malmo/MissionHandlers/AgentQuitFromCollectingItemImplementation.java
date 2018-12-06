package com.microsoft.Malmo.MissionHandlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.microsoft.Malmo.MissionHandlerInterfaces.IWantToQuit;
import com.microsoft.Malmo.MissionHandlers.AgentQuitFromCraftingItemImplementation.ItemQuitMatcher;
import com.microsoft.Malmo.MissionHandlers.RewardForCollectingItemImplementation.GainItemEvent;
import com.microsoft.Malmo.MissionHandlers.RewardForItemBase.ItemMatcher;
import com.microsoft.Malmo.Schemas.AgentQuitFromCollectingItem;
import com.microsoft.Malmo.Schemas.BlockOrItemSpecWithDescription;
import com.microsoft.Malmo.Schemas.MissionInit;

public class AgentQuitFromCollectingItemImplementation extends HandlerBase implements IWantToQuit {
	AgentQuitFromCollectingItem params;
	private HashMap<String, Integer> collectedItems;
	List<ItemQuitMatcher> matchers;
	String quitCode = "";
	boolean wantToQuit = false;
	boolean callCraft = true;

	public static class ItemQuitMatcher extends RewardForItemBase.ItemMatcher {
		String description;

		ItemQuitMatcher(BlockOrItemSpecWithDescription spec) {
			super(spec);
			this.description = spec.getDescription();
		}

		String description() {
			return this.description;
		}
	}

	@Override
	public boolean parseParameters(Object params) {
		if (params == null || !(params instanceof AgentQuitFromCollectingItem))
			return false;

		this.params = (AgentQuitFromCollectingItem) params;
		this.matchers = new ArrayList<ItemQuitMatcher>();
		for (BlockOrItemSpecWithDescription bs : this.params.getItem())
			this.matchers.add(new ItemQuitMatcher(bs));
		return true;
	}

	@Override
	public boolean doIWantToQuit(MissionInit missionInit) {
		return this.wantToQuit;
	}

	@Override
	public String getOutcome() {
		return this.quitCode;
	}

	@Override
	public void prepare(MissionInit missionInit) {
		MinecraftForge.EVENT_BUS.register(this);
		collectedItems = new HashMap<String, Integer>();
	}

	@Override
	public void cleanup() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onGainItem(GainItemEvent event) {
		System.out.println("Gained an item: " + event.stack.getUnlocalizedName());
		checkForMatch(event.stack);
	}

	@SubscribeEvent
	public void onPickupItem(EntityItemPickupEvent event) {
		if (event.getItem() != null && event.getItem().getEntityItem() != null) {
			ItemStack stack = event.getItem().getEntityItem();
			checkForMatch(stack);
		}
	}

	@SubscribeEvent
	public void onItemCraft(PlayerEvent.ItemCraftedEvent event) {
		if (callCraft)
			checkForMatch(event.crafting);

		callCraft = !callCraft;
	}

	private boolean getVariant(ItemStack is) {
		for (ItemMatcher matcher : matchers) {
			if (matcher.allowedItemTypes.contains(is.getItem().getUnlocalizedName())) {
				if (matcher.matchSpec.getColour() != null && matcher.matchSpec.getColour().size() > 0)
					return true;
				if (matcher.matchSpec.getVariant() != null && matcher.matchSpec.getVariant().size() > 0)
					return true;
			}
		}

		return false;
	}

	private int getCraftedItemCount(ItemStack is) {
		boolean variant = getVariant(is);

		if (variant)
			return (collectedItems.get(is.getUnlocalizedName()) == null) ? 0
					: collectedItems.get(is.getUnlocalizedName());
		else
			return (collectedItems.get(is.getItem().getUnlocalizedName()) == null) ? 0
					: collectedItems.get(is.getItem().getUnlocalizedName());

	}

	private void addCollectedItemCount(ItemStack is) {
		boolean variant = getVariant(is);

		if (variant) {
			int prev = (collectedItems.get(is.getUnlocalizedName()) == null ? 0
					: collectedItems.get(is.getUnlocalizedName()));
			collectedItems.put(is.getUnlocalizedName(), prev + is.getCount());
		} else {
			int prev = (collectedItems.get(is.getItem().getUnlocalizedName()) == null ? 0
					: collectedItems.get(is.getItem().getUnlocalizedName()));
			collectedItems.put(is.getItem().getUnlocalizedName(), prev + is.getCount());
		}
	}

	private void checkForMatch(ItemStack is) {
		int savedCrafted = getCraftedItemCount(is);
		if (is != null && is.getItem() != null) {
			for (ItemQuitMatcher matcher : this.matchers) {
				if (matcher.matches(is)) {
					if (savedCrafted != 0) {
						if (is.getCount() + savedCrafted >= matcher.matchSpec.getAmount()) {
							this.quitCode = matcher.description();
							this.wantToQuit = true;
						}
					} else if (is.getCount() >= matcher.matchSpec.getAmount()) {
						this.quitCode = matcher.description();
						this.wantToQuit = true;
					}
				}
			}

			addCollectedItemCount(is);
		}
	}
}
