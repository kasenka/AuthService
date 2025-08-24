package org.example.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.authservice.model.RequestStatus;
import org.example.authservice.model.Role;
import org.example.authservice.model.Side;
import org.example.authservice.model.User;
import org.example.authservice.repository.UserRepository;
import org.example.authservice.service.JwtService;
import org.example.authservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.*;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Комплексные тесты для UserController")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user1 = new User("user1","password1", Role.USER);
        User user2 = new User("user2","password2", Role.USER);

        userRepository.save(user1);
        userRepository.save(user2);

        User sender = new User("sender","sender1", Role.USER);
        User recipient = new User("recipient","recipient1", Role.USER);

        User friend = new User("friend","friend1", Role.USER);

        userRepository.save(friend);
        userRepository.save(sender);
        userRepository.save(recipient);

        sender.getFriends().add(friend);
        friend.getFriends().add(sender);

        userRepository.save(friend);
        userRepository.save(sender);
    }

    @AfterEach
    void clear() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Успешное получение юзеров")
    void getAllUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.users[0].username").value("user1"),
                        jsonPath("$.users[1].username").value("user2")
                )
                .andDo(print())
                .andReturn();
    }

    @Test
    @DisplayName("Успешное получение юзера")
    void getUser() throws Exception {
        String username = "user1";
        mockMvc.perform(get("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.username").value("user1"),
                        jsonPath("$.role").value("USER")
                )
                .andDo(print())
                .andReturn();
    }

    @Test
    @DisplayName("Юзера не существует")
    void invalidGetUser() throws Exception {
        String username = "user3";
        mockMvc.perform(get("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.error").value("Юзер не найден"))
                .andDo(print())
                .andReturn();
    }

    @Nested
    @DisplayName("Тесты заявок в друзья")
    class Friendship{

        @BeforeEach
        void setUp() throws Exception {
            String senderUsername = "sender";
            String recipientUsername = "recipient";

            mockMvc.perform(post("/api/users/friendrequests/" + recipientUsername)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", senderUsername))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.message").value("Ваша заявка отправлена")
                    );
        }

        @Test
        @DisplayName("Успешная отправка заявки дружбы")
        void sendFriendRequest() throws Exception {
            String senderUsername = "user1";
            String recipientUsername = "user2";

            mockMvc.perform(post("/api/users/friendrequests/" + recipientUsername)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Username", senderUsername))
                    .andExpectAll(
                            status().isCreated(),
                            jsonPath("$.message").value("Ваша заявка отправлена")
                    )
                    .andDo(print())
                    .andReturn();
        }

        static Stream<Arguments> argumentsInvalidSendFriendRequest(){
            return Stream.of(
                    Arguments.of("wrongsender","recipient","Отправитель не найден",
                            HttpStatus.NOT_FOUND),
                    Arguments.of("sender","wrongrecipient","Получатель не найден",
                            HttpStatus.NOT_FOUND),
                    Arguments.of("sender","sender","Нельзя отправить заявку самому себе",
                            HttpStatus.CONFLICT),
                    Arguments.of("sender","recipient","Запрос дружбы уже создан, ожидайте подтверждения",
                            HttpStatus.CONFLICT),
                    Arguments.of("recipient","sender","Вам уже отправлен запрос дружбы от этого пользователя",
                            HttpStatus.CONFLICT),
                    Arguments.of("sender","friend","Этот пользователь уже Ваш друг",
                            HttpStatus.CONFLICT)
            );
        }

        @ParameterizedTest(name = "[{index}] {2}")
        @MethodSource("argumentsInvalidSendFriendRequest")
        @DisplayName("Не успешная отправка заявки дружбы")
        void invalidSendFriendRequest(String senderUsername,String recipientUsername,
                                      String error, HttpStatus status) throws Exception {

            mockMvc.perform(post("/api/users/friendrequests/" + recipientUsername)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", senderUsername))
                    .andExpectAll(
                            status().is(status.value()),
                            jsonPath("$.error").value(error)
                    )
                    .andDo(print())
                    .andReturn();
        }

        @Test
        @DisplayName("Успешный просмотр заявок")
        void getFriendRequest() throws Exception {
            String username = "sender";

            mockMvc.perform(get("/api/users/friendrequests/side?side=" + Side.RESIPIENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", username))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.message").value("У Вас пока нет заявок в друзья")
                    )
                    .andDo(print())
                    .andReturn();

            mockMvc.perform(get("/api/users/friendrequests/side?side=" + Side.SENDER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", username))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$[0].sender").value("sender"),
                            jsonPath("$[0].recipient").value("recipient")
                    )
                    .andDo(print())
                    .andReturn();
        }

        @Test
        @DisplayName("Не успешный просмотр заявок")
        void invalidGetFriendRequest() throws Exception {
            String username = "wrongusername";

            mockMvc.perform(get("/api/users/friendrequests/side?side=" + Side.RESIPIENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", username))
                    .andExpectAll(
                            status().isNotFound(),
                            jsonPath("$.error").value("Юзер не найден")
                    )
                    .andDo(print())
                    .andReturn();

            mockMvc.perform(get("/api/users/friendrequests/side?side=" + Side.SENDER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", username))
                    .andExpectAll(
                            status().isNotFound(),
                            jsonPath("$.error").value("Юзер не найден")
                    )
                    .andDo(print())
                    .andReturn();
        }

        static Stream<Arguments> argumentsUpdateFriendRequest(){
            return Stream.of(
                    Arguments.of("sender","recipient",RequestStatus.ACCEPTED,"Принять"),
                    Arguments.of("sender","recipient",RequestStatus.REJECTED,"Отклонить")
            );
        }

        @ParameterizedTest(name = "[{index}] {3}")
        @MethodSource("argumentsUpdateFriendRequest")
        @DisplayName("Успешное обновление заявки")
        void updateFriendRequest(String senderUsername, String recipientUsername, RequestStatus status,
                                 String testName) throws Exception {

            mockMvc.perform(patch("/api/users/friendrequests/" + senderUsername +
                            "/status?status=" + status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", recipientUsername))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.message").value("Статус заявки обновлен")
                    )
                    .andDo(print())
                    .andReturn();
        }

        static Stream<Arguments> argumentsInvalidUpdateFriendRequest(){
            return Stream.of(
                    Arguments.of("wrongsender","recipient","Отправитель не найден"),
                    Arguments.of("sender","wrongrecipient","Получатель не найден"),
                    Arguments.of("user1","user2","Заявка не найдена")
            );
        }

        @ParameterizedTest(name = "[{index}] {2}")
        @MethodSource("argumentsInvalidUpdateFriendRequest")
        @DisplayName("Не успешное обновление заявки")
        void invalidUpdateFriendRequest(String senderUsername, String recipientUsername, String error) throws Exception {

            mockMvc.perform(patch("/api/users/friendrequests/" + senderUsername +
                            "/status?status=" + RequestStatus.REJECTED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", recipientUsername))
                    .andExpectAll(
                            status().isNotFound(),
                            jsonPath("$.error").value(error)
                    )
                    .andDo(print())
                    .andReturn();
        }

        @Test
        @DisplayName("Успешное получение друзей")
        void getAllFriends() throws Exception {
            String haveFriends = "sender";

            mockMvc.perform(get("/api/users/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", haveFriends))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.[0]").value("friend")
                    )
                    .andDo(print())
                    .andReturn();

            String noFriends = "recipient";

            mockMvc.perform(get("/api/users/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", noFriends))
                    .andExpectAll(
                            status().isOk(),
                            jsonPath("$.message").value("У Вас пока нет друзей")
                    )
                    .andDo(print())
                    .andReturn();
        }

        @Test
        @DisplayName("Не успешное получение друзей (юзер не найден)")
        void invalidGetAllFriends() throws Exception {
            String username = "someusername";

            mockMvc.perform(get("/api/users/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", username))
                    .andExpectAll(
                            status().isNotFound(),
                            jsonPath("$.error").value("Юзер не найден")
                    )
                    .andDo(print())
                    .andReturn();
        }

        @Test
        @DisplayName("Успешное удаление друга")
        void deleteFriend() throws Exception {
            String username = "friend";
            String user = "sender";

            mockMvc.perform(delete("/api/users/friends/" + username)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", user))
                    .andExpectAll(
                            status().isNoContent(),
                            jsonPath("$.message").value("Этот пользователь удален из ваших друзей")
                    )
                    .andDo(print())
                    .andReturn();
        }

        static Stream<Arguments> argumentsInvalidDeleteFriend(){
            return Stream.of(
                    Arguments.of("wrongsender","friend",HttpStatus.NOT_FOUND,
                            "Юзер не найден"),
                    Arguments.of("sender","wrongfriend",HttpStatus.NOT_FOUND,
                            "Друг не найден"),
                    Arguments.of("sender","recipient",HttpStatus.CONFLICT,
                            "Этого пользователя нет у Вас в друзьях")
            );
        }

        @ParameterizedTest(name = "[{index}] {3}")
        @MethodSource("argumentsInvalidDeleteFriend")
        @DisplayName("Не успешное удаление друга")
        void invalidDeleteFriend(String userUsername, String friendUsername,HttpStatus status,
                                 String error) throws Exception {

            mockMvc.perform(delete("/api/users/friends/" + friendUsername)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Username", userUsername))
                    .andExpectAll(
                            status().is(status.value()),
                            jsonPath("$.error").value(error)
                    )
                    .andDo(print())
                    .andReturn();
        }

    }

}
