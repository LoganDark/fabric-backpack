package net.logandark.backpack

import net.fabricmc.api.ModInitializer
import net.logandark.backpack.item.BackpackItem
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

@Suppress("unused")
object Backpack : ModInitializer {
	// This is where the tour starts. First, the BackpackItem is added to the
	// item registry. Go there to continue on!
	override fun onInitialize() {
		Registry.register(Registry.ITEM, Identifier("backpack", "backpack"), BackpackItem)
	}
}
