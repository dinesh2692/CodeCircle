package com.codecircle.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  public AuthController(UserRepository u, PasswordEncoder e, JwtService j){users=u;encoder=e;jwt=j;}

  record AuthRequest(@NotBlank @Email @Size(max=120) String email,
                     @NotBlank @Size(min=8,max=128) String password){}
  record AuthResponse(String token,String email){}

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody AuthRequest r){
    String email=r.email().trim().toLowerCase(Locale.ROOT);
    if(users.existsByUsername(email)) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","An account with this email already exists."));
    AppUser u=users.save(new AppUser(email,encoder.encode(r.password())));
    return ResponseEntity.ok(new AuthResponse(jwt.create(u.username),u.username));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody AuthRequest r){
    String email=r.email().trim().toLowerCase(Locale.ROOT);
    return users.findByUsername(email)
      .filter(u->encoder.matches(r.password(),u.passwordHash))
      .<ResponseEntity<?>>map(u->ResponseEntity.ok(new AuthResponse(jwt.create(u.username),u.username)))
      .orElseGet(()->ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Invalid email or password.")));
  }
}
