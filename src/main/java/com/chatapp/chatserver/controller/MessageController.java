package com.chatapp.chatserver.controller;

import com.chatapp.chatserver.model.Message;
import com.chatapp.chatserver.repository.MessageRepository;
import org.springframework.web.bind.annotation.*;
import com.chatapp.chatserver.repository.BlockedUserRepository;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin
public class MessageController {

    private final MessageRepository messageRepository;
    private final BlockedUserRepository blockedUserRepository;


    public MessageController(
            MessageRepository messageRepository,
            BlockedUserRepository blockedUserRepository
    ) {
        this.messageRepository = messageRepository;
        this.blockedUserRepository = blockedUserRepository;
    }

    // 🔹 Get full conversation between two users
    @GetMapping("/{user1}/{user2}")
    public List<Message> getConversation(
            @PathVariable String user1,
            @PathVariable String user2
    ) {
        return messageRepository
                .findBySenderAndReceiverOrSenderAndReceiver(
                        user1, user2,
                        user2, user1
                );
    }

    // 🔹 Get all users this person has chatted with
    @GetMapping("/conversations/{username}")
    public List<String> getConversations(@PathVariable String username) {

    	List<String> users = messageRepository.findConversationPartners(username);

        List<String> blockedUsers = blockedUserRepository
                .findByBlocker(username)
                .stream()
                .map(b -> b.getBlocked())
                .toList();

        return users.stream()
                .filter(user -> !blockedUsers.contains(user))
                .toList();
    }
    
    @DeleteMapping("/{id}")
    public void deleteMessage(@PathVariable Long id) {
        messageRepository.deleteById(id);
    }
    
    @PutMapping("/{id}")
    public Message updateMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        return messageRepository.findById(id)
                .map(msg -> {
                    msg.setContent(body.get("content"));
                    return messageRepository.save(msg);
                })
                .orElseThrow();
    }

}
