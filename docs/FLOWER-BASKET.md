# Flower Basket

Carry plants without filling your pockets. Each basket has 18 slots and keeps
its contents when closed, dropped or stored in a chest. Right-click with the
basket in either hand to open it.

The basket accepts plant block items, including saplings, flowers, leaves,
mushrooms, vines and cactus. It is not a general-purpose bag: seeds and unrelated
items are not accepted. Baskets cannot be put inside other baskets.

Suitable plants picked up from the ground go into the first basket in your main
inventory, working from the first hotbar slot onwards. Existing stacks are filled
first. Anything that does not fit is picked up normally, or stays on the ground
if your inventory is full. A basket in the offhand can be opened but does not
collect items automatically.

An open basket stays in its slot until you close it. This prevents it from being
moved into itself or swapped while its contents are being edited.

## Crafting

Six wooden sticks make one basket. Use this pattern in a crafting table:

```text
 S
S S
SSS
```

Sticks registered as `stickWood` are accepted. The basket has no durability.

## Older worlds

Old BOP flower baskets become this mod's baskets automatically. Their slot
positions, item counts, variants, names and custom data are preserved. Previously
stored items can be taken out even if the current basket would not accept them.

Back up the world first. If an item needed by a basket is missing, recovery stops
with an explanation rather than discarding it. Restore the backup and install
the required mod before trying again.

Basket recovery cannot restore a cabinet or entity that another mod discards.
Those containers need their own upgrade path. Install this add-on on both client
and server; a server-only migration mod cannot provide the basket or its screen.

This is an independent recreation with new artwork, not a bundled copy of BOP's
old implementation. It does not add new world generation.
