# Minecraft But I Need To Mine All Dirt From The World (Fabric 1.21.11)

- Mine dirt / grass / podzol / mycelium / coarse dirt / path / farmland -> you get a **Dirt Token** (gold coin) instead.
- You start with a **Shop Portal**. Place it anywhere, right-click it (not sneaking) to travel to the Dirt Shop. Right-click the portal inside the shop to go back. Break the portal to pick it up. `/shopportal` gives a new one.
- Tokens in your inventory are banked automatically when you open the shop. `/dirtbank` shows your balance.
- Talk to the **Dirt Merchant** to buy 6 levels of items:
  1. Dirt Digger (50)  2. Dirt Grenade x8 (300)  3. Dirt Nuke (2,500)
  4. Chunk Eraser (10,000)  5. Orbital Dirt Cannon (80,000)  6. PLANET ERASER (500,000)
- Erasing tools pay out bonus tokens for every dirt block they remove.

Edit prices in `Shop.java`, radii and multipliers at the top of `DirtTools.java`.
