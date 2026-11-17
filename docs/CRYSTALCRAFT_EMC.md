# Crystalcraft ore-gem EMC

The local ProjectE overrides assign **4,096 EMC each to 101 distinct Crystalcraft gem items that drop directly from ore blocks**. The values are stored in `minecraft/config/ProjectE/custom_emc.json`.

## Finding the list

The installed mod jar contains the data needed to build the list without guessing from item names:

1. Read `data/crystalcraft_unlimited_java/worldgen/configured_feature/*.json`. Collect the block states produced by `minecraft:ore` or `minecraft:scattered_ore` features. This also catches unusual ore IDs such as `peacock_ore_block` and `matrix_ore`.
2. Read those blocks' `data/crystalcraft_unlimited_java/loot_tables/blocks/*.json` files, including nested entries and random variants.
3. Keep dropped items in the Crystalcraft namespace that the mod includes in `forge:gems` or a `forge:gems/*` item tag. Follow tag references when present.
4. Deduplicate item IDs across stone/deepslate ores and assign the value to the gem item, rather than the ore block.

The extractor is read-only and can regenerate the complete list:

```sh
python3 scripts/list_crystalcraft_ore_gems.py \
  "minecraft/mods/Forge 1.20.1 Crystalcraft Unlimited Void Angel Update.jar"
```

Add `--json` for item/name/source-ore records and the jar checksum. The extractor reads the installed jar only; it does not download a mod or change EMC settings.

## Scope

- The exact registry IDs matter: `saphire` and `sapphire`, and `flourite` and `fluorite`, are separate items in this mod and all four are covered.
- Zircon ore has 13 possible gem drops: `zircon_1` through `zircon_12`, plus `zircon_14`. There is no `zircon_13` entry in that ore's loot table.
- Some ore-drop gems are also obtainable through crafting. An additional recipe does not exclude an item that satisfies the ore-drop rule.
- Fusion/crafted variants without an ore drop, such as solar/shadow/eclipse diamonds, painite, star sapphires and colored pearls, receive no new explicit override. Their EMC can still be calculated from recipes using the changed ingredients.
- Raw metals, dusts, ingots, scrap and gems obtained only from clusters or other non-ore sources are outside this direct-ore-drop rule. For example, pyrite is tagged as an ingot; sulfur and chloronium are tagged as dusts. Seaborgium requires smelting its raw material rather than dropping as a gem from its ore.
- Existing custom overrides remain intact: dragon eggs at **60,000 EMC**, dragon scales at **1,500 EMC**, stone at 1, cobblestone at 0, and electrotine dust at 32. A blanket override of every gem-tagged item would incorrectly include dragon scales.
- These are explicit config values, not a dump of the running server's current EMC map. The game must reload its EMC data or restart to use config changes.

## Audited jar

- File: `Forge 1.20.1 Crystalcraft Unlimited Void Angel Update.jar`
- SHA-256: `f2a4e8a0a0ed48ea247fbd8a21ba37fbbbfffa03a496a528d1543d95ffd0597b`

## Full list

Every item and source-ore path below uses the namespace `crystalcraft_unlimited_java:`. For example, `ruby` means `crystalcraft_unlimited_java:ruby`. All rows receive **4,096 EMC**.

