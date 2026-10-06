# Crafting

Use core 0.3.0.110021 for ordinary crafting recipes. No template or cutting tool
is required. All ingredients must be the same material, including wood type.

- Three full blocks in a row make six horizontal slabs.
- Three full blocks in a column make six vertical slabs.
- Two matching slabs diagonally in a 2 by 2 square make four steps.
- Two steps side by side make four corners.
- Six full blocks in the usual stair pattern make four stairs.
- Six full blocks in two columns of three make six walls.

Existing BOP slab and stair recipes remain unchanged. New recipes are added
only for missing pieces. A slab by itself can be rotated into its matching
vertical slab, or back again, without changing its quantity.
Steps can also be rotated one for one; horizontal and vertical step items
both work in corner recipes. Use two of the same item type rather than mixing
horizontal and vertical items in one recipe.

The core registers the recipes for every installed catalogue. This add-on
does not add templates, wooden walls, fences, gates or terrain generation.
Use BOP's native fences and gates. BOP materials in this catalogue do not
have pane recipes.

Automatic recovery of old pieces is separate from crafting. Old beta templates
can be recycled into paper in the grid, but they no longer cut building pieces.
