# AGENTS.md

Инструкции для `xlpvp`.

- Отвечай на русском, коротко и по делу.
- Текстовые файлы сохраняй в UTF-8 без BOM.
- Для поиска используй `rg` / `rg --files`.
- Сборку Gradle можно запускать для проверки изменений.
- Не трогай чужие несвязанные изменения.

## Что это

`xlpvp` - NeoForge-мод PvP-механик для Minecraft 1.21.1, сторона `BOTH`.
Он приближает боевку к classic/1.8-style PvP и добавляет отдельное зачарование скорости.

## Что меняет

- Mixin `PlayerAttackMixin` ограничивает частоту атак под classic PvP вместо ванильного 1.9+ cooldown.
- `ClassicPvpHandler` ускоряет атаку, меняет knockback, sprint-hit и обработку ударов.
- `FishingRodKnockbackHandler` возвращает PvP knockback от удочки.
- Клиентский `AutoSprintHandler` автоматически включает sprint при движении.
- Клиентский `AttackSoundHandler` убирает лишние ванильные attack sounds и добавляет ожидаемый hurt sound.
- Добавляет enchantment `xlpvp:speed` и inject loot book в treasure chests.
- `SpeedAnvilHandler` разрешает переносить/повышать Speed через наковальню.
- `AnvilScreenMixin` показывает высокую стоимость ремонта/зачарования выше ванильного лимита.

## Важные файлы

- `src/main/java/com/xlpvp/Core.java` - точка входа мода.
- `src/main/java/com/xlpvp/main/ClassicPvpHandler.java` - основная PvP-логика.
- `src/main/java/com/xlpvp/main/FishingRodKnockbackHandler.java` - удочка и knockback.
- `src/main/java/com/xlpvp/client/` - клиентские sprint/sound фиксы.
- `src/main/java/com/xlpvp/main/speed/` - enchantment Speed.
- `src/main/resources/xlpvp.mixins.json` - mixin-ы.
- `src/main/resources/data/xlpvp/` - enchantment, loot modifier.

## Проверка

- `./gradlew build`