| Gem name | Item path | Source ore block paths |
| --- | --- | --- |
| Alexandrite | `alexandrite` | `alexandrite_ore`, `deepslate_alexandrite_ore` |
| Amazonite | `amazonite` | `amazonite_ore` |
| Amber | `amber` | `amber_ore`, `deepslate_amber_ore` |
| Amethyst | `amethyst` | `amethyst_ore`, `deepslate_ametyst_ore` |
| Ametrine | `ametrine` | `ametrine_ore`, `deepslate_ametrine_ore` |
| Ammolite | `ammolite` | `ammolite_ore`, `deepslate_ammolite_ore` |
| Anti-Humoranium | `anti_humoranium` | `anti_humoranium_ore` |
| Aquamarine | `aquamarine` | `aquamarine_ore`, `deepslate_aquamarine_ore` |
| Sapphire | `australian_sapphire` | `australian_sapphire_ore` |
| Azurite | `azurite` | `azurite_ore` |
| Bixbite | `bixbite` | `bixbite_ore` |
| Black Opal | `black_opal` | `black_opal_ore`, `deepslate_black_opal_ore` |
| Bloodstone | `bloodstone` | `bloodstone_ore` |
| Brickerite | `brickerite` | `brickerite_ore` |
| Catseye | `catseye` | `catseye_ore` |
| Chalcanthite | `chalcanthite` | `chalcanthite_ore` |
| Chrysolite | `chrysolite` | `chrysolite_ore`, `deepslate_chrysolite_ore` |
| Cinnabar | `cinnabar` | `cinnabar_ore` |
| Citrine | `citrine` | `citrine_ore`, `deepslate_citrine_ore` |
| Cyan Emerald | `cyan_emerald` | `cyan_emerald_ore` |
| Cyber Crystal | `cyber_crystal` | `cyber_crystal_ore` |
| Enderium | `enderium` | `enderium_ore` |
| Fire Opal | `fire_opal` | `fire_opal_ore` |
| Flourite | `flourite` | `deepslate_flourite_ore`, `flourite_ore` |
| Fluorite | `fluorite` | `fluorite_ore` |
| Garnet | `garnet` | `deepslate_garnet_ore`, `garnet_ore` |
| Goshenite | `goshenite` | `goshenite_ore` |
| Grandidierite | `grandidierite` | `deepslate_grand_ore`, `grandidierite_ore` |
| Catseye | `green_catseye` | `green_catseye_ore` |
| Hackmantite | `hackmantite` | `hackmantite_ore` |
| Hercynite | `hercynite` | `hercynite_ore` |
| Howlite | `howlite` | `howlite_ore` |
| Humoranium | `humoranium` | `humoranium_ore` |
| Indigolite | `indigolite` | `indigolite_ore` |
| Iolite | `iolite` | `iolite_ore` |
| Iris Agate | `iris_agate` | `iris_agate_ore` |
| Jade | `jade` | `deepslate_jade_ore`, `jade_ore` |
| Jasper | `jasper` | `jasper_ore` |
| Kunzite | `kunzite` | `kunzite_ore` |
| Labradorite | `labradorite` | `labradorite_ore` |
| Larimar | `larimar` | `larimar_ore` |
| Malachite | `malachite` | `deepslate_malachite_ore`, `malachite_ore` |
| Matrix Opal | `matrix_opal` | `matrix_ore` |
| Medusa Quartz | `medusa_quartz` | `medusa_quartz_ore` |
| Moonstone | `moonstone` | `moonstone_ore` |
| Olivine | `olivine` | `olivine_ore` |
| Onyx | `onyx` | `onyx_ore` |
| Opal | `opal` | `deepslate_opal_ore`, `opal_ore` |
| Orange Diamond | `orange_diamond` | `orange_diamond_ore` |
| Peacock Ore | `peacock_ore` | `peacock_ore_block` |
| Peacock Topaz | `peacock_topaz` | `peacock_topaz_ore` |
| Peridot | `peridot` | `deepslate_peridot_ore`, `peridot_ore` |
| Pietersite | `pietersite` | `deepslate_pietersite_ore` |
| Pink Diamond | `pink_diamond` | `pink_diamond_ore` |
| Pink Ruby | `pink_ruby` | `pink_ruby_ore` |
| Pitambari Neelam | `pitambari_neelam` | `pitambari_neelam_ore` |
| Prism | `prism` | `deepslate_prism_ore`, `prism_ore` |
| Purple Diamond | `purple_diamond` | `purple_diamond_ore` |
| Rainbow Opal | `rainbow_opal` | `rainbow_opal_ore` |
| Rare Amethyst | `rare_amethyst` | `rare_amethyst_ore` |
| Rare Citrine | `rare_citrine` | `rare_citrine_ore` |
| Sapphire | `rare_sapphire` | `rare_deepslate_sapphire_ore`, `rare_sapphire_ore` |
| Tanzanite | `rare_tanzanite` | `rare_tanzanite_ore` |
| Raspberry Diamond | `raspberry_diamond` | `raspberry_diamond_ore` |
| Red Diamond | `red_diamond` | `red_diamond_ore` |
| Rhodonite | `rhodonite` | `rhodonite_ore` |
| Rose Quartz | `rose_quartz` | `rose_quartz_ore` |
| Ruby | `ruby` | `deepslate_ruby_ore`, `ruby_ore` |
| Rutile | `rutile` | `rutile_ore` |
| Sapphire | `saphire` | `deepslate_sapphire_ore`, `saphire_ore` |
| Sapphire | `sapphire` | `deepslate_saphire_ore`, `sapphire_ore` |
| Sardonyx | `sardonyx` | `deepslate_sardonyx_ore`, `sardonyx_ore` |
| Serpentine | `serpentine` | `serpentine_ore` |
| Smoky Quartz | `smoky_quartz` | `smoky_quartz_ore` |
| Sonoranite | `sonoranite` | `sonoranite_ore` |
| Spinel | `spinel` | `deepslate_spinel_ore`, `spinel_ore` |
| Stibnite | `stibnite` | `stibnite_ore` |
| Sugillite | `sugillite` | `deepslate_sugillite_ore`, `sugillite_ore` |
| Sunset Jasper | `sunset_jasper` | `sunset_jasper_ore` |
| Sunstone | `sunstone` | `sunstone_ore` |
| Tanzanite | `tanzanite` | `deepslate_tanzanite_ore`, `tanzanite_ore` |
| Topaz | `topaz` | `deepslate_topaz_ore`, `topaz_ore` |
| Tourmaline | `tourmaline` | `deepslate_tourmaline_ore`, `tourmaline_ore` |
| Turquoise | `turquoise` | `turquoise_ore` |
| Vesuvianite | `vesuvianite` | `vesuvianite_ore` |
| Watermelon Tourmaline | `watermelon_tourmaline` | `watermelon_tourmaline_ore` |
| White Diamond | `white_diamond` | `white_diamond_ore` |
| Yellow Onyx | `yellow_onyx` | `yellow_onyx_ore` |
| Zircon | `zircon_1` | `zircon_ore` |
| Zircon | `zircon_10` | `zircon_ore` |
| Zircon | `zircon_11` | `zircon_ore` |
| Zircon | `zircon_12` | `zircon_ore` |
| Zircon | `zircon_14` | `zircon_ore` |
| Zircon | `zircon_2` | `zircon_ore` |
| Zircon | `zircon_3` | `zircon_ore` |
| Zircon | `zircon_4` | `zircon_ore` |
| Zircon | `zircon_5` | `zircon_ore` |
| Zircon | `zircon_6` | `zircon_ore` |
| Zircon | `zircon_7` | `zircon_ore` |
| Zircon | `zircon_8` | `zircon_ore` |
| Zircon | `zircon_9` | `zircon_ore` |
