import crafttweaker.api.recipe.replacement.Replacer;
import crafttweaker.api.recipe.replacement.type.NameFilteringRule;
import crafttweaker.api.recipe.replacement.type.ManagerFilteringRule;
import crafttweaker.api.recipe.replacement.type.ComponentFilteringRule;
import crafttweaker.api.recipe.replacement.type.ModsFilteringRule;
import crafttweaker.api.recipe.replacement.type.NotFilteringRule;
import crafttweaker.api.ingredient.IIngredient;
import crafttweaker.api.item.IItemStack;

// Bundle of Sticks
Replacer.create()
    .filter(ManagerFilteringRule.of(<recipetype:minecraft:crafting>))
    .filter(NotFilteringRule.of(NameFilteringRule.regex("^minecraft:crafting_special_")))
    .replace<IIngredient>(<recipecomponent:crafttweaker:input/ingredients>, 
        <tag:items:forge:rods/wooden> as IIngredient, 
        <item:auram:bundled_sticks> as IIngredient)
    .execute();


craftingTable.remove(<item:auram:bundled_sticks>);
var d = <item:minecraft:stick>;
var r = <item:minecraft:string>;

craftingTable.remove(<item:waystones:warp_stone>);
craftingTable.addShapeless("warper", <item:waystones:warp_stone> * 8, [<item:minecraft:amethyst_block>, <item:minecraft:ender_pearl>]);

craftingTable.addShaped("bundlesticksfix", <item:auram:bundled_sticks> * 6, [
    [d, r, d],
    [d, r, d],
    [d, r, d]]);

mods.extendedcrafting.CombinationCrafting.addRecipe("10cfe9d1-f944-4f33-b64a-4aa78a19a428", <item:auram:rock_catalyst>, 100000, [
	<item:crystalcraft_unlimited_java:shadow_diamond_block>, <item:minecraft:chest>, <item:thermal:magenta_rockwool>
]);