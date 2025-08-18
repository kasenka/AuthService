package org.example.authservice.controller;

import jakarta.validation.Valid;

import org.apache.coyote.BadRequestException;
import org.example.authservice.dto.UserAuthDTO;
import org.example.authservice.dto.UserMapper;
import org.example.authservice.model.Role;
import org.example.authservice.model.User;
import org.example.authservice.repository.RefreshTokenRepository;
import org.example.authservice.repository.UserRepository;
import org.example.authservice.service.JwtService;
import org.example.authservice.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final RefreshTokenRepository refreshTokenRepository;
    private UserRepository userRepository;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;

    public AuthController(UserRepository userRepository,
                          JwtService jwtService,
                          UserMapper userMapper,
                          PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokenService, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping(value = "/register")
    public ResponseEntity<?> register(@RequestBody @Valid UserAuthDTO userAuthDTO,
                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<String> errors = bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.toList());
            return ResponseEntity.badRequest()
                    .body(Map.of("errors", errors));
        }

        if(userRepository.findByUsername(userAuthDTO.getUsername()).isPresent()){
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Этот юзернейм уже занят"));
        }

        User user = userMapper.map(userAuthDTO);

        user.setEncryptedPassword(passwordEncoder.encode(userAuthDTO.getPassword()));
        user.setRole(Role.USER);

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userMapper.map(user));
    }

    @PostMapping(value = "/login")
    public ResponseEntity<?> login(@RequestBody UserAuthDTO userAuthDTO){

        try {
            User user = userRepository.findByUsername(userAuthDTO.getUsername())
                    .orElseThrow(() -> new BadRequestException(""));

            if (!passwordEncoder.matches(userAuthDTO.getPassword(), user.getEncryptedPassword())) {
                throw new BadRequestException("");
            }

            String jwtAccess = jwtService.generateAccessToken(user);
            String jwtRefresh = jwtService.generateRefreshToken(user);

            refreshTokenService.saveRefreshToken(jwtRefresh, user.getUsername(),
                    LocalDateTime.now().plusSeconds(jwtService.jwtExpirationRefresh));

            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of(
                            "user", userMapper.map(user),
                            "jwtAccess", jwtAccess,
                            "jwtRefresh", jwtRefresh));
        } catch (BadRequestException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверный юзернейм или пароль"));
        }
    }

    @PostMapping(value = "/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken){
        if (refreshTokenService.isValid(refreshToken)){
            String username = refreshTokenService.getUsernameByToken(refreshToken);
            User user = userRepository.findByUsername(username).get();

            String accessToken = jwtService.generateAccessToken(user);
            return ResponseEntity.ok()
                    .body(Map.of("jwtAccess", accessToken));
        }return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Невалидный refreshToken"));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestHeader("Authorization") String header){

            if (header == null || !header.startsWith("Bearer ") || header.length() < 8){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Access токен в заголовке отсутствует"));
            }
            String token = header.substring(7);

        try{
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Некорректный JWT токен"));
            }

            User user = userRepository.findByUsername(jwtService.extractUsername(token)).get();

            return ResponseEntity.status(HttpStatus.OK)
                    .body(userMapper.map(user));

        }catch (io.jsonwebtoken.ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Access токен просрочен"));
        } catch (io.jsonwebtoken.JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Access токен не валидный"));
        }
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        if (refreshTokenRepository.findByToken(refreshToken).isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh токен не валидный"));
        }
        refreshTokenService.deleteRefreshToken(refreshToken);
        return ResponseEntity.ok("Logged out");
    }

}

