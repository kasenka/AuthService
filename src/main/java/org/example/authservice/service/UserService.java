package org.example.authservice.service;

import jakarta.transaction.Transactional;
import org.example.authservice.dto.FriendRequestDTO;
import org.example.authservice.dto.UserDTO;
import org.example.authservice.dto.UserMapper;
import org.example.authservice.model.FriendRequest;
import org.example.authservice.model.RequestStatus;
import org.example.authservice.model.Side;
import org.example.authservice.model.User;
import org.example.authservice.repository.FriendRequestRepository;
import org.example.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Service
public class UserService {

    @Autowired
    FriendRequestRepository friendRequestRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserEventProducer userEventProducer;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDTO::new).toList();
    }

    public UserDTO getUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Юзер не найден"));
        return new UserDTO(user);
    }

    public List<FriendRequestDTO> getAllFriendRequests(String username, Side side) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Юзер не найден"));

        List<FriendRequest> friendRequest = new ArrayList<>();
        switch (side){
            case RECIPIENT ->
                    friendRequest = friendRequestRepository.findAllByRecipientId(user.getId());
            case SENDER ->
                    friendRequest = friendRequestRepository.findAllBySenderId(user.getId());
        }

        return friendRequest.stream()
                        .map(fr -> new FriendRequestDTO(fr))
                        .toList();
    }

    public boolean sendFriendRequest(String senderUsername ,String recipientUsername) {

        User senderUser = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new NoSuchElementException("Отправитель не найден"));
        User recipientUser = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new NoSuchElementException("Получатель не найден"));

        if (senderUsername.equals(recipientUsername)) {
            throw new IllegalArgumentException("Нельзя отправить заявку самому себе");
        }

        friendRequestRepository.findBySenderIdAndRecipientId(
                senderUser.getId(), recipientUser.getId())
                .filter(fr -> fr.getStatus().equals(RequestStatus.PENDING))
                .ifPresent(fr -> {
                    throw new IllegalStateException("Запрос дружбы уже создан, ожидайте подтверждения");
                });

        friendRequestRepository.findBySenderIdAndRecipientId(
                recipientUser.getId(), senderUser.getId())
                .filter(fr -> fr.getStatus().equals(RequestStatus.PENDING))
                .ifPresent(fr -> {
                    throw new IllegalStateException("Вам уже отправлен запрос дружбы от этого пользователя");
                });

        if (senderUser.getFriends().contains(recipientUser)){
            throw new IllegalStateException("Этот пользователь уже Ваш друг");
        }

        FriendRequest friendRequestToSend = new FriendRequest(senderUser, recipientUser, RequestStatus.PENDING);
        friendRequestRepository.save(friendRequestToSend);

        if (friendRequestRepository.findBySenderIdAndRecipientId(
                senderUser.getId(),recipientUser.getId())
                .isPresent()) {return true;}
        return false;
    }

    public boolean updateFriendRequest(String recipientUsername, String senderUsername, RequestStatus status){
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new NoSuchElementException("Отправитель не найден"));
        User receiver = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new NoSuchElementException("Получатель не найден"));

        FriendRequest friendRequest = friendRequestRepository.findBySenderIdAndRecipientId(
                sender.getId(), receiver.getId())
                .orElseThrow(() -> new NoSuchElementException("Заявка не найдена"));

        if (status.equals(RequestStatus.ACCEPTED)){
            sender.getFriends().add(receiver);
            receiver.getFriends().add(sender);
        }
        sender.getSendRequests().remove(friendRequest);
        receiver.getReceivedRequests().remove(friendRequest);

        userRepository.save(sender);
        userRepository.save(receiver);

        friendRequestRepository.delete(friendRequest);

        if (!friendRequestRepository.findBySenderIdAndRecipientId(
                        sender.getId(),receiver.getId()).isPresent()) {
            return true;}
        return false;
    }

    public Set<String> getAllFriends(String username){
        UserDTO userDTO = new UserDTO(userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Юзер не найден")));
        Set<String> friends = userDTO.getFriends();

        return friends;
    }

    public boolean deleteFriend(String userUsername, String friendUsername){
        User user = userRepository.findByUsername(userUsername)
                .orElseThrow(() -> new NoSuchElementException("Юзер не найден"));

        User friend = userRepository.findByUsername(friendUsername)
                .orElseThrow(() -> new NoSuchElementException("Друг не найден"));

        if (!user.getFriends().contains(friend)){
            throw new IllegalStateException("Этого пользователя нет у Вас в друзьях");
        }

        user.getFriends().remove(friend);
        friend.getFriends().remove(user);

        userRepository.save(user);
        userRepository.save(friend);

        if (!user.getFriends().contains(friend) && !friend.getFriends().contains(user)) {return true;}
        return false;
    }

    public boolean deleteUser(String userUsername){
        User user = userRepository.findByUsername(userUsername)
                .orElseThrow(() -> new NoSuchElementException("Юзер не найден"));

        userRepository.delete(user);

        if (userRepository.findByUsername(userUsername).isEmpty()) {
            userEventProducer.sendUserDeletedEvent(user.getUsername());
            return true;
        }
        return true;
    }
}
