# 🧈 Lootanant — Gold Bar Auction!

A real-time, web-based multiplayer auction board game built with **Java/Spring Boot** and vanilla **HTML5/CSS/JS**. Players compete to accumulate **50 Net Worth** by bidding on high-purity gold bars — bluffing, strategizing, and outmaneuvering opponents to become **The Lootanant**.

---

## 🎮 Game Overview

The King is hoarding the world's purest Gold Bars. As a budding thief, your goal is to prove you have the strategic mind to become his Lieutenant — the **Lootanant**.

### Rules at a Glance

| Concept | Detail |
|---------|--------|
| **Players** | 2–8 (human or CPU) + Spectators |
| **Starting Money** | 12 ¢ per player (configurable) |
| **Starting Net Worth** | 0 |
| **Gold Bars** | Random purity 1k–24k each round |
| **Bidding** | Clockwise; each bid must be higher than the last |
| **Winning a Round** | Last bidder standing pays their bid, gains the bar's purity as Net Worth |
| **Losing a Bid** | Money is refunded when outbid |
| **Passing** | Locks you out of the current round |
| **Income** | +1 ¢ for **all** players after each round (sold or discarded) |
| **Victory** | First player to reach **50 Net Worth** wins! (configurable) |

### Strategic Depth

- You can see opponents' **Net Worth** but **not** their money — bluffing is key.
- Overbidding drains your funds; underbidding lets opponents grab high-value gold bars cheaply.
- Sometimes passing is the smartest move — let others waste their money!

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.4.3 |
| **Real-time** | WebSocket (STOMP over SockJS) |
| **Frontend** | Single-page HTML5/CSS3/JavaScript (no framework) |
| **Build** | Maven (wrapper included) |
| **Database** | None — in-memory `ConcurrentHashMap` |

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** or later
- No other dependencies required (Maven wrapper included)

### Run the Application

```bash
# Clone the repository
git clone <repo-url>
cd Lootanant

# Build and run (Unix/macOS)
./mvnw spring-boot:run

# Build and run (Windows)
mvnw.cmd spring-boot:run
```

The app starts at **http://localhost:8080**.

### Run Tests

```bash
./mvnw test
```

---

## 📁 Project Structure

```
Lootanant/
├── pom.xml                          # Maven config (Spring Boot 3.4.3, Java 21)
├── src/
│   ├── main/
│   │   ├── java/imperfect/lootanant/
│   │   │   ├── LootanantApplication.java    # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── WebSocketConfig.java     # STOMP/SockJS WebSocket setup
│   │   │   ├── controller/
│   │   │   │   └── GameController.java      # REST API endpoints
│   │   │   ├── model/
│   │   │   │   ├── GameRoom.java            # Room state (players, bids, deed)
│   │   │   │   └── Player.java              # Player state (money, net worth)
│   │   │   └── service/
│   │   │       └── GameService.java         # Core game logic & AI
│   │   └── resources/
│   │       ├── application.properties       # Server config
│   │       └── static/
│   │           └── index.html               # Complete single-page game UI
│   └── test/
│       └── java/imperfect/lootanant/
│           └── LootanantApplicationTests.java
├── instructions.txt                 # Original game design blueprint
├── part1.txt                        # Detailed gameplay specification
└── bugs.txt                         # Bug/improvement tracker
```

---

## 🔌 API Reference

All endpoints are under `/api`. Request/response bodies are JSON.

### Room Management

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/create` | `{ "name": "Host" }` | Create a new room. Returns `roomCode`, `playerId`, `hostId`. |
| `GET` | `/api/rooms` | — | Get list of available rooms (code and host name). |
| `POST` | `/api/join` | `{ "roomCode": "AB3XY", "name": "Player" }` | Join an existing room. Returns `playerId`. |
| `POST` | `/api/spectate` | `{ "roomCode": "AB3XY" }` | Join as a spectator. Returns `playerId`. |
| `POST` | `/api/reconnect` | `{ "roomCode": "AB3XY", "playerId": "..." }` | Reconnect to an existing session. |
| `POST` | `/api/leave` | `{ "roomCode": "AB3XY", "playerId": "..." }` | Leave the game (progress reset, CPU takes over). |
| `POST` | `/api/addCpu` | `{ "roomCode": "AB3XY", "hostId": "..." }` | Add a CPU player (host only). |
| `POST` | `/api/rename` | `{ "roomCode": "AB3XY", "playerId": "...", "name": "NewName" }` | Rename a player in the waiting room. |
| `POST` | `/api/settings` | `{ "roomCode": "AB3XY", "hostId": "...", "winNetWorth": 50, "startingCents": 12 }` | Update game settings (host only, before start). |
| `POST` | `/api/start` | `{ "roomCode": "AB3XY", "hostId": "..." }` | Start the game (host only, min 2 players). |

### Gameplay

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/bid` | `{ "roomCode": "AB3XY", "playerId": "...", "amount": 5 }` | Place a bid (must exceed current high bid). |
| `POST` | `/api/pass` | `{ "roomCode": "AB3XY", "playerId": "..." }` | Pass on the current round. |
| `GET` | `/api/state/{roomCode}/{playerId}` | — | Get current game state (opponent money hidden). |

### WebSocket Channels

Connect via SockJS at `/ws`. Subscribe to STOMP destinations:

| Destination | Description |
|-------------|-------------|
| `/topic/room/{code}/state/{playerId}` | Per-player game state updates (hides opponent money) |
| `/topic/room/{code}/gameStarted` | Broadcast when host starts the game |
| `/topic/room/{code}/roundResult` | Round outcome (winner, deed value, bid paid) |
| `/topic/room/{code}/winner` | Game winner announcement |

---

## 🤖 CPU AI

CPU players use a **Greedy** strategy:
- Bid on high-value deeds (≥5) when they can afford at least 30% more than the current bid
- 33% chance to bid 1 on any card when no bids have been placed
- Otherwise, pass

---

## 🎨 UI Features

- **Gold Bar Theme** — Players bid on 1k–24k purity gold bars using **¢**
- **3D Shiny Gold Bar** — Animated golden card in the center of the table
- **Spectator Mode** — Join via code or select from a list of available rooms
- **Session Persistence** — Refreshing the browser or reconnecting keeps you in the game
- **CPU Takeover** — Disconnected players are replaced by CPU AI until they return
- **Lobby** — Create or join rooms, or spectate
- **Waiting Room** — See players, rename yourself, host can add CPUs, configure game settings, and start
- **Roundtable Layout** — Players arranged in a circle around the central gold bar
- **Animations** — Gold bar flip, bid-win glow, net worth pop, turn pulse, round/turn banners
- **Leaderboard** — Top ranking players displayed as a compact horizontal strip
- **Hidden Information** — Opponents' Cents are hidden; only Net Worth (total gold) is visible. Spectators cannot see gold bar purity.
- **15-second Turn Timer** — Visual countdown bar; auto-pass on timeout
- **Winner Overlay** — Trophy animation with "The Lootanant" title
- **Player Manual** — In-app "How to Loot a King" guide
- **Responsive Design** — Optimized for both desktop and mobile browsers

---

## 📜 License

This project is for educational and entertainment purposes.
