import crafttweaker.api.loot.modifier.CommonLootModifiers;

<entitytype:minecraft:blaze>.addLootModifier(
  "blaze_rod_drop_blaze",
  CommonLootModifiers.add(<item:minecraft:blaze_rod>)
);

<entitytype:minecraft:blaze>.addLootModifier(
  "blaze_rod_drop_blaze_gp",
  CommonLootModifiers.add(<item:minecraft:gunpowder>)
);

<entitytype:minecraft:sheep>.addLootModifier(
  "sheepstring",
  CommonLootModifiers.add(<item:minecraft:string>)
);

<entitytype:minecraft:creeper>.addLootModifier(
  "gunpowdercrp",
  CommonLootModifiers.add(<item:minecraft:gunpowder>)
);

<entitytype:minecraft:enderman>.addLootModifier(
  "epearl",
  CommonLootModifiers.add(<item:minecraft:ender_pearl>)
);