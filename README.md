# 🧈 Lootanant — Gold Bar Auction!

A real-time, web-based multiplayer auction board game built with **Java/Spring Boot** and vanilla **HTML5/CSS/JS**. Players compete to accumulate **50 Net Worth** by bidding on high-purity gold bars — bluffing, strategizing, and outmaneuvering opponents to become **The Lootanant**.

---

## 🎮 Game Overview

The King is hoarding the world's purest Gold Bars. As a budding thief, your goal is to prove you have the strategic mind to become his Lieutenant — the **Lootanant**.

### Game Modes

| Mode | Description |
|------|-------------|
| **🧈 Classic** | The original auction game — pure bidding strategy |
| **👑 Rage** | Classic rules + King's Vault, taxation, anonymous bribing, and desperate loans |

When creating a room, the host selects the game mode. All players in the room play the same mode.

### Classic Mode — Rules at a Glance

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

### Rage Mode — Additional Rules

Rage mode includes all Classic rules **plus** the following:

#### 🏦 The King's Vault & Taxation (Every 5 Rounds)

- **Trigger:** Every 5th round (after the auction), a **Taxation Phase** begins.
- **Base Tax:** Every player is taxed **25%** of their total Ant-cents (rounded down).
- **The Vault:** All taxed cents are moved into a central **King's Vault**, displayed as a golden chest icon on screen.
- **Animation:** A "King's Auditor" (an ant in a crown) walks across the screen collecting cents.

#### 🗡️ Anonymous Bribing (The Sabotage)

- **Action:** Any player can click the **"Bribe"** button at any time.
- **Cost:** 1 Ant-cent = +10% tax on a target player for the next Taxation Phase.
- **Anonymity:** The UI displays: *"Someone whispered to the King... [Player Name]'s tax has increased!"* but never reveals who paid.
- **Stacking:** Bribes stack but are capped at **40%** extra (total max tax: **65%**).
- **Indicator:** A ⚠️ icon appears next to bribed players in the leaderboard and player cards.
- Bribe money goes directly into the King's Vault.

#### 🏦 Take a Loan (The Recovery)

- **Eligibility:** Players with **fewer than 3 Ant-cents** can take a loan (vault must have at least 5¢).
- **The Offer:** The King offers **5 Ant-cents** (deducted from the King's Vault).
- **Confirmation:** A modal asks: *"The King offers 5¢, but he will take 35% of your net worth. Do you accept the shame?"* with Confirm/Deny buttons.
- **Penalty:** Deduct **35%** of the player's Net Worth (rounded to nearest whole number, minimum penalty: 3 Karats).
- **Public:** A notification is displayed: *"[Player Name] took a desperate loan! Their net worth dropped by [X]!"*
- **Animation:** The Vault shakes when a loan is taken.

#### 🎰 Vault Jackpot (The Money Returns!)

- **Timing:** Every **11th round** (Round 11, 22, 33…) is a **Jackpot Round**.
- **Key to the Vault:** The gold bar becomes the "Key to the Vault" — the card glows orange.
- **Winner Takes All:** Whoever wins the auction gets the gold bar's Karats **PLUS up to 20 Ant-cents** from the King's Vault.
- **Vault Resets:** After the jackpot is claimed, any awarded cents are removed from the vault (vault resets to 0 if 20 or fewer cents were in it).
- **No Winner?** If nobody bids on a Jackpot Round, the vault keeps its money until the next one.
- **Purpose:** Prevents the "Money Drain" problem — taxes and bribes remove money from players, and the Jackpot puts it back in a fun, high-stakes way.

### Strategic Depth

- You can see opponents' **Net Worth** but **not** their money — bluffing is key.
- Overbidding drains your funds; underbidding lets opponents grab high-value gold bars cheaply.
- Sometimes passing is the smartest move — let others waste their money!
- **Rage Mode:** Bribe the leader before a tax round to drain their funds! Watch the ⚠️ icons to know who's being targeted.

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
│   │   │   │   ├── GameRoom.java            # Room state (players, bids, deed, vault)
│   │   │   │   └── Player.java              # Player state (money, net worth, bribe tax)
│   │   │   └── service/
│   │   │       └── GameService.java         # Core game logic, AI, taxation & rage mechanics
│   │   └── resources/
│   │       ├── application.properties       # Server config
│   │       └── static/
│   │           └── index.html               # Complete single-page game UI
│   └── test/
│       └── java/imperfect/lootanant/
│           └── LootanantApplicationTests.java
├── addons.txt                       # Rage mode feature specification
└── README.md                        # This file
```

---

## 🔌 API Reference

All endpoints are under `/api`. Request/response bodies are JSON.

### Room Management

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/create` | `{ "name": "Host", "gameMode": "classic" }` | Create a new room. `gameMode` can be `"classic"` or `"rage"`. Returns `roomCode`, `playerId`, `hostId`, `gameMode`. |
| `GET` | `/api/rooms` | — | Get list of available rooms (code, host name, game mode). |
| `POST` | `/api/join` | `{ "roomCode": "AB3XY", "name": "Player" }` | Join an existing room. Returns `playerId`, `gameMode`. |
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

### Rage Mode Endpoints

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/bribe` | `{ "roomCode": "AB3XY", "playerId": "...", "targetId": "..." }` | Anonymously bribe a player (+10% tax). Costs 1¢. |
| `POST` | `/api/loan` | `{ "roomCode": "AB3XY", "playerId": "...", "amount": 3 }` | Take a loan from the King's Vault. Requires < 3¢. |

### WebSocket Channels

Connect via SockJS at `/ws`. Subscribe to STOMP destinations:

| Destination | Description |
|-------------|-------------|
| `/topic/room/{code}/state/{playerId}` | Per-player game state updates (hides opponent money) |
| `/topic/room/{code}/gameStarted` | Broadcast when host starts the game (includes `gameMode`) |
| `/topic/room/{code}/roundResult` | Round outcome (winner, deed value, bid paid) |
| `/topic/room/{code}/winner` | Game winner announcement |
| `/topic/room/{code}/rageEvent` | Rage mode events: bribe notifications, loan notifications, taxation results |

---

## 🤖 CPU AI

CPU players use a **Greedy** strategy:
- Bid on high-value deeds (≥12k) when they can afford at least 30% more than the current bid
- 33% chance to bid 1 on any card when no bids have been placed
- Otherwise, pass

---

## 🎨 UI Features

- **Game Mode Selection** — Choose Classic or Rage mode when creating a room
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
- **Player Manual** — In-app "How to Loot a King" guide with tabs for Classic and Rage modes
- **Responsive Design** — Optimized for both desktop and mobile browsers

### Rage Mode UI

- **King's Vault** — Golden chest icon displayed prominently, glows when money is added, shakes when a loan is taken
- **Tax Countdown** — Shows rounds remaining until next taxation
- **Tax Animation** — "King's Auditor" ant walks across screen collecting cents
- **Bribe Modal** — Select a target player to anonymously increase their tax
- **Loan Modal** — Borrow from the vault with a net worth penalty
- **Bribe Indicator** — ⚠️ icon next to bribed players in leaderboard and player cards

---

## 📜 License

This project is for educational and entertainment purposes.
