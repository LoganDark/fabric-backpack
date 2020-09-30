package net.logandark.backpack.item

import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3
import dev.onyxstudios.cca.api.v3.item.ItemComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.item.ItemComponentInitializer
import net.logandark.backpack.BackpackScreenHandler
import net.logandark.backpack.component.BackpackInventoryComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

object BackpackItem : Item(Settings().maxCount(1).group(ItemGroup.MISC)), ItemComponentInitializer {
	// This happens first, and gets a component key that we can use to register
	// our component with Cardinal Components. We specify this in our
	// fabric.mod.json, so all we have to do is declare that we have it here.
	@Suppress("MemberVisibilityCanBePrivate")
	val BACKPACK_INVENTORY_COMPONENT: ComponentKey<BackpackInventoryComponent> =
		ComponentRegistryV3.INSTANCE.getOrCreate(
			Identifier("backpack", "inventory"),
			BackpackInventoryComponent::class.java
		)

	// Immediately after we have our component key, this method is called by
	// Cardinal Components. We get a registry that we can use to say that all
	// items of our ID, backpack:backpack, should have a
	// BackpackInventoryComponent.
	override fun registerItemComponentFactories(registry: ItemComponentFactoryRegistry) {
		registry.registerFor(
			// This is our backpack item, all items with this identifier will be
			// assigned a BackpackInventoryComponent.
			Identifier("backpack", "backpack"),

			// Here we specify our component key, which acts like the ID of our
			// component.
			BACKPACK_INVENTORY_COMPONENT,

			// This works because BackpackInventoryComponent has a constructor
			// that takes a single ItemStack. If the constructor didn't work
			// like that, I'd have to pass a lambda here that takes an ItemStack
			// and returns a BackpackInventoryComponent.
			::BackpackInventoryComponent
		)
	}

	// Once we actually have the item in-game and right-click on it, this method
	// gets called.
	override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
		val stack = user.getStackInHand(hand)

		@Suppress("MemberVisibilityCanBePrivate")
		if (world.isClient) {
			// If this is the client, we can't do anything. But we have to
			// return success, or else the server won't get notified of our
			// usage!
			return TypedActionResult.success(stack)
		} else {
			// We're on the server, we have more power here. Get a reference to
			// the component on the item...
			val component = BackpackItem.BACKPACK_INVENTORY_COMPONENT.get(stack)

			// ...and open a screen. The server is actually what opens screens
			// on players - it assigns the client a "sync ID" and the client
			// uses that ID to interact with the screen, doing things like
			// moving items and such.
			(user as ServerPlayerEntity).openHandledScreen(object : NamedScreenHandlerFactory {
				// This is the title of the container, which is displayed at the
				// top of the screen. We can just return the name of the stack
				// here, so that players can rename the item to change the name
				// of the backpack GUI.
				override fun getDisplayName(): Text {
					return stack.name
				}

				// This is the most important part of a screen handler factory:
				// the actual part that creates the screen handler.
				override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler? {
					// Here, we provide our own custom screen handler that takes
					// three arguments: the sync ID, the player inventory and
					// the backpack inventory. It takes care of the rest, so go
					// see BackpackScreenHandler to continue on!
					return BackpackScreenHandler(syncId, inv, component)
				}
			})

			return TypedActionResult.success(stack)
		}
	}
}
