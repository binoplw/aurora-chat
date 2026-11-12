<p align="center"><img src="src/main/resources/assets/aurorachat/icon.png" width="96" alt="Aurora Chat"></p>

<h1 align="center">Aurora Chat</h1>

<p align="center">A beautiful, smooth and highly customizable chat for Minecraft (Fabric, client-side).<br>
Красивый, плавный и настраиваемый чат для Minecraft (Fabric, только клиент).</p>

<p align="center">
  <a href="https://binoplw.github.io/aurora-chat/">Website / Сайт</a> ·
  <a href="https://modrinth.com/mod/aurora-ryizen">Modrinth</a> ·
  <a href="../../issues">Bug reports / Баги</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

<p align="center"><a href="https://github.com/binoplw/aurora-chat/actions/workflows/build.yml"><img src="https://github.com/binoplw/aurora-chat/actions/workflows/build.yml/badge.svg" alt="Build"></a></p>

<p align="center"><img src="docs/media/themes.gif" width="640" alt="Aurora Chat themes"></p>

---

## Features

- **Window** — move it by ⠿ and resize like a messenger; Compact / Discord-like / Messenger layouts
- **30+ themes** — Discord, Telegram, WhatsApp, iMessage, Twitch, Steam, Spotify and more; share your theme as a code
- **Channels & tabs** — global / local chat (G / L) with badges; tabs All, G, L, DM, Clan, System and your own; extra windows
- **Translator** — right-click a message → Translate; the ⇄ button translates what you type
- **Convenience** — right-click menu (copy with colors, reply, mention, pin), smart search (`nick:` `channel:` `type:` `date:`), history across restarts, `&` color palette with gradients, emoji, Tab name completion, scheduled messages, reminders, AFK auto-reply, anti-spam, word filter
- **Performance** — about 1 ms per frame with a 2000-message history, low-FPS saving mode
- **Languages** — English and Russian (follows the game language)

## Возможности

- **Окно** — двигается за ⠿ и растягивается; раскладки: компактная, как в Discord, мессенджер
- **30+ тем** — Discord, Telegram, WhatsApp, iMessage, Twitch, Steam, Spotify и другие; обмен темами кодом
- **Каналы и вкладки** — G / L со значками, вкладки Все, G, L, ЛС, Клан, Система и свои, отдельные окна
- **Переводчик** — ПКМ по сообщению → «Перевести», кнопка ⇄ переводит то, что пишешь
- **Удобство** — меню по ПКМ, умный поиск, история между запусками, палитра «&» с градиентами, эмодзи, ники по Tab, отложенные сообщения, напоминания, АФК-автоответчик, антиспам, фильтр слов
- **Не лагает** — около 1 мс на кадр при истории в 2000 сообщений

## Free and Aurora Chat+

The basic chat is free: movable window, 3 themes, G / L channels, All / G / L tabs.
**Aurora Chat+** (subscription on Boosty, 250 ₽ / month; monthly key tied to your Minecraft name) unlocks everything else — get it on the [website](https://binoplw.github.io/aurora-chat/#premium), then `/achat key <key>`.
This repository contains the free part; the code of Aurora Chat+ features is not published (stubs with the same API are in its place), so a build from source is the free edition.

Базовый чат бесплатный. **Aurora Chat+** (подписка на Boosty, 250 ₽ в месяц; ключ на ник на месяц) открывает всё остальное. В репозитории — бесплатная часть, код функций Aurora Chat+ не публикуется.

## Requirements

Minecraft **26.1–26.1.2, 26.2 or 26.3**, Fabric Loader, **Fabric API**. [Mod Menu](https://modrinth.com/mod/modmenu) is optional.
Settings: `/achat` or ⚙ in the chat header.

## Building

Java 25 is required.

```
gradlew build                 # build/libs/aurora-chat-<version>-public.jar
gradlew runClientGameTest     # in-game tests (opens a test world, takes screenshots)
```

`libs/` contains a prebuilt [Aurora Lib](libs) (performance helpers shared by Aurora mods); it is bundled into the mod jar.

### Translations

UI texts are written in Russian in the code and translated at display time using
`src/main/resources/assets/aurorachat/i18n/en.json` (key — Russian text, value — translation).
To add strings: `python i18n/extract_ui.py src/main/java/net/primlixe/aurorachat i18n/ui_ru.txt`, add lines
`number<TAB>translation` to `i18n/en_N.txt`, then `python i18n/build_dict.py src/main/resources/assets/aurorachat/i18n/en.json`.
Pull requests with new languages are welcome.

## License

[MIT](LICENSE)
