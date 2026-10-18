# Smart Chest

A chest that works like the vanilla one — same size, same double chest, same hoppers, same comparator, **same
copper golem** — with upgrade slots next to its inventory. One upgrade raises how many items a slot holds, up to
**999**. Another **compresses piles into blocks** on its own, so a full stack of emeralds becomes a block of
emeralds instead of a second slot.

For Minecraft **26.3**, on Fabric.

<!-- Screenshot: a Smart Chest screen with the upgrade panel on the right -->

## The chest

- 27 slots, a double chest when two are placed side by side, hopper in and out, comparator output.
- **12 wood variants**: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, poplar, crimson
  and warped. There is no bamboo one.
- **Recipe**: eight planks of the wood you want around a copper ingot. One recipe per wood.
- A Smart Chest only joins another Smart Chest **of the same wood**, never a vanilla chest.
- You find them in the *Functional Blocks* creative tab, right after the vanilla chest.

<!-- Screenshot: the twelve variants in a row -->

## Upgrade slots

Five slots sit in a panel to the right of the chest screen. Hoppers and copper golems never see them, so they
never fill them with items.

| Slot | Takes |
| --- | --- |
| 1 | a Capacity Upgrade |
| 2 | a vanilla **Crafter** (turns compression on) |
| 3 – 5 | one item each, for forced compression (see below) |

**Double chest**: the upgrades of one half — the left one as you face the chest — apply to all 54 slots, and the
screen shows only that panel. When two chests merge, upgrades of the other half move into the free slots of the
main half; if a slot is taken they are dropped in front of the chest. Breaking a chest drops everything it holds,
upgrades included.

## Capacity upgrades

An upgrade sets how many items one slot can hold, for **every item that is not damageable** — it also applies to
items that stack to 16 or 1 in vanilla, like ender pearls or potions.

| Upgrade | Slot cap | Centre of the recipe |
| --- | --- | --- |
| Capacity Upgrade (128) | 128 | iron ingot |
| Capacity Upgrade (256) | 256 | gold ingot |
| Capacity Upgrade (512) | 512 | diamond |
| Capacity Upgrade (999) | 999 | netherite scrap |

The four recipes are independent — you do not need the previous tier — and share one pattern. Only the centre
changes:

```
lapis      observer   lapis
amethyst   CENTRE     amethyst
redstone   piston     redstone
```

(`amethyst` is an amethyst shard.)

- **No upgrade, no change**: a slot holds the vanilla stack size of its item.
- **Not affected**: tools, weapons and armour (anything with durability), plus shulker boxes, bundles and written
  books. These keep their vanilla stack size.
- **Taking items out**: one click, or one shift-click, takes out **one vanilla stack** of the item — 64, 16 or 1.
  Potions come out one by one.
- **Hoppers and copper golems** also stay at the vanilla stack size per slot. Only you fill a slot past it, by
  hand.
- **Removing the upgrade** is refused while a slot holds more than the lower cap would allow. The screen says
  so.
- **Comparator**: the signal is the fill ratio against each slot's cap, so a chest with a low tier climbs faster.
- Breaking the chest drops the contents in the usual small item entities, never a single entity of 999.

## Compression

Put a **vanilla Crafter** in the second upgrade slot. When the chest holds more of an item than **one slot can
take** — 64 by default, or the cap of the capacity upgrade — it turns *N* of them into one block, and repeats as
long as the total stays above the cap.

> 64 emeralds + 1 emerald = 65, over the cap of 64. Nine emeralds are removed, an emerald block is added, and 56
> emeralds are left.

It counts the **total** in the chest, not one slot: the golem and the hoppers drop the 65th item in a second slot.

- **The reverse recipe is required.** The mod reads the recipes of the game and only compresses an item when a
  recipe packs one repeated ingredient into one block *and* another recipe unpacks that block back into the same
  number of items. Nothing is hard-coded. It is why **quartz is never compressed**: a block of quartz cannot be
  crafted back into quartz.
- **Cascade.** Nuggets become ingots, ingots become blocks, each time the total goes over the cap.
- **Plain items only.** An item with a custom name, enchantments or any other data is never touched, and the
  block never merges with such a pile: renamed emeralds stay emeralds.
- **Nothing is ever lost.** If the block does not fit in the chest, nothing is removed.
- **Crafter first.** Without a Crafter in the slot, nothing is compressed — forced compression included.
- **No cost at rest.** The chest reacts when its content changes; it does not scan every tick. A chest that is
  open can wait up to 5 ticks.

