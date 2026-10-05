# Define duration: 0 fade in, 240 ticks (12 seconds) stay, 0 fade out
title @a times 0 240 0

# Main title in red
title @a title {"text":"This action will have consequences...","color":"red"}

# Subtitle handling
execute if storage dusmp_neoforge-1211:arg string equals "nick" run title @a subtitle {"storage":"dusmp_neoforge-1211:arg","color":"white"}
execute if storage dusmp_neoforge-1211:arg string equals "blank" run title @a subtitle {"clear":true}

# Play a sound using the master source (level-up sound as example)
playsound @a minecraft:entity.player.levelup master ~ ~ ~ 1 1