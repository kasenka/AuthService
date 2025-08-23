package org.example.authservice.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "friend_requests")

@Getter
@Setter

@AllArgsConstructor
@NoArgsConstructor
public class FriendRequest {

    public FriendRequest(User sender, User recipient, RequestStatus status) {
        this.sender = sender;
        this.recipient = recipient;
        this.status = status;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // кто отправил заявку
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    @JsonIgnore
    private User sender;

    // кому отправлена заявка
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    @JsonIgnore
    private User recipient;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;
}

