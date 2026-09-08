# Distant Beacons

![Multiple beacons, ~1000 blocks apart each, with voxy](https://cdn.modrinth.com/data/EKfeICoL/images/221b359ad651733431ea4835dafb9e847a23b667.png)

Now beacons can actually be used for markers across players worlds! Distant Beacons extends Minecraft's beacon beam rendering beyond the normal client render distance. This mod looks best with LOD rendering mods such as **[Voxy](https://modrinth.com/mod/voxy)** and **[Distant Horizons](https://modrinth.com/mod/distanthorizons)**. 

## Features

- 🔭 **Extreme-distance beacon rendering**  
  Beacon beams can remain visible over a million blocks away.

- ✨ **Independent beam rendering**  
  Distant beams are rendered independently of the beacon block's normal client-side rendering range.

- 💾 **Persistent beacon tracking**  
  Known beacons are stored as persistent server data and restored across server/world restarts.

- 🎨 **Accurate beam data**  
  Beacon beam sections and colors are retained and reproduced for remote rendering.

- 🌐 **Multiplayer support**  
  For the mod to work in multiplayer, both the server and the client must have the mod installed.

## Configuration

Distant Beacons provides an in-game configuration screen through **[Mod Menu](https://modrinth.com/mod/modmenu)**.

![Distant Beacons configuration](https://cdn.modrinth.com/data/cached_images/7522ec507d7cb504a0e8c63f432d61dc459b7f28_0.webp)

Available settings:

### Width Distance Divisor

Controls how quickly the beam increases in width as the viewing distance increases. Lower values cause the beam to become thicker at shorter distances.

### Width Maximum Multiplier

Controls the maximum thickness multiplier applied to distant beacon beams. Higher values allow beams to become substantially wider at extreme distances.

### Camera Cull Distance

Controls the maximum distance used by the camera culling adjustment for distant beacon rendering. Without the mod the camera culling sets the beacons beam render limit to **2048** blocks, while the mod increases it to **1,048,576** blocks.

### Beam Height

Controls how far upward the remotely rendered beam extends. This can be configured to heights far beyond Minecraft's normal world height, and is needed when beams are very distant.

### Beam Depth

![Beam depth setting showcase](https://cdn.modrinth.com/data/cached_images/d9b4a798c6ee4e22f955bb5fee3c697919aa9006.gif)

Controls how far downward the remotely rendered beam extends below the beacon. This is useful when viewing beacons from extreme distances where the terrain around the beacon is not loaded. 
