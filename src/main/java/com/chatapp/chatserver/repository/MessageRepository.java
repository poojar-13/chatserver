package com.chatapp.chatserver.repository;

import com.chatapp.chatserver.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findBySenderAndReceiverOrSenderAndReceiver(
	        String sender1,
	        String receiver1,
	        String sender2,
	        String receiver2
	);

	@Query("""
		    SELECT DISTINCT 
		        CASE 
		            WHEN m.sender = :username THEN m.receiver
		            ELSE m.sender
		        END
		    FROM Message m
		    WHERE m.sender = :username OR m.receiver = :username
		""")
		List<String> findConversationPartners(@Param("username") String username);

}
