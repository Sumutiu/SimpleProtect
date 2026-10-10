# 🟩 SimpleProtect – Your build matters!

**SimpleProtect** is a lightweight and fully server-side mod for **Fabric** and **NeoForge** that brings a simple, emerald-block-based protection system to your Minecraft world.  
Farm emeralds, craft them into blocks, and use them to safeguard your builds from griefers.  

Perfect for survival servers, SMPs, and creative builders who want peace of mind.

---

## ✨ Features

- 🟩 Protect your builds using emerald blocks *(the block can be changed in the config)*  
- 🛡️ Each protection block protects a **61×61 area** around it (30 blocks in every direction, configurable), from bottom to top of the world, in that dimension  
- ➕ Place more protection blocks near the edge to make your protection bigger  
- 👥 Share access by adding friends to your protection list, even while they are offline  
- ❌ Remove players from access at any time, even while they are offline  
- 💥 Protected from explosions, fire, pistons, dispensers and flowing water and lava  
- 🐄 Animals, villagers, armor stands, item frames and vehicles are protected too  
- 🔔 See a message when you enter or leave someone's protection  
- 🛠️ Admin tools: bypass, list and delete protections  
- 📁 Per-protection data stored in easy-to-read JSON files  
- 🌍 100% server-side — no client mod required!  

---

## 🛡️ How it works

- Place a protection block to create a protection around it. Only you and the players you add can build there.  
- **Make it bigger:** place another protection block inside your protection, at least 15 blocks (on X or Z) from your other protection blocks. It adds its own area to your protection.  
- Protection blocks closer than that (like a beacon base) are just normal blocks, so decorating with them doesn't create extra protections.  
- Connected protection blocks are one protection: players you add or remove apply to all of it.  
- Break a protection block to remove its area. Nobody else can break it.  
- Protections of different players can't overlap. A protection block placed where it would overlap someone else's protection is placed as a normal block.  

Inside someone else's protection you can't:

- 🧱 Break or place blocks  
- 🚪 Use blocks (chests, doors, buttons, levers, furnaces...)  
- 🪣 Use buckets (lava, water) or place lily pads  
- 🔥 Light fires  
- 🐄 Hurt or use animals, villagers, armor stands, item frames, boats and minecarts, yourself or with your tamed pets (monsters can always be fought, and so can anything attacking you)  

And from outside it:

- 💥 Explosions don't destroy blocks inside, and creepers can't hurt the animals and decorations there  
- 🔥 Fire doesn't burn blocks inside  
- 🧲 Pistons can't push, pull or break blocks inside, or push blocks in  
- 🌊 Water and lava don't flow in  
- 🎯 Dispensers can't place lava, water, fire or blocks inside, or shoot into it  

---

## 🔧 Commands

Stand inside your protection to use `add` and `remove`:

- `/sprotection add <player>` – Grant a player access to your protection (online, or anyone who joined the server before)  
- `/sprotection remove <player>` – Remove a player’s access to your protection (works for offline players too)  
- `/sprotection info` – Who owns the protection you're standing in, and who may build there  
- `/sprotection list` – Your protection blocks and where they are  

Operators only:

- `/sprotection bypass` – Build anywhere, in every protection (run it again to turn it off)  
- `/sprotection delete` – Delete the protection you're standing in (all its connected protection blocks)  
- `/sprotection list <player>` – Another player's protection blocks (also offline)  

---

## ⚙️ Configuration

The config is created on the first start at:  
`/config/SimpleProtect_Seed_<world seed>/SimpleProtect.json`

| Setting | Default | What it does |
|---|---|---|
| `SimpleProtect_Radius` | `30` | How far a protection reaches from its block, on X and Z |
| `SimpleProtect_Block` | `minecraft:emerald_block` | The block that creates protections |
| `SimpleProtect_MaxProtectionsPerPlayer` | `10` | Most protection blocks one player can have, extensions included (`0` = no limit) |
| `SimpleProtect_MinExtensionDistance` | `15` | How far from your other protection blocks a new one must be to make your protection bigger |
| `SimpleProtect_ProtectEntities` | `true` | Protect animals, villagers, armor stands, item frames and vehicles |
| `SimpleProtect_ProtectFromExplosions` | `true` | Explosions don't destroy blocks inside protections |
| `SimpleProtect_ProtectFromFireSpread` | `true` | Fire doesn't burn blocks inside protections |
| `SimpleProtect_ProtectFromPistons` | `true` | Pistons can't move blocks into, out of, or inside someone else's protection |
| `SimpleProtect_ProtectFromFluids` | `true` | Water and lava don't flow into protections |
| `SimpleProtect_ProtectFromDispensers` | `true` | Dispensers outside a protection can't dispense into it |

Changes take effect after a server restart. Changing the radius changes the size of existing protections too.

---

## 📁 Data

Protections are saved in `/mods/SimpleProtect_Seed_<world seed>/SimpleProtect.json`.  
If the file can't be read, the mod doesn't start (players can't join) and the file is left untouched, so no protection is lost.

---

## 🧩 Requirements

**Fabric**
- [Fabric Loader](https://fabricmc.net/use/)  
- [Fabric API](https://modrinth.com/mod/fabric-api)  

**NeoForge**
- [NeoForge](https://neoforged.net/) (nothing else needed)  

---

## 🛠️ Building

The project builds both loaders from one shared codebase:

- `common/` – the mod itself (protections, commands, config, storage, most mixins), plain Minecraft code  
- `fabric/` – the Fabric entrypoint, `fabric.mod.json` and the Fabric-only fire mixin  
- `neoforge/` – the NeoForge entrypoint, `neoforge.mods.toml` and the NeoForge-only fire mixin  

Run `gradlew build` (Java 25). The jars are created in:

- `fabric/build/libs/simpleprotect-fabric-<version>.jar`  
- `neoforge/build/libs/simpleprotect-neoforge-<version>.jar`  

Versions (Minecraft, Fabric, NeoForge) are all set in `gradle.properties`.

---

## 🌟 Why SimpleProtect?

Because **your build matters!**  
SimpleProtect keeps your world safe from griefers without complicated claim systems or confusing GUIs.  

Just place emerald blocks, protect your area, and build with confidence.

---

## 📜 License

This mod is licensed under the GNU AGPLv3 Licence.  

---

## 💬 Feedback

Found a bug or have a feature suggestion?  
Open an issue or PR on GitHub!  
