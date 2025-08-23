package org.example.authservice.repository;

import org.example.authservice.model.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    Optional<FriendRequest> findBySenderIdAndRecipientId(Long sender_id, Long recipient_id);
    List<FriendRequest> findAllByRecipientId(Long resipient_id);
    List<FriendRequest> findAllBySenderId(Long sender_id);
}
