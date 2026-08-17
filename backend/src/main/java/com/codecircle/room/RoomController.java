package com.codecircle.room;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom random = new SecureRandom();
  private final Set<String> rooms = ConcurrentHashMap.newKeySet();

  record CreateResponse(String room, String owner) {}
  record JoinResponse(boolean available, String room) {}

  @PostMapping
  public ResponseEntity<CreateResponse> create(Principal principal) {
    String code;
    do {
      StringBuilder b = new StringBuilder(6);
      for (int i=0;i<6;i++) b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      code=b.toString();
    } while(!rooms.add(code));
    return ResponseEntity.ok(new CreateResponse(code, principal.getName()));
  }

  @GetMapping("/{room}")
  public ResponseEntity<?> check(@PathVariable String room) {
    String code=room.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    if(code.length()!=6) return ResponseEntity.badRequest().body(Map.of("message","Room code must be 6 characters."));
    return ResponseEntity.ok(new JoinResponse(rooms.contains(code), code));
  }
}
