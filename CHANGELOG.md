# Changelog

## Unreleased

- Pinned and autopilot-suggested goals no longer look alike: pinned goals show a gold ★, the suggestion a
  small compass (with a tooltip); in the checklist the suggestion has a cyan frame and says so on hover.
  Pinning the suggestion used to look like "a different goal got pinned" because the next suggestion took its place.

## 0.1.1

- Needed items in containers: thicker gold frame and a softly pulsing gold glow behind the item.
- Unpinning: Pin/Unpin button on the detail screen, "Unpin all" in settings and as a key (unbound by default),
  hover hint in the checklist (left click pins or unpins), action-bar message on every pin change.

---

Нужные предметы в сундуках и инвентаре заметнее: толстая золотая рамка и пульсирующая подложка.
Открепление: кнопка «Открепить» на экране «подробно», «Открепить все» в настройках и клавишей,
подсказка при наведении в чек-листе, сообщение над хотбаром при закреплении и откреплении.

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
