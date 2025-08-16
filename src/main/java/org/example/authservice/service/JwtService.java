package org.example.authservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.authservice.model.UserAuth;
import org.example.authservice.repository.UserAuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final String jwtSecret;
    public final long jwtExpirationAccess = 1 * 60 * 1000;
    public final long jwtExpirationRefresh = 24 * 60 * 60 * 1000;

    @Autowired
    private UserAuthRepository userRepository;

    public JwtService(
            @Value("classpath:${jwt.secret}") Resource secretResource
    ) throws IOException {
        this.jwtSecret = new String(secretResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    public String generateAccessToken(UserAuth user){
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId())
                .claim("role", user.getRole())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationAccess))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(UserAuth user){
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationRefresh))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .setSigningKey(jwtSecret.getBytes())
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        final String username = extractUsername(token);

        if (userRepository.findByUsername(username).isEmpty()) {return false;}
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtSecret.getBytes())
                    .build()
                    .parseClaimsJws(token);
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
