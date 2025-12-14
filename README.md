# Duels

A modern, feature-rich duels plugin for Paper 1.21+ servers.

## Features

- **Queue System** — Unranked & Ranked matchmaking with ELO ratings
- **Custom Kits** — Create kits from inventory with configurable GUI icons
- **Arena Management** — Multiple arenas with spawn points
- **Kit Editor** — Players can customize their kit layouts
- **Leaderboard** — Top players with PlaceholderAPI support
- **Anti-Cheat** — Built-in Flight, KillAura, and Reach detection
- **Discord Logging** — Webhook or Bot integration for events
- **Multi-Language** — English and Turkish out of the box
- **Legacy PvP** — Optional 1.8-style combat mechanics
- **Storage** — SQLite (default) or MySQL

## Requirements

- Java 21+
- Paper 1.21+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

## Installation

1. Drop the JAR into `plugins/`
2. Restart the server
3. Configure `config.yml`

## Commands

| Command | Description |
|---------|-------------|
| `/duel <player> [kit]` | Send a duel request |
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

## Building

```bash
mvn clean package
```

## License

[MIT License](LICENSE)
