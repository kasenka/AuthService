package org.example.authservice.controller;

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

import java.util.List;
import java.util.Optional;

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
                        .map(user -> userMapper.map(user)));
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(userRepository.findByUsername(username).get());
    }

    @PostMapping("/friendrequests/{recipient}")
    public ResponseEntity<?> sendFriendRequest(@RequestHeader(name = "X-User-Username") String senderUsername,
                                               @PathVariable(name = "recipient") String recipientUsername) {
        if (userRepository.findByUsername(recipientUsername).isEmpty()){
            System.out.println(recipientUsername);
            System.out.println(userRepository.findByUsername(recipientUsername));

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Получатель не найден");
        }

        Optional<FriendRequest> friendRequest = friendRequestRepository.findBySenderIdAndReceiverId(
                userRepository.findByUsername(senderUsername).get().getId(),
                userRepository.findByUsername(recipientUsername).get().getId());

        if (friendRequest.isPresent() && friendRequest.get().getStatus().equals(RequestStatus.PENDING)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Запрос дружбы уже создан, ожидайте подтверждения");
        }

        User senderUser = userRepository.findByUsername(senderUsername).get();
        User recipientUser = userRepository.findByUsername(recipientUsername).get();

        if (senderUser.getFriends().contains(recipientUser)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Этот пользователь уже Ваш друг");
        }

        FriendRequest friendRequestToSend = new FriendRequest(senderUser, recipientUser, RequestStatus.PENDING);
        friendRequestRepository.save(friendRequestToSend);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Ваша заявка отправлена");
    }
}
