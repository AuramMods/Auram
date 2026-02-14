craftingTable.remove(<item:colossalchests:chest_wall_copper>);


craftingTable.addShaped("60d2bd24-c0e3-4dee-8196-b860ab4bf838", <item:colossalchests:chest_wall_copper>, [
	[<item:thermal:copper_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:copper"}), <item:thermal:copper_gear>], 
	[<item:minecraft:chest>, <item:colossalchests:chest_wall_wood>, <item:minecraft:chest>], 
	[<item:thermal:copper_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:copper"}), <item:thermal:copper_gear>]
]);

craftingTable.remove(<item:colossalchests:chest_wall_iron>);
craftingTable.addShaped("f5239a64-dd71-49e3-8aa4-eaa69d2a7d07", <item:colossalchests:chest_wall_iron>, [
	[<item:thermal:iron_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:iron"}), <item:thermal:iron_gear>], 
	[<item:ironchest:iron_chest>, <item:colossalchests:chest_wall_copper>, <item:ironchest:iron_chest>], 
	[<item:thermal:iron_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:iron"}), <item:thermal:iron_gear>]
]);

craftingTable.remove(<item:colossalchests:chest_wall_silver>);
craftingTable.addShaped("a65883d0-47e9-445e-9534-c730a93a41b0",  <item:colossalchests:chest_wall_silver>, [
	[<item:thermal:silver_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:silver"}), <item:thermal:silver_gear>], 
	[<item:ironchest:gold_chest>, <item:colossalchests:chest_wall_iron>, <item:ironchest:gold_chest>], 
	[<item:thermal:silver_gear>, <item:tconstruct:large_plate>.withTag({Material: "tconstruct:silver"}), <item:thermal:silver_gear>]
]);

craftingTable.remove(<item:colossalchests:chest_wall_gold>);
craftingTable.addShaped("467ea66d-672f-420d-8234-0a3298b74709",  <item:colossalchests:chest_wall_gold>, [
	[<item:thermal:gold_gear>, <item:tconstruct:large_plate_cast>, <item:thermal:gold_gear>], 
	[<item:ironchest:gold_chest>, <item:colossalchests:chest_wall_silver>, <item:ironchest:gold_chest>], 
	[<item:thermal:gold_gear>, <item:tconstruct:large_plate_cast>, <item:thermal:gold_gear>]
]);

craftingTable.remove(<item:colossalchests:chest_wall_diamond>);
craftingTable.addShaped("080f9889-9098-419b-98c4-917a6affb899",  <item:colossalchests:chest_wall_diamond>, [
	[<item:thermal:diamond_gear>, <item:minecraft:diamond_block>, <item:thermal:diamond_gear>], 
	[<item:ironchest:diamond_chest>, <item:colossalchests:chest_wall_gold>, <item:ironchest:diamond_chest>], 
	[<item:thermal:diamond_gear>, <item:minecraft:diamond_block>, <item:thermal:diamond_gear>]
]);

craftingTable.remove(<item:colossalchests:chest_wall_obsidian>);
craftingTable.addShaped("c01cb2db-f5a7-440c-9251-881c17558459",  <item:colossalchests:chest_wall_obsidian>, [
	[<item:mekanism:ingot_refined_obsidian>, <item:mekanism:block_refined_obsidian>, <item:mekanism:ingot_refined_obsidian>], 
	[<item:ironchest:crystal_chest>, <item:colossalchests:chest_wall_diamond>, <item:ironchest:crystal_chest>], 
	[<item:mekanism:ingot_refined_obsidian>, <item:mekanism:block_refined_obsidian>, <item:mekanism:ingot_refined_obsidian>]
]);