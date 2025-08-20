package org.example.authservice.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.authservice.model.FriendRequest;
import org.example.authservice.model.RequestStatus;
import org.example.authservice.model.User;

@Getter
@Setter
public class FriendRequestDTO {
    private String sender;
    private String receiver;
    private RequestStatus status;

    public FriendRequestDTO(FriendRequest friendRequest) {
        this.sender = friendRequest.getSender().getUsername();
        this.receiver = friendRequest.getReceiver().getUsername();
        this.status = friendRequest.getStatus();
    }
}
