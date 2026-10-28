# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-10-27

### Changed

- Overhauled the mod selection, pack browser and asset extract screens with vanilla-style panels, text fields and scrollbars.
- The asset extract screen now lists assets in a scrollable list with texture thumbnails, sizes and folders, plus a Select All/None toggle and a selection counter.
  - Assets that can't be found are greyed out.
- The pack browser layout now scales with the window and GUI scale instead of using fixed positions.
- Image previews now use whole-number scaling on a transparency checkerboard, and animated textures preview their first frame.
- Search results in the pack browser show the file name first, with its folder alongside, and long names show the full path on hover.
- The pack browser's file tree scrolls sideways when deep folders don't fit, using the scrollbar along its bottom or Shift + scroll.
- The divider between the pack browser's panels can be dragged to resize them.

### Fixed

- Long file paths in the pack browser no longer get cut off at the panel edges.
- Exporting chests, signs, beds and other block entities now includes their texture instead of only the block's particle texture.

## [1.3.5] - 2026-10-27

### Changed

- The "Open" button in the pack browser now works for all files, not just JSON.

### Fixed

- Exporting images (and exporting from the pack browser) now opens the folder containing the exported file instead of the pack root.

## [1.3.4] - 2026-08-18

### Added

- Added an option to disable opening the file browser after an export.

## [1.3.3] - 2026-08-18

### Changed

- You can now scroll horizontally in the filetree.

### Fixed

- Exports now only open the parent folder automatically (fixes spam on large exports).

## [1.3.2] - 2026-08-04

### Fixed

- Fixed exporting tags with a slash.

## [1.3.1] - 2026-07-21

### Fixed

- Fixed tag additions.

## [1.3.0] - 2026-07-07

### Added

- Added `/packdev tag add` and `tag remove` command to quickly add items/entities/blocks to tags in-game.

### Changed

- Item Descriptions exports now live under their respective namespaces instead of `item_descriptions` (except for
  vanilla items).

## [1.2.0] - 2026-07-04

### Added

- Added `/packdev browse` command to browse/edit/export files from any mod in-game.

## [1.1.0] - 2026-07-04

### Added

- Added support for exporting Item Descriptions lang entries (Z by default).
- Added `/export` subcommands for biomes, structures, configured/placed features, tag entries, and registry entries.

### Changed

- Separated datapack, query, and resource pack export directories.

## [1.0.1] - 2026-07-04

### Added

- Added the ability to automatically export `mcmeta` files if they exist.

### Fixed

- Removed unused config option.

## [1.0.0] - 2026-07-02

- Initial release.