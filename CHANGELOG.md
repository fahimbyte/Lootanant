# Changelog

## [2.1.0] - 2026-02-15

### Added
- **Royal Ledger (Activity Feed)**: Introduced a glassmorphism-styled activity feed to track game events (bribes, loans, taxation, round results, income phase).
- **Spectator Leave Functionality**: Spectators can now leave the room via a "🚪 Leave" button.
- **Room Status in Lobby**: Added status indicators ("waiting" or "in-game") for rooms in the available rooms list.

### Changed
- **Spectator Experience**: Tax details overlay now auto-dismisses for spectators after 5 seconds, and spectators auto-confirm tax phases to avoid blocking players.
- **Tax Calculation**: Standardized taxation to be 25% of Net Worth (rounded to nearest whole number), deducted from cents.
- **Improved Sync**: `roundNum` is now accurately synced from the server state.
- **Memory Management**: Added automatic purging of stale/finished rooms after 30 minutes of inactivity.

### Fixed
- **Double-Bidding Bug**: Resolved an issue where rapid bidding/passing could cause stale state broadcasts.
- **Tax Rounding**: Fixed a 5¢ discrepancy in the King's Vault due to incorrect rounding in taxation.
- **Thread Safety**: Synchronized game state mutations in scheduled tasks to prevent race conditions.

## [2.0.3] - 2026-02-14

### Added
- **Rage Mode**: A new intensive game mode with enhanced mechanics.
- **Bribe Mechanic**: Players can now bribe each other to influence game outcomes.
- **Loan System**: Introduced the ability for players to take loans during the game.
- **Taxation Phase**: Added a taxation phase to balance the game economy.
- **CPU AI Enhancements**: Improved CPU behavior, including specific actions for Rage Mode.
- **Room Management**: Support for different game modes when creating a room.

### Changed
- Updated `pom.xml` to version 2.0.1.
- Significant UI updates in `index.html` to support new mechanics and Rage Mode.

## [1.1.0] - 2026-02-13

### Added
- **Spectator Mode**: Users can now join existing games as spectators.
- **Reconnection Support**: Players can reconnect to an ongoing game if they get disconnected.
- **Room Browsing**: Added ability to see and join available rooms from the main menu.
- **Custom Room Settings**: Hosts can now configure winning net worth and starting cents.
- **Leave Room**: Functionality for players to safely leave a room.

### Fixed
- General bug fixes and stability improvements across service and controller layers.

## [1.0.0] - 2026-02-12

### Added
- Initial release of Lootanant.
- Core multiplayer auction game mechanics.
- Basic CPU players for solo play.
- WebSocket-based real-time communication.
- Web-based user interface.

[2.0.1]: https://github.com/fahimbyte/Lootanant/compare/v1.1.0...v2.0.1
[1.1.0]: https://github.com/fahimbyte/Lootanant/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/fahimbyte/Lootanant/releases/tag/v1.0.0
