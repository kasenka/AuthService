package org.example.authservice.controller;

import jakarta.transaction.Transactional;
import org.example.authservice.dto.FriendRequestDTO;
import org.example.authservice.dto.UserDTO;
import org.example.authservice.dto.UserMapper;
import org.example.authservice.model.FriendRequest;
import org.example.authservice.model.RequestStatus;
import org.example.authservice.model.User;
import org.example.authservice.repository.FriendRequestRepository;
import org.example.authservice.repository.UserRepository;
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

    @GetMapping("")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(userRepository.findAll().stream()
                        .map(UserDTO::new));
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
         try {
             User user = userRepository.findByUsername(username).get();
             return ResponseEntity.status(HttpStatus.OK)
                     .body(new UserDTO(user));
         }catch (Exception e){
             return ResponseEntity.status(HttpStatus.NOT_FOUND)
                     .body(Map.of("error", "Юзер не найден"));
         }
    }

    @GetMapping("/friendrequests/side") // посмотреть все заявки юзера как receiver/sender
    public ResponseEntity<?> getAllFriendRequests(@RequestHeader(name = "X-User-Username") String username,
                                                  @RequestParam String side) {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new NoSuchElementException(""));

            List<FriendRequest> friendRequest = new ArrayList<>();
            switch (side){
                case "receiver" ->
                        friendRequest = friendRequestRepository.findAllByReceiverId(user.getId());
                case "sender" ->
                        friendRequest = friendRequestRepository.findAllBySenderId(user.getId());
            }

            if (friendRequest.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK)
                        .body("У Вас пока нет заявок в друзья");
            }

            return ResponseEntity.status(HttpStatus.OK)
                    .body(friendRequest.stream()
                            .map(fr -> new FriendRequestDTO(fr))
                            .toList());

        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Юзер не найден"));
        }
    }

    @PostMapping("/friendrequests/{recipient}") // отправить заявку
    public ResponseEntity<?> sendFriendRequest(@RequestHeader(name = "X-User-Username") String senderUsername,
                                               @PathVariable(name = "recipient") String recipientUsername) {
        if (userRepository.findByUsername(recipientUsername).isEmpty()){
            System.out.println(recipientUsername);
            System.out.println(userRepository.findByUsername(recipientUsername));

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Получатель не найден"));
        }

        User senderUser = userRepository.findByUsername(senderUsername).get();
        User recipientUser = userRepository.findByUsername(recipientUsername).get();

        Optional<FriendRequest> friendRequest = friendRequestRepository.findBySenderIdAndReceiverId(
                senderUser.getId(), recipientUser.getId());

        if (friendRequest.isPresent() && friendRequest.get().getStatus().equals(RequestStatus.PENDING)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Запрос дружбы уже создан, ожидайте подтверждения"));
        }

        Optional<FriendRequest> reversedFriendRequest = friendRequestRepository.findBySenderIdAndReceiverId(
                recipientUser.getId(), senderUser.getId());

        if (reversedFriendRequest.isPresent() && reversedFriendRequest.get().getStatus().equals(RequestStatus.PENDING)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Вам уже отправлен запрос дружбы от этого пользователя"));
        }

        if (senderUser.getFriends().contains(recipientUser)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Этот пользователь уже Ваш друг"));
        }

        FriendRequest friendRequestToSend = new FriendRequest(senderUser, recipientUser, RequestStatus.PENDING);
        friendRequestRepository.save(friendRequestToSend);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Ваша заявка отправлена");
    }

    @Transactional
    @PatchMapping("/friendrequests/{sender}/status") // принять || отвергнуть заявку
    public ResponseEntity<?> updateFriendRequest(@RequestHeader(name = "X-User-Username") String recipientUsername,
                                                 @PathVariable(name = "sender") String senderUsername,
                                                 @RequestParam(name = "status") RequestStatus status){
        User sender = userRepository.findByUsername(senderUsername).get();
        User receiver = userRepository.findByUsername(recipientUsername).get();

        Optional<FriendRequest> friendRequest = friendRequestRepository.findBySenderIdAndReceiverId(
                sender.getId(), receiver.getId());

        if (status.equals(RequestStatus.ACCEPTED)){
                sender.getFriends().add(receiver);
                receiver.getFriends().add(sender);
        }
        sender.getSendRequests().remove(friendRequest.get());
        receiver.getReceivedRequests().remove(friendRequest.get());

        userRepository.save(sender);
        userRepository.save(receiver);

        friendRequestRepository.delete(friendRequest.get());

        return ResponseEntity.status(HttpStatus.OK)
                .body("Статус заявки обновлен");
    }
}
