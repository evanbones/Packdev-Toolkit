# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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