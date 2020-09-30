package net.logandark.backpack.component

import dev.onyxstudios.cca.api.v3.component.ComponentV3
import nerdhub.cardinal.components.api.component.Component
import nerdhub.cardinal.components.api.util.ItemComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
class BackpackInventoryComponent(
	val stack: ItemStack
) : ItemComponent<BackpackInventoryComponent>, ComponentV3, Inventory {
	// Right now, the amount of slots is hardcoded to 18 items, or 2 rows of 9.
	// Try changing this value!
	val items = Array<ItemStack>(9 * 2) { ItemStack.EMPTY }

	// What follows is all the methods needed to implement the Inventory
	// interface.

	// This method is called by Minecraft whenever something changes in the
	// inventory.
	override fun markDirty() {
		// Nothing. The synchronization will happen when it happens.
	}

	override fun clear() {
		items.fill(ItemStack.EMPTY)
	}

	override fun setStack(slot: Int, stack: ItemStack) {
		// It's safe to not care about being in bounds because it's the caller's
		// job to make sure that it is. Other Minecraft inventories throw
		// IndexOutOfBoundsExceptions if you try to set a stack that doesn't
		// exist, which means it's okay for us to as well.
		items[slot] = stack
	}

	override fun isEmpty(): Boolean {
		return items.all { it.isEmpty }
	}

	// This method is for removing only a specific amount of items from a
	// certain slot. See the next method for a version that removes an
	// entire stack out of a slot.
	override fun removeStack(slot: Int, amount: Int): ItemStack {
		val current = getStack(slot)

		// Luckily, ItemStacks already have a method that allows us to split off
		// a certain amount. It's called split. Basically what it does is return
		// a new ItemStack containing up to the specified amount of items, while
		// those items are subtracted from the stack that you called split on.
		return current.split(amount)
	}

	override fun removeStack(slot: Int): ItemStack {
		// Grab the item out of the slot.
		val current = getStack(slot)
		setStack(slot, ItemStack.EMPTY)
		return current
	}

	override fun getStack(slot: Int): ItemStack {
		return try {
			items[slot]
		} catch (_: IndexOutOfBoundsException) {
			// Unlike for setStack, we cannot throw an exception here. Minecraft
			// expects ItemStack.EMPTY if it tries to get from an invalid slot.
			//
			// A good rule of thumb is to see what exceptions vanilla methods
			// can throw, as a guideline for what exceptions you can throw.
			// Vanilla methods seem to use bounds checking here to prevent these
			// exceptions from occurring, so we shouldn't throw them ourselves.
			ItemStack.EMPTY
		}
	}

	override fun canPlayerUse(player: PlayerEntity): Boolean {
		// Only allow a player to use this inventory if they're holding the
		// backpack. This method is called every tick, and if it ever returns
		// false, the player is booted out of the GUI instantly.
		//
		// This way, if the item ever gets dropped or moved in any way, the GUI
		// is instantly closed.
		return player.itemsHand.any { it === stack }
	}

	override fun size(): Int {
		return items.size
	}

	// Alright, this is the end of the inventory methods. Everything after here
	// is Cardinal Components.

	// This is called whenever an item is copied, we want the inventory to copy
	// efficiently as well
	override fun copyFrom(other: BackpackInventoryComponent) {
		// Copy the items so that mutations in the other inventory don't apply
		// to this one
		for (i in 0 until items.size) {
			items[i] = other.items[i].copy()
		}
	}

	// Here we basically write all of our items to a list. Not very interesting,
	// but it's good to know how it's done.
	override fun writeToNbt(tag: CompoundTag) {
		val listTag = ListTag()

		for (stack in items) {
			listTag.add(stack.toTag(CompoundTag()))
		}

		tag.put("items", listTag)
	}

	// Just read the items from the list.
	override fun readFromNbt(tag: CompoundTag) {
		val listTag = tag.getList("items", 10)

		for (i in 0 until listTag.size) {
			items[i] = ItemStack.fromTag(listTag.getCompound(i))
		}
	}

	// Our component is equal to another if they have the exact same items that
	// we do.
	override fun isComponentEqual(other: Component): Boolean {
		// This component is not equal to any components that aren't also
		// backpack inventories
		if (other !is BackpackInventoryComponent)
			return false

		// This component is not equal to any inventory with a different amount
		// of slots
		if (other.items.size != items.size)
			return false

		// We have to use this instead of checking the array itself for equality
		// since we need to use the special ItemStack.areEqual method. Otherwise
		// a clone of this component is not equal to itself and the client plays
		// the item equip animation every tick.
		for (i in 0 until items.size)
			if (!ItemStack.areEqual(other.items[i], items[i]))
				return false

		return true
	}

	// Don't forget to return to BackpackScreenHandler!
}
