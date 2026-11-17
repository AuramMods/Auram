// One guaranteed bonus skull at the skeleton's death location, for any cause of death.
// Normal loot and Looting remain independent and may produce another skull.
EntityEvents.death('minecraft:wither_skeleton', event => {
  if (event.level.isClientSide()) return;

  let skeleton = event.entity;
  let skull = event.level.createEntity('minecraft:item');
  skull.mergeNbt({ Item: { id: 'minecraft:wither_skeleton_skull', Count: 1 } });
  skull.setPosition(skeleton.x, skeleton.y, skeleton.z);
  skull.setDefaultPickUpDelay();
  skull.spawn();
});
