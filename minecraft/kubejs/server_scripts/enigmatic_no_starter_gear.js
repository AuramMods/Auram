// kubejs/server_scripts/enigmatic_no_starter_gear.js

PlayerEvents.loggedIn(event => {
  const player = event.player
  const data = player.persistentData

  const keys = [
    'enigmaticlegacy.firstjoin',
    'enigmaticlegacy.cursedgift',
    'enigmaticlegacy.patchouliforce'
  ]

  keys.forEach(key => {
    data.putBoolean(key, true)
  })

  let persisted = data.getCompound('PlayerPersisted')

  keys.forEach(key => {
    persisted.putBoolean(key, true)
  })

  data.put('PlayerPersisted', persisted)

  console.info(`[KubeJS] Forced Enigmatic Legacy starter flags for ${player.username}`)
})