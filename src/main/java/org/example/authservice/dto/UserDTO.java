package org.example.authservice.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.authservice.model.FriendRequest;
import org.example.authservice.model.Role;
import org.example.authservice.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class UserDTO {
    private Long id;
    private String username;
    private Role role;
    private List<String> sendRequests;
    private List<String> receivedRequests;
    private Set<String> friends;

    public UserDTO(User user){
        this.id = user.getId();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.sendRequests = user.getSendRequests().stream()
                .map(r -> r.getRecipient().getUsername())
                .toList();
        this.receivedRequests = user.getReceivedRequests().stream()
                .map(r -> r.getSender().getUsername())
                .toList();
        this.friends = user.getFriends().stream()
                .map(f -> f.getUsername())
                .collect(Collectors.toSet());
    }
}
