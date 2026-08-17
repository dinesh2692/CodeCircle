package com.codecircle.auth;
import java.nio.charset.StandardCharsets; import java.security.Key; import java.util.Date;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys;
@Service public class JwtService { private final Key key; private final long expiry;
 public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.expiry-ms:86400000}") long expiry){ if(secret.length()<32) throw new IllegalArgumentException("JWT secret must be at least 32 characters"); key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expiry=expiry; }
 public String create(String username){Date now=new Date();return Jwts.builder().subject(username).issuedAt(now).expiration(new Date(now.getTime()+expiry)).signWith(key).compact();}
 public String username(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
 public boolean valid(String token){try{username(token);return true;}catch(Exception e){return false;}}
}
