package org.example.authservice.controller;

import jakarta.validation.Valid;

import org.apache.coyote.BadRequestException;
import org.example.authservice.dto.UserAuthDTO;
import org.example.authservice.dto.UserMapper;
import org.example.authservice.model.Role;
import org.example.authservice.model.UserAuth;
import org.example.authservice.repository.RefreshTokenRepository;
import org.example.authservice.repository.UserAuthRepository;
import org.example.authservice.service.JwtService;
import org.example.authservice.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class AuthController {

    private UserAuthRepository userRepository;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;

    public AuthController(UserAuthRepository userRepository,
                          JwtService jwtService,
                          UserMapper userMapper,
                          PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping(value = "/register")
    public ResponseEntity<?> register(@RequestBody @Valid UserAuthDTO userAuthDTO,
                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", bindingResult.getAllErrors()));
        }

        if(userRepository.findByUsername(userAuthDTO.getUsername()).isPresent()){
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Этот юзернейм уже занят"));
        }

        UserAuth user = userMapper.map(userAuthDTO);

        user.setEncryptedPassword(passwordEncoder.encode(userAuthDTO.getPassword()));
        user.setRole(Role.USER);

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userMapper.map(user));
    }

    @PostMapping(value = "/login")
    public ResponseEntity<?> login(@RequestBody UserAuthDTO userAuthDTO){

        try {
            UserAuth user = userRepository.findByUsername(userAuthDTO.getUsername())
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
            UserAuth user = userRepository.findByUsername(username).get();

            String accessToken = jwtService.generateAccessToken(user);
            return ResponseEntity.ok()
                    .body(Map.of("jwtAccess", accessToken));
        }return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Невалидный refreshToken"));
    }


    @DeleteMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
        return ResponseEntity.ok("Logged out");
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestHeader("Authorization") String header){

            if (header == null || !header.startsWith("Bearer ")){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Access токен в заголовке отсутствует"));
            }
            String token = header.substring(7);

        try{
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Access токен не валидный"));
            }

            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of("username", jwtService.extractUsername(token)));

        }catch (io.jsonwebtoken.ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Access токен просрочен"));
        } catch (io.jsonwebtoken.JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Некорректный JWT токен"));
        }
    }

}

