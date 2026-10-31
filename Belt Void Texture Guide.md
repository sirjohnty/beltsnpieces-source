# Belt Void texture sheet

Current housing mapping: world and item models now reference `create:block/brass_block` directly. The roof edge and walls share a continuous vertical UV range, with opening posts sampling the matching outer borders. This removes the extra horizontal band baked into the old atlas side regions. The PNG and region documentation below are retained as a legacy editable source; they no longer drive the housing. Flaps still use Create's brass funnel texture.

Edit this PNG directly:

[belt_void.png](src/main/resources/assets/beltsnpieces/textures/block/belt_void.png)

256 x 256 RGBA PNG. Keep the dimensions and filename. All housing surfaces use this single image. The approved black moving flaps retain Create's original funnel texture. The sheet now contains the finished warm brass material, using Create's actual brass block artwork and brass funnel rim pixels.

Each region is 64 x 64 pixels. Coordinates start at the top-left:

| Region | Pixel rectangle (x, y, width, height) |
|---|---|
| Left side panel | 0, 0, 64, 64 |
| Right side panel | 64, 0, 64, 64 |
| Roof exterior | 128, 0, 64, 64 |
| Roof interior | 192, 0, 64, 64 |
| Intake header | 0, 64, 64, 64 |
| Exit header | 64, 64, 64, 64 |
| Intake wall edges | 128, 64, 64, 64 |
| Exit wall edges | 192, 64, 64, 64 |
| Wall undersides | 0, 128, 64, 64 |
| Reserved (unused) | 64, 128, 64, 64 |
| Reserved (unused) | 128, 128, 64, 64 |
| Edges and trim | 192, 128, 64, 64 |
| Reserved (unused) | 0, 192, 64, 64 |
| Reserved (unused) | 64, 192, 64, 64 |
| Reserved (unused) | 128, 192, 64, 64 |
| Reserved (unused) | 192, 192, 64, 64 |

The last four regions are reserved and currently unused. Flap appearance is supplied by Create. Repeated frame pieces share their region. Each housing face uses 4 texture pixels per model unit, so 4 texture pixels span 1 model unit (1/16 of a block) everywhere. Smaller faces use only part of their 64-pixel region, starting at its top-left corner.

Keep pixel edges crisp; disable smoothing in your image editor. Save the PNG, run Gradle classes to copy resources, and use F3+T in the development client. No release build is needed.

Outer side panels now use their complete 64 x 64 regions across the combined roof edge and wall: the roof uses the top 1.25/12 of the artwork, and the wall uses the remainder. This preserves all four borders and makes the pattern continuous at the roof seam. The roof exterior also uses its complete region. These framed surfaces fit the artwork to their full bounds rather than cropping at four texture pixels per model unit. The item model uses the same mapping.

[Labeled UV guide](Belt%20Void%20UV%20Guide.svg)

The housing is now three flat cuboids: roof and two side walls (16 visible quads). Left/right regions cover the full outer walls. Raised hatch, ribs, fasteners and separate feet were removed. Both wall upper faces are omitted where they meet the roof.

Artwork uses 4x4 identical pixel groups: one logical texel per model unit, matching the brass funnel. Keep that coarse grid when editing. The 256px sheet is retained for atlas layout compatibility.

Brass finish: side/roof regions use Create brass block art; header/post regions use the brass funnel intake rim. The black flap material remains Create's original funnel material.