In vanilla 26.3 this compresses 20 rules: bone meal, coal, copper ingots and nuggets, diamonds, dried kelp, emeralds,
gold ingots and nuggets, **wheat into hay bales**, iron ingots and nuggets, lapis lazuli, netherite ingots, raw
copper, gold and iron, redstone, resin and slimeballs.

**Other mods.** Anything built on ordinary shaped or shapeless crafting recipes works with no setup, as long as
the reverse recipe exists. Crate Delight was tested: its crates and their reverse recipes are picked up. A mod
with its own recipe type is ignored.

## Forced compression

Many items have no reverse recipe: quartz, amethyst shards, glowstone dust, snowballs, clay balls, melon slices,
bamboo, string, sand… The three **forced compression** slots lift the reverse-recipe condition for the items you
put in them.

- Put the **real item** in the slot, **one per slot**. It stays there until you take it back.
- The slot only accepts an item that can be packed by a plain crafting recipe — one ingredient, repeated to fill
  a 2×2 or 3×3 grid (or a shapeless recipe of 4 or 9), with no craft remainder and no durability on the result
  — and that the normal compression does not handle already. Anything else bounces back to your cursor.
- Everything else from the section above still applies: the cap, the total, the cascade, the Crafter, nothing
  lost.
- **It is a one-way trip.** Put sand in, you get sandstone; put string in, you get white wool. That is your call.
- A forced chain needs one slot per step: ice, then packed ice, to reach blue ice.
- You can only place an item by **clicking** it into the slot. Shift-click, number keys and dragging do not
  fill these slots, and a double-click never picks up from them.
- The client has no recipes, so the slot looks like it accepts anything; the server decides and an item it
  refuses returns to your cursor.

In vanilla 26.3, 24 items can be forced: amethyst shards, quartz, glowstone dust, snowballs, clay balls, honeycomb,
bamboo, melon slices, nether wart, ice, packed ice, bricks, nether bricks, resin bricks, sand, red sand, string,
rabbit hide (into leather), disc fragments (into music disc 5), prismarine shards, magma cream, pointed dripstone,
sulfur and sulfur spikes.

## Copper golems

A copper golem takes items from a copper chest and **delivers them to a Smart Chest** as it would to a vanilla
chest, single or double: when the chest is empty, or already holds a pile of the same item. Smart Chests are
destinations only — the golem never takes from them. It fills a slot up to the vanilla stack size, and
compression then takes care of the rest.

## Configuration

`config/smartchest.json`, written with its defaults the first time the game starts. Restart the game to apply a
change.

```json
{
  "capacity": { "tiers": [128, 256, 512, 999] },
  "compression": { "enabled": true }
}
```

| Key | Default | Rule |
| --- | --- | --- |
| `capacity.tiers` | `[128, 256, 512, 999]` | four whole numbers, **strictly increasing**, each from 2 to 999 |
| `compression.enabled` | `true` | `false` turns compression off, forced slots included |

- A missing key takes its default.
- A file with an invalid value is **ignored as a whole**: the defaults apply, the log says why, and the file is
  **never overwritten**. Delete it to get a fresh one.
- **The configuration is not synchronised in multiplayer.** Use the same `capacity.tiers` on the client and on the
  server. Different tiers break the client's prediction of clicks, the tooltips and the drag count; the server
  corrects the clicks, and nothing is lost.

## Data pack tags

| Tag | Type | Content |
| --- | --- | --- |
| `#smartchest:golem_destinations` | block | the twelve chests, where a copper golem may deliver; only a block whose block entity is a chest works |
| `#smartchest:no_capacity_upgrade` | item | shulker boxes, bundles and written books: excluded from the capacity upgrades |
| `#smartchest:no_compression` | item | empty. An item listed here is never compressed, and nothing is compressed *into* it either. Use it to settle a conflict between mods' recipes |

The recipes are plain JSON in `data/smartchest/recipe/` and can be overridden by a data pack.

If an item has several compression recipes, the first one found is used; the log lists these cases at startup,
along with the full compression tables.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.19.5** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) — the only dependency
- Java 25

Install it on the **client and on the server**. Removing the mod from a world deletes its chests, and what they
hold, with it.

## Known limits

- A Smart Chest never merges with a vanilla chest or with another wood. Two chests already placed side by side do
  not merge after the fact — that is vanilla behaviour; merging happens when a chest is placed.
- A copy made with the creative pick-block key keeps the contents but not the upgrades.
- The configuration is not synchronised in multiplayer (see above).

## Credits and license

Released under the **MIT License** — see `LICENSE`.

All textures are drawn by script (`tools/`) or by hand; none comes from generative AI. The wood grain of the
chests is derived from the planks textures of Minecraft. Not an official Minecraft product; not approved by or
associated with Mojang or Microsoft.
