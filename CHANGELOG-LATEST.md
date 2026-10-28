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