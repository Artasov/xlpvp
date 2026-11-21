xlpvp
===================================
Client Side & Server Side mod `1.21.1` `NeoForge` `21.1.117` (not tested on others).
It will return to the game a normal PVP of 1.7-1.8 versions.

1) Removes the delay between beats.
2) When a fishing rod hits an entity, it gets knocked back.
3) Better endless auto-sprint


The mod is installed in the `mods` folder of your game directory.

## Запись дуэли на сервере

С установленным `xl-minigames` администратор может ввести `/dueltrace <ник>` перед дуэлью или во время неё. Записываются оба участника одной дуэли. Матч завершает запись автоматически. Команда `/dueltrace stop` останавливает её вручную, а `/dueltrace status` показывает состояние и имя файла.

Журнал находится в `logs/duel-traces/` каталога Minecraft. Каждая строка — одно JSON-событие. Заголовок содержит время UTC, UUID матча и участников, ники, кит и видимые серверу IP-адреса. На Linux файл доступен владельцу. Последняя строка содержит причину завершения, количество пропущенных записей и счётчики боевых событий для каждого участника.

- `seq`, `tick`, `us` задают порядок, серверный тик и микросекунды с начала записи. `state` показывает серверные координаты и оценку пинга каждого игрока. `tick.interval_us` позволяет заметить длинные тики.
- Для удара мечом сопоставьте `in_attack`, `melee_attempt`, `melee_rejected` и `melee_damage_result`. Причины `reach`, `hit_lock` и `using_item` обозначают дальность, защиту после попадания и использование предмета. Взмах `in_swing` сам по себе не доказывает, что клиент отправил атаку по сопернику.
- Для удочки сопоставьте `rod_entity_impact`, `rod_motion_sent`, `out_velocity`, последующие `in_move` жертвы и `out_track_move` сопернику. У исходящего события `actor` — получатель пакета. `netToMainUs` у входящего пакета измеряет время от его приёма сервером до обработки на основном потоке. Точный момент показа отскока на экране известен только клиенту.
