package org.example.authservice.controller;

import jakarta.transaction.Transactional;
import org.example.authservice.dto.FriendRequestDTO;
import org.example.authservice.dto.UserDTO;
import org.example.authservice.dto.UserMapper;
import org.example.authservice.model.RequestStatus;
import org.example.authservice.model.Side;
import org.example.authservice.repository.FriendRequestRepository;
import org.example.authservice.repository.UserRepository;
import org.example.authservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    FriendRequestRepository friendRequestRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserService userService;

    @GetMapping("")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(Map.of("users",userService.getAllUsers()));
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
         try {
             UserDTO userDTO = userService.getUser(username);
             return ResponseEntity.status(HttpStatus.OK)
                     .body(userDTO);
         }catch (Exception e){
             return ResponseEntity.status(HttpStatus.NOT_FOUND)
                     .body(Map.of("error", e.getMessage()));
         }
    }

    @GetMapping("/friendrequests/side") // посмотреть все заявки юзера как receiver/sender
    public ResponseEntity<?> getAllFriendRequests(@RequestHeader(name = "X-User-Username") String username,
                                                  @RequestParam Side side) {
        try {
            List<FriendRequestDTO> friendRequestDTOS = userService.getAllFriendRequests(username,side);

            if (friendRequestDTOS.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("message","У Вас пока нет заявок в друзья"));
            }

            return ResponseEntity.status(HttpStatus.OK)
                    .body(friendRequestDTOS);

        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/friendrequests/{recipient}") // отправить заявку
    public ResponseEntity<?> sendFriendRequest(@RequestHeader(name = "X-User-Username") String senderUsername,
                                               @PathVariable(name = "recipient") String recipientUsername) {
        try{
            if (userService.sendFriendRequest(senderUsername, recipientUsername)) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message","Ваша заявка отправлена"));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ваша заявка не была сохранена, попробуйте еще раз"));

        } catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @PatchMapping("/friendrequests/{sender}/status") // принять || отвергнуть заявку
    public ResponseEntity<?> updateFriendRequest(@RequestHeader(name = "X-User-Username") String recipientUsername,
                                                 @PathVariable(name = "sender") String senderUsername,
                                                 @RequestParam(name = "status") RequestStatus status){
        try {
            if (userService.updateFriendRequest(recipientUsername, senderUsername, status)) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("message","Статус заявки обновлен"));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error","Статус заявки не обновлен, ошибка"));
        }catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/friends")
    public ResponseEntity<?> getAllFriends(@RequestHeader(name = "X-User-Username") String username){

        try{
            Set<String> friends = userService.getAllFriends(username);

            if (friends.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("message","У Вас пока нет друзей"));
            }
            return ResponseEntity.status(HttpStatus.OK)
                    .body(friends);

        }catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @DeleteMapping("/friends/{username}")
    public ResponseEntity<?> deleteFriend(@RequestHeader(name = "X-User-Username") String userUsername,
                                          @PathVariable(name = "username") String friendUsername){
        try{
            if (userService.deleteFriend(userUsername, friendUsername)){
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body(Map.of("message","Этот пользователь удален из ваших друзей"));
            }return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error","Не удалось удалить пользователя из ваших друзей"));

        }catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }

    }
}
