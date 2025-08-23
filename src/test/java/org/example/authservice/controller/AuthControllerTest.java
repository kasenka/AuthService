package org.example.authservice.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.example.authservice.dto.UserAuthDTO;

import org.example.authservice.model.Role;
import org.example.authservice.model.User;
import org.example.authservice.repository.RefreshTokenRepository;
import org.example.authservice.repository.UserRepository;
import org.example.authservice.service.JwtService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.junit.jupiter.api.Assertions.assertTrue;


import java.util.Map;
import java.util.stream.Stream;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Комплексные тесты для AuthController")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    String username = "new", password = "testpassword";

    @Autowired
    private  RefreshTokenRepository refreshTokenRepository;


    @AfterEach
    void cleanUsers(){
        userRepository.deleteAll();
    }


    @Nested
    @DisplayName("Тесты регистрации")
    class RegistrationTests {

        @Test
        @DisplayName("Успешная регистрация")
        void register_success() throws Exception {

            UserAuthDTO userAuthDTO = new UserAuthDTO(username, password);

            MvcResult result = mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.id")
                                    .exists(),
                            jsonPath("$.username")
                                    .value(userAuthDTO.getUsername()),
                            jsonPath("$.role")
                                    .value("USER")
                    )
                    .andDo(print())
                    .andReturn();
        }


        static Stream<Arguments> invalidUserRegister() {
            return Stream.of(
                    Arguments.of("", "testpassword", "Логин не может быть пустым"),
                    Arguments.of("testusername", "", "Пароль не может быть пустым")
            );
        }

        @ParameterizedTest(name = "[{index}] {2}")
        @MethodSource("invalidUserRegister")
        @DisplayName("Неверный юзернейм или пароль")
        void invalidRegister(String username, String password, String error) throws Exception {

            UserAuthDTO userAuthDTO = new UserAuthDTO(username, password);

            MvcResult result = mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isBadRequest(),
                            jsonPath("$.errors").exists(),
                            jsonPath("$.errors", Matchers.contains(error))
                    )
                    .andDo(print())
                    .andReturn();

        }

        @Test
        @DisplayName("Не уникальные данные регистрации")
        void notUniqueRegister() throws Exception {

            UserAuthDTO user1 = new UserAuthDTO("testusername1", "testpassword1");
            UserAuthDTO user2 = new UserAuthDTO("testusername1", "testpassword1");

            MvcResult result1 = mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(user1)))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.username")
                                    .value(user1.getUsername()),
                            jsonPath("$.role")
                                    .value("USER")
                    )
                    .andDo(print())
                    .andReturn();

            MvcResult result2 = mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(user2)))
                    .andExpectAll(
                            status().isBadRequest(),
                            jsonPath("$.error").exists(),
                            jsonPath("$.error").value("Этот юзернейм уже занят")
                    ).andDo(print())
                    .andReturn();
        }
    }

    @Nested
    @DisplayName("Тесты логина")
    class LoginTests {

        @BeforeEach
        void setUpRegister() throws Exception {
            UserAuthDTO userAuthDTO = new UserAuthDTO(username, password);

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.username")
                                    .value(userAuthDTO.getUsername()),
                            jsonPath("$.role")
                                    .value("USER"));
        }

        @Test
        @DisplayName("Успешный логин")
        void login_success() throws Exception {

            UserAuthDTO userAuthDTO = new UserAuthDTO(username, password);

            MvcResult resultLogin = mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.user.id")
                                    .exists(),
                            jsonPath("$.user.username")
                                    .value(userAuthDTO.getUsername()),
                            jsonPath("$.user.role")
                                    .value("USER"),
                            jsonPath("$.jwtAccess")
                                    .exists(),
                            jsonPath("$.jwtRefresh")
                                    .exists()
                    )
                    .andDo(print())
                    .andReturn();

            String responseContent = resultLogin.getResponse().getContentAsString();
            Map<String, Object> userData = objectMapper.readValue(responseContent, Map.class);

            String username = jwtService.extractUsername(userData.get("jwtAccess").toString());
            assertTrue(username.equals(userAuthDTO.getUsername()),
                    "Username не совпадает с jwtAccessToken");
        }

        static Stream<Arguments> invalidLogin(){
            return Stream.of(
                    Arguments.of(new UserAuthDTO("testusername", "wrongpassword"),
                            "Неверный пароль"),
                    Arguments.of(new UserAuthDTO("wrongusername", "testpassword"),
                            "Неверный юзернейм"),
                    Arguments.of(new UserAuthDTO("testusername", " "),
                            "Пустой пароль"),
                    Arguments.of(new UserAuthDTO(" ", "testpassword"),
                            "Пустой юзернейм")
            );
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("invalidLogin")
        @DisplayName("Неверный юзернейм или пароль")
        void invalidLoginData(UserAuthDTO userAuthDTO, String message) throws Exception {

            MvcResult result = mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isUnauthorized(),
                            jsonPath("$.error").exists(),
                            jsonPath("$.error")
                                    .value("Неверный юзернейм или пароль")
                    ).andDo(print())
                    .andReturn();
        }
    }

    @Nested
    @DisplayName("Тесты jwtAccessToken & jwtRefreshToken")
    class JWTTest{

        @Autowired
        RefreshTokenRepository refreshTokenRepository;

        private Map<String, Object> loginUserData;

        @BeforeEach
        void setUpLogin() throws Exception {
            UserAuthDTO userAuthDTO = new UserAuthDTO(username, password);

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.username")
                                    .value(userAuthDTO.getUsername()),
                            jsonPath("$.role")
                                    .value("USER"));


            MvcResult resultLogin = mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userAuthDTO)))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.user.id")
                                    .exists(),
                            jsonPath("$.user.username")
                                    .value(userAuthDTO.getUsername()),
                            jsonPath("$.user.role")
                                    .value("USER"),
                            jsonPath("$.jwtAccess")
                                    .exists(),
                            jsonPath("$.jwtRefresh")
                                    .exists()
                    ).andReturn();

            String responseContent = resultLogin.getResponse().getContentAsString();
            loginUserData = objectMapper.readValue(responseContent, Map.class);
        }

        @Test
        @DisplayName("Успешный переход по пути /api/validate")
        void getValidate() throws Exception {

            mockMvc.perform(post("/api/validate")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginUserData.get("jwtAccess"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.id").exists(),
                            jsonPath("$.username").value(username),
                            jsonPath("$.role").value("USER")
                    ).andDo(print());

        }

        static Stream<Arguments> wrongJWT(){
            return Stream.of(
                    Arguments.of("", HttpStatus.BAD_REQUEST,
                            "Access токен в заголовке отсутствует"),
                    Arguments.of("generate", HttpStatus.UNAUTHORIZED,
                            "Некорректный JWT токен"),
                    Arguments.of("wrongJWT", HttpStatus.UNAUTHORIZED,
                            "Access токен не валидный"),
                    Arguments.of("eyJhbGciOiJIUzI1NiJ9" +
                                    ".eyJzdWIiOiJ0ZXN0dXNlciIsImlkIjo0NCwicm9sZSI6IlVTRVIiLCJleHAiOjE3NTU0Mjg1Nzd9" +
                                    ".RHhhnqwI42J9KvyOc18aJYk4x24QYAnl7h005H7i88A",
                            HttpStatus.UNAUTHORIZED,
                            "Access токен не валидный")
            );
        }

        @ParameterizedTest(name = "[{index}] {2}")
        @MethodSource("wrongJWT")
        @DisplayName("Не успешный переход по пути /api/validate")
        void invalidJWT(String jwt, HttpStatus status, String error) throws Exception {
            if (jwt.equals("generate")) {
                jwt = jwtService.generateAccessToken(
                        new User (9999L,"wronguser","wrongpassword", Role.USER));
            }

            mockMvc.perform(post("/api/validate")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + jwt)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().is(status.value()),
                            jsonPath("$.error").value(error)
                    )
                    .andDo(print());

        }

        @Test
        @DisplayName("Успешное получение нового AccessJWTToken")
        void getAccessJWTToken() throws Exception {

            String jwtRefresh = loginUserData.get("jwtRefresh").toString();

            MvcResult result = mockMvc.perform(post("/api/refresh?refreshToken=" + jwtRefresh)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.jwtAccess").exists())
                    .andDo(print())
                    .andReturn();

            mockMvc.perform(post("/api/validate")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " +
                                            objectMapper.readValue(result.getResponse().getContentAsString(), Map.class)
                                                    .get("jwtAccess").toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.username").value(username))
                    .andDo(print());
        }

        @Test
        @DisplayName("Не успешное получение нового AccessJWTToken (invalid RefreshToken)")
        void invalidRefreshToken() throws Exception {

            String jwtRefresh = jwtService.generateAccessToken(
                    new User (9999L,"wronguser","wrongpassword", Role.USER));

            mockMvc.perform(post("/api/refresh?refreshToken=" + jwtRefresh)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isNotFound(),
                            jsonPath("$.error").exists(),
                            jsonPath("$.error").value("Невалидный refreshToken"))
                    .andDo(print());
        }

        @Test
        @DisplayName("Успешный logout")
        @Transactional
        void successfulLogout() throws Exception {

            mockMvc.perform(delete("/api/logout?refreshToken=" + loginUserData.get("jwtRefresh"))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginUserData.get("jwtAccess"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isOk(),
                            MockMvcResultMatchers.content().string("Logged out"))
                    .andDo(print());

            assertTrue(refreshTokenRepository.findByToken(loginUserData.get("jwtRefresh").toString()).isEmpty());
        }

        @Test
        @DisplayName("Не успешный logout")
        @Transactional
        void invalidLogout() throws Exception {

            mockMvc.perform(delete("/api/logout?refreshToken=" + " ")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginUserData.get("jwtAccess"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpectAll(
                            status().isBadRequest(),
                            jsonPath("$.error").value("Refresh токен не валидный"))
                    .andDo(print());
        }
    }
}

