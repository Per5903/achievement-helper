# Changelog

## 0.1.0

First release, for Minecraft 26.2 (Fabric). Client-side only: works in singleplayer and on any server.

- **Goals panel (HUD)**: pinned advancements plus an autopilot suggestion (easiest first). Icons show what is
  still missing: foods, biomes, mobs, cat/wolf/frog variants as real models. Drag it anywhere, any corner, any size.
- **Icon hints for every vanilla single-action advancement**, e.g. `raw iron + furnace → iron ingot`,
  with a short note only where words are unavoidable.
- **Checklist (J)**: every advancement as a coloured cell, filters (can do now / started / easy / pinned / done),
  left click pins, right click opens the detail view: everything left and done, with names.
- **Breeding and taming**: what to feed each animal for Two by Two, what tames cats and wolves.
- **In the world**: gold outline on mobs the goals need and on needed items on the ground; gold frame on needed
  items in chests and the inventory; reminder when entering a biome a goal asks for; "+1" progress toasts.
- **Settings** screen (comparator in the checklist, or Mod Menu). English and Russian.
- Modpacks and mods can add hints for their own advancements in `assets/<ns>/achievehelper_hints/*.json`.

---

Первый релиз для Minecraft 26.2 (Fabric). Только клиент: работает в одиночной игре и на любых серверах.
Панель целей с иконками недостающего, подсказки-цепочки для всех ванильных ачивок, чек-лист с фильтрами и
подробным просмотром, корм для разведения, подсветка нужных мобов и предметов, напоминания о биомах, настройки.
