craftingTable.remove(<item:mystcraft:booster>);
craftingTable.remove(<item:mystcraft:blockbookbinder>);
mods.extendedcrafting.TableCrafting.addShaped("89210c29-e74a-4d1d-99df-90219f1e72eb", 0, <item:mystcraft:blockbookbinder>, [
	[<item:minecraft:leather>, <item:minecraft:leather>, <item:minecraft:leather>, <item:minecraft:leather>, <item:minecraft:leather>], 
	[<item:minecraft:leather>, <item:botania:terrasteel_ingot>, <item:crystalcraft_unlimited_java:maroon_diamond>, <item:botania:terrasteel_ingot>, <item:minecraft:leather>], 
	[<item:minecraft:stripped_dark_oak_wood>, <item:draconicevolution:draconium_dust>, <item:projecte:alchemical_chest>, <item:malum:hallowed_gold_ingot>, <item:minecraft:stripped_dark_oak_wood>], 
	[<item:minecraft:stripped_dark_oak_wood>, <item:minecraft:glowstone_dust>, <item:embers:ember_grit>, <item:midnight:dewshroom_powder>, <item:minecraft:stripped_dark_oak_wood>], 
	[<item:aether:golden_oak_log>, <item:minecraft:stripped_dark_oak_wood>, <item:minecraft:stripped_dark_oak_wood>, <item:minecraft:stripped_dark_oak_wood>, <item:aether:golden_oak_log>]
]);


craftingTable.remove(<item:mystcraft:writingdesk>);
craftingTable.remove(<item:mystcraft:blockinkmixer>);

craftingTable.remove(<item:mystcraft:personal_link_book>);
craftingTable.addShaped("0efb9164-cbf9-4d29-b682-bd8962d9fb2c", <item:mystcraft:personal_link_book>, [
	[<item:minecraft:air>, <item:aether:zanite_gemstone>, <item:minecraft:air>], 
	[<item:minecraft:glowstone_dust>, <item:minecraft:bookshelf>, <item:tinkersjewelry:gem>.withTag({Material: "tconstruct:whitestone"})], 
	[<item:minecraft:air>, <item:midnight:moonshroom_powder>, <item:minecraft:air>]
]);