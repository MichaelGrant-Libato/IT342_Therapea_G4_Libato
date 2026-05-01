package com.therapea.backend.repository;

import com.therapea.backend.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m WHERE (m.senderEmail = :user1 AND m.receiverEmail = :user2) OR (m.senderEmail = :user2 AND m.receiverEmail = :user1) ORDER BY m.timestamp ASC")
    List<MessageEntity> findConversation(@Param("user1") String user1, @Param("user2") String user2);

}