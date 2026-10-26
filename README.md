<div align="center">

# SimpleEconomy

**A high-performance, modular economy suite featuring a Category Shop, Player Auction House, Scrap Buyer, and Real-Time Dynamic Market Engine for Paper, Purpur, Spigot, and Folia.**

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20+-2b2b2b.svg?style=flat-square)](https://papermc.io)
[![Java Version](https://img.shields.io/badge/Java-17+-2b2b2b.svg?style=flat-square)](https://www.oracle.com/java/)
[![Folia](https://img.shields.io/badge/Folia-Supported-2b2b2b.svg?style=flat-square)](https://papermc.io/software/folia)
[![Release](https://img.shields.io/badge/Release-1.3-2b2b2b.svg?style=flat-square)]()
[![License](https://img.shields.io/badge/License-MIT-2b2b2b.svg?style=flat-square)](LICENSE)

[Overview](#overview) • [Key Features](#key-features) • [Compatibility](#compatibility) • [Commands and Permissions](#commands-and-permissions) • [Configuration](#configuration) • [Localization](#localization) • [Installation](#installation) • [Compiling from Source](#compiling-from-source)

</div>

---

## Overview

SimpleEconomy is an all-in-one, high-throughput economy infrastructure designed for modern Minecraft server networks. Standard economy setups typically depend on multiple legacy plugins for shops, auctions, and scrapper merchants, which frequently cause main-thread database hitches, race conditions during purchases, and static, inflation-prone financial systems.

SimpleEconomy consolidates these services into a single, cohesive architecture. It combines asynchronous database pipelines, thread-safe atomic transaction handling, native Folia regional scheduler integration, and an autonomous supply-and-demand dynamic market engine. In addition, the plugin includes official localized Minecraft item names across 13 languages, eliminating unformatted technical material keys from graphical user interfaces.

---

## Key Features

- **Categorized Server Shop (`/shop`)**
  - Section-based graphical navigation with automated multi-page support.
  - Dedicated quantity selection interface supporting step adjustments (`+1`, `-1`, `+10`, `-10`, stack of 64, or custom quantities up to 640 units).
  - Progressive Item Lock mechanism: limits purchasable inventory to an administrator-configured percentage per category, with permission-based bypass capability.
  - Sound feedback for navigation, quantity selection, purchase validation, and error states.

- **Player Auction House (`/ah`)**
  - Player-to-player trading platform with customizable maximum listing slots per user.
  - Anti-sniping staging delay: newly listed items enter a temporary countdown window before becoming purchasable, preventing automated bot sniping.
  - Isolated Claims Storage GUI: stores purchased, expired, and cancelled items safely, avoiding inventory overflow drops.
  - Atomic database transactions ensuring protection against duplicate claims and concurrent purchase conflicts.
  - Configurable listing cooldowns (including zero-cooldown instant listing).

- **Scrap Buyer (`/buyer`)**
  - Mass-selling interface allowing players to deposit multiple inventory items simultaneously for instant liquidation.
  - Live emerald balance monitor displaying updated Vault player funds in real time.
  - Built-in, paginated price catalog displaying per-unit and 64-unit stack payouts for every accepted resource.
  - Safe return fallback: unaccepted items are returned to player inventories or dropped safely at their feet if full.

- **Real-Time Dynamic Market Engine**
  - Autonomous supply and demand mechanics active across both `/shop` and `/buyer`.
  - Shop buy prices increase with continuous player purchases and gradually decay downward toward baseline discounts during prolonged inactivity.
  - Buyer scrap payouts decrease during heavy market saturation and recover toward premium payouts during resource scarcity.
  - Bounded by configurable minimum floor and maximum ceiling multipliers to prevent runaway inflation or deflation.
  - Scheduled background decay task with database persistence (`se_dynamic_market`) across server restarts.
  - Real-time visual market trend indicators rendered directly in item tooltips (`▲ +%`, `▼ -%`, `● Base Price`).

- **Multi-Threading and Folia Compatibility**
  - Full compatibility with Folia's regionalized multi-threading model using `EntityScheduler`, `GlobalRegionScheduler`, and `AsyncScheduler`.
  - Non-blocking HikariCP connection pooling supporting SQLite (WAL mode) and MySQL/MariaDB.
  - All database queries and file writes execute asynchronously, guaranteeing zero main-thread performance degradation.

- **Native Multi-Language and Item Translation**
  - 13 built-in languages with zero external dependencies.
  - Bundled translation properties tables covering more than 1,200 vanilla items, ensuring players see authentic client translations in their native language.

---

## Compatibility

| Platform | Supported Versions | Implementation Details |
| :--- | :--- | :--- |
| **Folia** | 1.20 – 1.21.x+ | Native regional entity, global, and asynchronous schedulers |
| **Paper** | 1.20 – 1.21.x+ | Asynchronous Paper task schedulers and modern inventory events |
| **Purpur** | 1.20 – 1.21.x+ | Fully supported |
| **Spigot** | 1.20 – 1.21.x+ | Supported with standard Bukkit asynchronous scheduler fallback |

---

## Commands and Permissions

### Commands

| Command | Aliases | Description | Default Permission |
| :--- | :--- | :--- | :--- |
| `/shop` | `/eshop` | Opens the server shop category interface | `simpleeconomy.shop.use` |
| `/ah` | `/auction` | Opens the player auction house interface | `simpleeconomy.auction.use` |
| `/ah sell <price>` | `/auction sell` | Lists the item currently held in main hand | `simpleeconomy.auction.use` |
| `/buyer` | `/scrapper`, `/sell` | Opens the scrap buyer bulk selling interface | `simpleeconomy.buyer.use` |
| `/simpleeconomy reload` | `/se reload` | Reloads configurations, shops, market state, and languages | `simpleeconomy.admin` |

### Permissions

| Node | Description | Default Assignment |
| :--- | :--- | :--- |
| `simpleeconomy.shop.use` | Allows opening and purchasing items within `/shop` | `true` (all players) |
| `simpleeconomy.shop.bypass` | Bypasses category item lock limits in `/shop` | `op` |
| `simpleeconomy.auction.use` | Allows viewing, buying, and listing items on `/ah` | `true` (all players) |
| `simpleeconomy.buyer.use` | Allows opening and selling resources via `/buyer` | `true` (all players) |
| `simpleeconomy.admin` | Access to administrative reload command and all system bypasses | `op` |

---

## Configuration

The default configuration file (`config.yml`):

```yaml
# =======================================================
#             SimpleEconomy Plugin Configuration
# =======================================================

# Active plugin language
# Supported: en, ru, zh, de, fr, es, pt, ja, uk, it, pl, tr, ko
language: "en"

# Currency formatting pattern
# Available variables: %amount%, %symbol%
currency:
  symbol: "$"
  format: "%amount%%symbol%"

# GUI sound effects
sounds:
  enabled: true

# Modular system activation
modules:
  shop: true
  auction: true
  buyer: true

# Database storage configuration
database:
  type: "sqlite"
  sqlite:
    file: "database.db"
  mysql:
    host: "localhost"
    port: 3306
    database: "simple_economy"
    username: "root"
    password: ""
    pool-size: 10
    connection-timeout: 30000

# Auction House (/ah) parameters
auction:
  max_slots: 5
  listing_cooldown_seconds: 0
  publish_delay_seconds: 5
  expire_hours: 48
  min_price: 1.0
  max_price: 100000000.0

# Scrap Buyer (/buyer) parameters
buyer:
  sound_enabled: true
  drop_items_on_close_if_full: true

# Server Shop (/shop) parameters
shop:
  close_on_buy: false
  item_lock:
    enabled: false
    allowed_percentage: 100
    bypass_permission: "simpleeconomy.shop.bypass"

# Dynamic Market Engine (Real Market Mechanics)
dynamic_market:
  enabled: true
  change_percent_per_item: 0.1
  min_price_multiplier: 0.5
  max_price_multiplier: 3.0
  decay_interval_seconds: 300
  decay_percent: 1.5
  show_trends: true
```

---

## Localization

Pre-configured interface languages and item dictionaries are located in `plugins/SimpleEconomy/languages/`:

| Code | Language | Native Name | Interface File | Item Dictionary |
| :---: | :--- | :--- | :--- | :--- |
| `en` | English | English | `languages/en.yml` | `languages/items/en.properties` |
| `ru` | Russian | Русский | `languages/ru.yml` | `languages/items/ru.properties` |
| `zh` | Chinese (Simplified) | 简体中文 | `languages/zh.yml` | `languages/items/zh.properties` |
| `de` | German | Deutsch | `languages/de.yml` | `languages/items/de.properties` |
| `fr` | French | Français | `languages/fr.yml` | `languages/items/fr.properties` |
| `es` | Spanish | Español | `languages/es.yml` | `languages/items/es.properties` |
| `pt` | Portuguese | Português | `languages/pt.yml` | `languages/items/pt.properties` |
| `ja` | Japanese | 日本語 | `languages/ja.yml` | `languages/items/ja.properties` |
| `uk` | Ukrainian | Українська | `languages/uk.yml` | `languages/items/uk.properties` |
| `it` | Italian | Italiano | `languages/it.yml` | `languages/items/it.properties` |
| `pl` | Polish | Polski | `languages/pl.yml` | `languages/items/pl.properties` |
| `tr` | Turkish | Türkçe | `languages/tr.yml` | `languages/items/tr.properties` |
| `ko` | Korean | 한국어 | `languages/ko.yml` | `languages/items/ko.properties` |

---

## Installation

1. Download `simple-economy-1.3.jar` from the release section.
2. Place the JAR file into your server's `plugins/` directory.
3. Ensure `Vault` and an economy service provider (e.g., `EssentialsX`) are installed.
4. Start or restart the server to generate default configurations, shops, and database tables.
5. Modify `plugins/SimpleEconomy/config.yml` according to your server requirements.
6. Execute `/simpleeconomy reload` to apply updates without server downtime.

---

## Compiling from Source

### Requirements
- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.8+
- Git

### Build Instructions

```bash
git clone https://github.com/NothingFlare/SimpleEconomy.git
cd SimpleEconomy
mvn clean package
```

The resulting binary will be located in:
```text
target/simple-economy-1.3.jar
```

---

## License

This project is licensed under the [MIT License](LICENSE).
