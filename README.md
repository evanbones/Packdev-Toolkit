# Packdev Toolkit

<a href='https://fabricmc.net'><img alt="fabric" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>
<a href='https://neoforged.net/'><img alt="neoforge" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg"></a>

A collection of developer tools for building modpacks and datapacks. Pull textures/models out of resource packs,
dump lang entries, search datapacks, and copy item IDs: all without leaving the game!

## Requirements

- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl).
- [EMI](https://modrinth.com/mod/emi) (optional).
    - When present, exporting and translation-key-copying work on any hovered item/recipe in EMI.
- [Fabric API](https://modrinth.com/mod/fabric-api) (Fabric).

## Features

### Export hovered asset/recipe (`O`)

Press the export key (default: **`O`**) to pull whatever you're looking at into a checklist screen, where you pick which
files to copy into your export folder:

![Texture Extraction](https://raw.githubusercontent.com/evanbones/Packdev-Toolkit/refs/heads/1.21.1/images/extraction_screen.png)

- **Looking at a block**: grabs every texture used by its current block model (including block entities like chests).
- **Looking at an entity**: grabs its texture.
- **Holding an item**: grabs the item's texture(s).
- **Hovering an item in EMI**: same as holding it.
- **Hovering a recipe in EMI's recipe viewer**: saves the recipe straight to a datapack JSON file (singleplayer
  only).

![Recipe Extraction](https://raw.githubusercontent.com/evanbones/Packdev-Toolkit/refs/heads/1.21.1/images/recipe_extraction.png)

Hold **Shift** while pressing the export key to instead grab the raw JSON definitions (blockstate + block model, or
item model) instead of the PNG textures.

![Blockstate Extraction](https://raw.githubusercontent.com/evanbones/Packdev-Toolkit/refs/heads/1.21.1/images/blockstate_extraction.png)

Selected files are copied into your configured export directory (see [Configuration](#configuration)) and the containing
folder is opened for you automatically.

### Copy translation key (`J`)

Press the copy key (default: **`J`**) while looking at a block/entity, holding an item, or hovering an item in EMI to
copy its lang entry to your clipboard:

```json
"item.minecraft.diamond": "Diamond"
```

Handy for quickly building `en_us.json` entries for modpack renames.

### Dump item IDs (`/export`)

Grabs item IDs posts them to chat as a single copyable JSON array (click the message to copy it to your clipboard).
Useful for making tags or loot tables without manually typing IDs.

| Command             | Dumps                                             |
|---------------------|---------------------------------------------------|
| `/export hand`      | The item in your main hand                        |
| `/export hotbar`    | All 9 hotbar slots + offhand (duplicates ignored) |
| `/export inventory` | Your entire inventory (duplicates ignored)        |

### Search datapacks for item/block usage (`/query`)

Requires OP. Scans every loaded datapack for references to a given item or block and writes
the matching file IDs to a JSON file in your export folder (`queries/` subfolder), then links the file in chat.

| Command                                   | Searches                                                      |
|-------------------------------------------|---------------------------------------------------------------|
| `/query find_item_in_loot <item>`         | `loot_table` and `loot_modifiers` JSON for the item ID        |
| `/query find_block_in_features <block>`   | `worldgen/configured_feature` JSON for the block ID           |
| `/query find_block_in_structures <block>` | `.nbt` structure files whose block palette contains the block |

Useful for answering "which loot tables have this?" or "which structures use this block?" without manually searching
through datapack files.

## Configuration

Accessible from the config screen in-game using YACL, or `packdev_toolkit.json` in your config folder

### Export Directory

The root folder that assets, recipes, and `/query` results are written to. Can be relative to the game/server directory
(default `packdev_toolkit_exports`) or an absolute path (e.g. `C:/Users/Name/Downloads`).

Exports are namespaced as `assets/` (textures, blockstates, models), `data/` (recipes), and `queries/` (`/query`
results).

### Keybinds

Keybinds are rebindable through the Controls screen under the "Packdev Toolkit" category.

## Credits

Thanks to [Cassian](https://modrinth.com/user/Cassian) for the idea and basic implementation of translation key copying!

## License

[![Code license (MIT)](https://img.shields.io/badge/code%20license-MIT-green.svg?style=flat-square)](https://github.com/evanbones/Packdev-Toolkit/blob/1.20.1/LICENSE)

---

[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_vector.svg)](https://discord.com/invite/JcGRdT6Pbx) [![github-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/github-plural_vector.svg)](https://github.com/evanbones/Packdev-Toolkit)
