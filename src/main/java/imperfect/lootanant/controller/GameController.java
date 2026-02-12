package imperfect.lootanant.controller;

import imperfect.lootanant.model.GameRoom;
import imperfect.lootanant.model.Player;
import imperfect.lootanant.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody Map<String, String> body) {
        String hostName = body.getOrDefault("name", "Host");
        GameRoom room = gameService.createRoom(hostName);
        Player host = room.getPlayers().get(0);
        return ResponseEntity.ok(Map.of(
                "roomCode", room.getRoomCode(),
                "playerId", host.getId(),
                "hostId", room.getHostId()
        ));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Map<String, String> body) {
        String code = body.get("roomCode");
        String name = body.getOrDefault("name", "Player");
        Player player = gameService.joinRoom(code, name);
        if (player == null) return ResponseEntity.badRequest().body(Map.of("error", "Cannot join room"));
        return ResponseEntity.ok(Map.of("playerId", player.getId()));
    }

    @PostMapping("/addCpu")
    public ResponseEntity<?> addCpu(@RequestBody Map<String, String> body) {
        String code = body.get("roomCode");
        String hostId = body.get("hostId");
        Player cpu = gameService.addCpu(code, hostId);
        if (cpu == null) return ResponseEntity.badRequest().body(Map.of("error", "Cannot add CPU"));
        return ResponseEntity.ok(Map.of("cpuName", cpu.getDisplayName()));
    }

    @PostMapping("/rename")
    public ResponseEntity<?> renamePlayer(@RequestBody Map<String, String> body) {
        String code = body.get("roomCode");
        String pid = body.get("playerId");
        String newName = body.getOrDefault("name", "Player");
        boolean ok = gameService.renamePlayer(code, pid, newName);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Cannot rename"));
        return ResponseEntity.ok(Map.of("status", "renamed"));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("roomCode");
        String hid = (String) body.get("hostId");
        int winNetWorth = ((Number) body.get("winNetWorth")).intValue();
        int startingAntCents = ((Number) body.get("startingAntCents")).intValue();
        boolean ok = gameService.updateSettings(code, hid, winNetWorth, startingAntCents);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Cannot update settings"));
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startGame(@RequestBody Map<String, String> body) {
        String code = body.get("roomCode");
        String hostId = body.get("hostId");
        boolean ok = gameService.startGame(code, hostId);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Cannot start game"));
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/bid")
    public ResponseEntity<?> placeBid(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("roomCode");
        String playerId = (String) body.get("playerId");
        int amount = ((Number) body.get("amount")).intValue();
        boolean ok = gameService.placeBid(code, playerId, amount);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Invalid bid"));
        return ResponseEntity.ok(Map.of("status", "bid placed"));
    }

    @PostMapping("/pass")
    public ResponseEntity<?> pass(@RequestBody Map<String, String> body) {
        String code = body.get("roomCode");
        String playerId = body.get("playerId");
        boolean ok = gameService.pass(code, playerId);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Cannot pass"));
        return ResponseEntity.ok(Map.of("status", "passed"));
    }

    @GetMapping("/state/{roomCode}/{playerId}")
    public ResponseEntity<?> getState(@PathVariable String roomCode, @PathVariable String playerId) {
        GameRoom room = gameService.getRoom(roomCode);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(gameService.getPublicState(room, playerId));
    }
}
