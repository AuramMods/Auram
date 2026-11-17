// Bonus tear for player-attributed ghast kills, independent of normal loot and Looting.
EntityEvents.death('minecraft:ghast', event => {
  if (event.level.isClientSide()) return;

  // Uses the responsible player, including the shooter of a projectile.
  let killer = event.source.player;
  if (!killer) return;

  let tear = killer.level.createEntity('minecraft:item');
  tear.mergeNbt({ Item: { id: 'minecraft:ghast_tear', Count: 1 } });
  tear.setPosition(killer.x, killer.y, killer.z);
  tear.setDefaultPickUpDelay();
  tear.spawn();
});
