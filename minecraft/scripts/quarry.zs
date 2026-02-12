craftingTable.remove(<item:extrautilitiesrebirth:ender_infused_obsidian>);
mods.extendedcrafting.CombinationCrafting.addRecipe("f345129f-c1fe-45f2-9d70-95e0e3ea56cx", <item:extrautilitiesrebirth:ender_infused_obsidian>*2, 100000, [
	<item:minecraft:obsidian>, <item:minecraft:ender_pearl>, <item:minecraft:ender_pearl>, <item:minecraft:ender_pearl>, <item:minecraft:ender_pearl>
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_quarry>);
mods.extendedcrafting.CombinationCrafting.addRecipe("c555b425-6526-4bbc-bfa9-b6c92cce8a4b", <item:extrautilitiesrebirth:ender_quarry>, 1000000, [
	<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:mekanism:advanced_control_circuit>, <item:crystalcraft_unlimited_java:black_opal_pickaxe>.withTag({Damage: 0 as int}), <item:extrautilitiesrebirth:ender_core>, <item:extrautilitiesrebirth:ender_thermic_pump>
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_thermic_pump>);
mods.extendedcrafting.CombinationCrafting.addRecipe("b0337066-4471-4be1-b2ac-a7cd048f37bc", <item:extrautilitiesrebirth:ender_thermic_pump>, 1000000, [
	<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:embers:ember_siphon>, <item:mekanism:advanced_mechanical_pipe>, <item:draconicevolution:draconium_core>, <item:botania:lens_normal>
]);

craftingTable.remove(<item:tesseract:tesseract>);
mods.extendedcrafting.CombinationCrafting.addRecipe("40fa61db-8b05-400f-9182-f901be8e9077", <item:tesseract:tesseract>, 1000000, [
	<item:extendedcrafting:frame>, <item:minecraft:glowstone_dust>, <item:midnight:moonshroom_powder>, <item:botania:mana_powder>, <item:evolvedmekanism:quantum_mechanical_pipe>, <item:evolvedmekanism:quantum_logistical_transporter>, <item:embers:ember_grit>, <item:evolvedmekanism:quantum_universal_cable>, <item:ae2:singularity>
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_quarry_speed_upgrade>);
mods.extendedcrafting.TableCrafting.addShaped("6201664d-656c-45ee-a996-342a070a2453", 0, <item:extrautilitiesrebirth:ender_quarry_speed_upgrade>, [
	[<item:extrautilitiesrebirth:speed_upgrade>, <item:refinedstorage:speed_upgrade>, <item:extrautilitiesrebirth:speed_upgrade>], 
	[<item:thermal:rf_coil_xfer_augment>, <item:extrautilitiesrebirth:ender_quarry_upgrade_base>, <item:thermal:rf_coil_xfer_augment>], 
	[<item:extrautilitiesrebirth:speed_upgrade>, <item:mekanism:upgrade_speed>, <item:extrautilitiesrebirth:speed_upgrade>]
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade_2>);
mods.extendedcrafting.TableCrafting.addShaped("3b538a17-1f06-4d7b-897e-1a37094f44ba", 0, <item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade_2>, [
	[<item:pneumaticcraft:upgrade_matrix>, <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:apotheosis:gem>.withTag({affix_data: {rarity: "apotheosis:ancient"}, gem: "apotheosis:the_end/endersurge"}), <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:pneumaticcraft:upgrade_matrix>], 
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:ars_nouveau:thread_heights>, <item:extrautilitiesrebirth:ender_quarry_speed_upgrade>, <item:draconicevolution:module_core>, <item:extrautilitiesrebirth:ender_infused_obsidian>], 
	[<item:botania:terrasteel_ingot>, <item:extrautilitiesrebirth:ender_quarry_speed_upgrade>, <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:extrautilitiesrebirth:ender_quarry_speed_upgrade>, <item:botania:terrasteel_ingot>], 
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:evolvedmekanism:alloy_hypercharged>, <item:extrautilitiesrebirth:ender_quarry_speed_upgrade>, <item:evolvedmekanism:alloy_hypercharged>, <item:extrautilitiesrebirth:ender_infused_obsidian>], 
	[<item:evolvedmekanism:alloy_hypercharged>, <item:midnight:rendium_shard>, <item:embers:catalytic_plug>, <item:midnight:rendium_shard>, <item:evolvedmekanism:alloy_hypercharged>]
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade_3>);
mods.extendedcrafting.CombinationCrafting.addRecipe("d61e357d-b365-4a31-99c1-55ccb46a8f1d", <item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade_3>, 100000000, [
	<item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade_2>, <item:draconicevolution:awakened_core>, <item:extendedcrafting:singularity>.withTag({Id: "extendedcrafting:redstone"}), <item:botania:life_essence>, <item:draconicevolution:awakened_core>, <item:draconicevolution:awakened_core>, <item:draconicevolution:awakened_core>, <item:evolvedmekanism:dense_control_circuit>, <item:extendedcrafting:crystaltine_catalyst>
]);

craftingTable.remove(<item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade>);
mods.extendedcrafting.TableCrafting.addShaped("d0b73542-18c5-46cd-a6e8-03623b27eca8", 0, <item:extrautilitiesrebirth:ender_quarry_silk_touch_upgrade>, [
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:minecraft:enchanted_book>.withTag({StoredEnchantments: [{id: "minecraft:silk_touch", lvl: 1 as short}]}), <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:extrautilitiesrebirth:ender_infused_obsidian>], 
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:appflux:core_64k>, <item:ae2:fluix_pearl>, <item:appflux:core_64k>, <item:extrautilitiesrebirth:ender_infused_obsidian>], 
	[<item:refinedstorage:silk_touch_upgrade>, <item:botania:mana_pearl>, <item:crystalcraft_unlimited_java:eclipse_diamond>, <item:botania:mana_pearl>, <item:mekanism:module_silk_touch_unit>], 
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:appflux:core_64k>, <item:ae2:fluix_pearl>, <item:appflux:core_64k>, <item:extrautilitiesrebirth:ender_infused_obsidian>], 
	[<item:extrautilitiesrebirth:ender_infused_obsidian>, <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:tinkersjewelry:gem>.withTag({Material: "tinkersjewelry:amethyst"}), <item:extrautilitiesrebirth:ender_infused_obsidian>, <item:extrautilitiesrebirth:ender_infused_obsidian>]
]);