package net.logandark.backpack

import net.logandark.backpack.component.BackpackInventoryComponent
import net.logandark.backpack.item.BackpackItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity

// This is our actual screen handler, the class that bridges the gap between GUI
// and Inventory. This is what gets opened on the *server side* - what gets
// opened on the *client side* is a vanilla GUI that displays our slots.
class BackpackScreenHandler(
	syncId: Int,
	playerInventory: PlayerInventory,

	// Keep this for later as a field
	@Suppress("MemberVisibilityCanBePrivate")
	val component: BackpackInventoryComponent
) : GenericContainerScreenHandler(
	// We need a ScreenHandlerType in order to instruct
	// GenericContainerScreenHandler what to look like. Here we have a helper
	// method that automatically calculates the right type to use depending on
	// how many slots the backpack inventory has, but you can easily hardcode
	// this in an actual mod.
	getTypeFromSlots(component.size())!!,

	// It needs to know its sync ID or else it won't know how to send packets to
	// the client!
	syncId,

	// Nothing interesting to see here.
	playerInventory,

	// Our BackpackInventoryComponent doubles as an Inventory, which is why it's
	// valid to pass it here.
	component,

	// If our inventory had 19 slots, how many rows would it have? Three! We're
	// basically doing ceil division here, but since Java doesn't support that
	// natively, we have to add eight before dividing by nine.
	(component.size() + 8) / 9
) {
	companion object {
		fun getTypeFromSlots(slots: Int): ScreenHandlerType<GenericContainerScreenHandler>? {
			// The vanilla client only supports inventories with a width of 9
			// and a height of up to 6. We could go through the trouble of
			// making our own custom screen handler with its own custom GUI, but
			// vanilla already provides perfectly usable classes.
			//
			// I believe that these are all intentionally present for modded
			// servers like Bukkit, but that doesn't mean that client mods can't
			// use them too!
			return when {
				slots <= 9  -> ScreenHandlerType.GENERIC_9X1
				slots <= 18 -> ScreenHandlerType.GENERIC_9X2
				slots <= 27 -> ScreenHandlerType.GENERIC_9X3
				slots <= 36 -> ScreenHandlerType.GENERIC_9X4
				slots <= 45 -> ScreenHandlerType.GENERIC_9X5
				slots <= 54 -> ScreenHandlerType.GENERIC_9X6
				else        -> null
			}
		}
	}

	// Go check out BackpackInventoryComponent for an explanation of how the
	// inventory itself works, then come back here!

	// Alright, you back now? Good.

	// We need to override the slot click in order to prevent people from taking
	// the backpack from its slot. Luckily, it's not too hard... it just
	// requires a bit of trial and error.
	override fun onSlotClick(
		slotId: Int,
		clickData: Int,
		actionType: SlotActionType,
		playerEntity: PlayerEntity
	): ItemStack {
		// Apparently, going off of this repo:
		// https://github.com/kwpugh/SimpleBackpack/blob/68385919f44ffcd4a59492f357119a161c022a17/src/main/java/com/kwpugh/simple_backpack/backpack/BackpackScreenHandler.java#L40
		// negative slotIds are used by networking internals. Negative slot IDs
		// aren't valid anyway.
		return if (slotId >= 0) {
			// See if the movement needs to be overridden due to touching the
			// backpack...
			overrideContainerMovement(slotId, clickData, actionType, playerEntity)
			// ... if not, see if we need to prevent them from placing another
			// backpack inside this one ...
				?: overrideBackpackPlacement(slotId, clickData, actionType, playerEntity)
				// ... and if everything seems fine, use the default behavior
				?: super.onSlotClick(slotId, clickData, actionType, playerEntity)
		} else {
			super.onSlotClick(slotId, clickData, actionType, playerEntity)
		}
	}

	// This method is used to forbid all forms of moving a currently-open
	// backpack.
	private fun overrideContainerMovement(
		slotId: Int,
		clickData: Int,
		actionType: SlotActionType,
		playerEntity: PlayerEntity
	): ItemStack? {
		val stack = getSlot(slotId).stack

		// First things first, if we're getting a click on the backpack
		// slot ...
		if (actionType != SlotActionType.CLONE && stack == component.stack) {
			// ... deny it immediately.

			// Contrary to the javadoc of this method (onSlotClick), we
			// *actually* need to return the *current* state of the slot.
			// ServerPlayNetworkHandler will detect when the client is wrong
			// and correct it for us.
			return when (actionType) {
				// The client is expecting the item to now be under its
				// cursor. Tell it no. Not sure why this needs to be EMPTY
				// instead of stack, but this works and that doesn't...
				SlotActionType.PICKUP      -> ItemStack.EMPTY

				// The client is expecting the item to be moved somewhere
				// else. Tell it that the item hasn't moved.
				SlotActionType.QUICK_MOVE  -> stack

				// There is a special case for SWAP below, but you can still
				// press a number key while hovering over the backpack. Tell
				// the client that the slot still hasn't changed.
				SlotActionType.SWAP        -> stack

				// The client is expecting the stack to be gone. Tell it
				// that it's wrong.
				SlotActionType.THROW       -> stack

				// The client is expecting to displace the item (I think).
				// Tell it that it failed.
				SlotActionType.QUICK_CRAFT -> stack

				// The client is expecting to pick up the item. It's still
				// there.
				SlotActionType.PICKUP_ALL  -> stack

				// I already accounted for CLONE, Kotlin...
				else                       -> null
			}
		}

		// Next up, if we are swapping ...
		if (actionType == SlotActionType.SWAP) {
			// ... with the backpack slot ...
			if (playerEntity.inventory.getStack(clickData) == component.stack) {
				// ... then deny it as well. Tell the client that the slot
				// it's trying to swap out has not changed.
				return component.stack
			}
		}

		// Things look fine.
		return null
	}

	// This method is used to forbid all forms of placing a backpack inside
	// another.
	private fun overrideBackpackPlacement(
		slotId: Int,
		clickData: Int,
		actionType: SlotActionType,
		playerEntity: PlayerEntity
	): ItemStack? {
		val slot = slots[slotId]

		return when (actionType) {
			SlotActionType.PICKUP,
			SlotActionType.QUICK_CRAFT -> {
				val cursorStack = playerEntity.inventory.cursorStack

				// If trying to place a backpack into the backpack...
				if (cursorStack?.item is BackpackItem && slot.inventory == component) {
					// ...tell the client that their cursor stack hasn't changed
					// and then also tell the ServerPlayNetworkHandler
					(playerEntity as ServerPlayerEntity).updateCursorStack()
					cursorStack
				} else {
					null
				}
			}
			SlotActionType.QUICK_MOVE  -> {
				// If trying to shift-click a backpack from outside the backpack
				// into it, then say the spot they are shift-clicking hasn't
				// changed
				if (slot.stack.item is BackpackItem && slot.inventory != component) {
					slot.stack
				} else {
					null
				}
			}
			SlotActionType.SWAP        -> {
				val swappingWith = playerEntity.inventory.getStack(clickData)

				// If trying to swap another backpack from the hotbar into this
				// one, say that the hotbar slot hasn't changed
				if (swappingWith.item is BackpackItem && slot.inventory == component) {
					swappingWith
				} else {
					null
				}
			}

			// That's everything that can be used to insert an item into the
			// backpack. We're done.
			else                       -> null
		}
	}

	// Known bugs:
	// 1. Right-click-dragging a backpack quickly over another backpack's slots,
	//    placing it back in your inventory and then shift-clicking to put it
	//    into the other backpack's inventory sometimes causes
	//    desynchronization. Something about the right-click-drag causes the
	//    client to get out of sync and the shift-click looks like a shift click
	//    on air, so the server doesn't reset the client. This is probably a
	//    client issue, or one with how right-click-drags are denied by the
	//    server. Needs more research.
	// 2. Swapping with off-hand causes client-server desync. The server
	//    disallows it correctly, but the client doesn't get reset correctly so
	//    it desyncs. This is because the off-hand isn't part of the container
	//    so it doesn't get sent to the client when its move is rejected. The
	//    item appears to completely disappear from the client as it's removed
	//    from the container but not put back into their hand. This happens when
	//    trying to move the open backpack from your off-hand and also when
	//    trying to move another backpack from your off-hand into an open one.
	//
	// I'd say these are left as an exercise to the reader, but that would be a
	// fucking lie. I just don't feel like fixing them. They're not major.
}
