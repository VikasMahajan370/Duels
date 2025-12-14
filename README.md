# Duels

A modern, feature-rich duels plugin for Paper 1.21+ servers with professional **1.8 Legacy PvP** combat mechanics.

## Features

- **Queue System** — Unranked & Ranked matchmaking with ELO ratings
- **Custom Kits** — Create kits from inventory with configurable GUI icons
- **Arena Management** — Multiple arenas with spawn points
- **Kit Editor** — Players can customize their kit layouts
- **Leaderboard** — Top players with PlaceholderAPI support
- **Anti-Cheat** — Built-in Flight, KillAura, and Reach detection
- **Discord Logging** — Webhook or Bot integration for events
- **Multi-Language** — English and Turkish out of the box
- **1.8 Legacy PvP** — Full combat system matching Minecraft 1.8 behavior
- **Storage** — SQLite (default) or MySQL

## 1.8 Legacy PvP System

The plugin includes a comprehensive **1.8 combat system** that works 100% server-side:

### Combat Mechanics
- ⚔️ **No Attack Cooldown** — Spam-click PvP like 1.8
- 🎯 **1.8-Style Knockback** — Consistent horizontal/vertical KB with sprint-reset
- 🛡️ **Fake Sword Blocking** — Right-click damage reduction (mechanic only, no animation)
- 💎 **Normalized Damage** — Weapon damage values match 1.8
- ❌ **No Critical Randomness** — Crits are removed for competitive play
- ❌ **No Sweeping Edge** — Disabled completely
- ❌ **Shields Disabled** — Cannot use or equip shields in duels
- 🍎 **1.8 Golden Apples** — Original absorption/regeneration values

### Per-Kit Knockback Presets

Each kit can have custom knockback settings for unique gameplay styles:

```yaml
kits:
  Combo:
    icon: GOLDEN_APPLE
    items: [...]
    knockback:
      horizontal: 0.42      # Higher KB for longer combos
      vertical: 0.36
      rod-horizontal: 0.70  # Custom rod knockback
      rod-vertical: 0.45

  Sumo:
    icon: STICK
    knockback:
      horizontal: 0.55      # Very high KB for knockoff
      vertical: 0.42
```

### Config Options

```yaml
combat:
  legacy-pvp: true
  knockback:
    horizontal: 0.36
    vertical: 0.32
    sprint-multiplier: 1.0
    kb-enchant-multiplier: 0.4
    rod-horizontal: 0.65
    rod-vertical: 0.40
  sword-block:
    enabled: true
    damage-reduction: 0.5
    cooldown-ticks: 6
  damage:
    use-1-8-values: true
    remove-crits: true
    remove-sweeping: true
  golden-apple:
    remove-cooldown: true
    use-1-8-effects: true
  shields:
    disable-completely: true
```

## Requirements

- Java 21+
- Paper 1.21+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- Compatible with: ViaVersion / ViaBackwards / ViaRewind

## Installation

1. Drop the JAR into `plugins/`
2. Restart the server
3. Configure `config.yml`

## Commands

| Command | Description |
|---------|-------------|
| `/duel invite <player> [kit]` | Send a duel request |
| `/duel accept/deny <player>` | Accept or deny requests |
| `/lobby` | Return to lobby |
| `/stats [player]` | View statistics |
| `/leaderboard` | Open leaderboard GUI |
| `/ping` | View connection info |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/duel admin reload` | Reload configuration |
| `/duel admin kit create <name>` | Create kit from inventory |
| `/duel admin arena create <name>` | Create a new arena |
| `/duel admin arena setspawn <name> <1\|2\|spectator>` | Set arena spawns |
| `/duel admin setlobby` | Set lobby location |

## PlaceholderAPI

```
%duels_top_1_name%    %duels_top_1_wins%
%duels_top_2_name%    %duels_top_2_losses%
%duels_top_3_name%    %duels_top_3_kills%
...up to position 10
```

## Permissions

| Permission | Description |
|------------|-------------|
| `duels.admin` | Access to admin commands |
| `duels.anticheat.alerts` | Receive anti-cheat alerts |
| `duels.chat.color` | Use color codes in chat |
| `duels.chat.bypass` | Bypass chat cooldown |

## Building

```bash
mvn clean package
```

## Architecture

```
me.raikou.duels
├── combat/           # 1.8 Legacy PvP system
│   ├── CombatManager
│   ├── DamageHandler
│   ├── KnockbackHandler
│   ├── LegacyBlockHandler
│   ├── ShieldHandler
│   └── GoldenAppleHandler
├── anticheat/        # Anti-cheat detection
├── arena/            # Arena management
├── duel/             # Duel logic
├── kit/              # Kit system
├── queue/            # Matchmaking
└── ...
```

## License

[MIT License](LICENSE)

