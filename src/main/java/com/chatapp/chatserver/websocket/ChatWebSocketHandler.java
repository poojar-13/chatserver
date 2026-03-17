package com.chatapp.chatserver.websocket;
import org.springframework.stereotype.Component;
import com.chatapp.chatserver.model.Message;
import com.chatapp.chatserver.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.List; 
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.chatapp.chatserver.repository.BlockedUserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
	private final MessageRepository messageRepository;
	private final BlockedUserRepository blockedUserRepository;
	public ChatWebSocketHandler(
	        MessageRepository messageRepository,
	        BlockedUserRepository blockedUserRepository
	) {
	    this.messageRepository = messageRepository;
	    this.blockedUserRepository = blockedUserRepository;
	}



	private static final Map<String, java.util.List<String>> privateHistory =
	        new ConcurrentHashMap<>();


    private static final Map<WebSocketSession, String> users =
            new ConcurrentHashMap<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        String username = (String) session.getAttributes().get("username");

        if (username == null) {
            session.close();
            return;
        }

        users.put(session, username);
        broadcast(username + " joined the chat");
        broadcastUserList();

        // ← ADD THIS: mark all undelivered messages as delivered
        List<Message> undelivered = messageRepository
            .findByReceiverAndStatus(username, "SENT");

        for (Message msg : undelivered) {
            msg.setStatus("DELIVERED");
            messageRepository.save(msg);

            // Notify the sender
            for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {
                if (entry.getValue().equals(msg.getSender()) 
                        && entry.getKey().isOpen()) {
                    entry.getKey().sendMessage(
                        new TextMessage("DELIVERED|" + msg.getId())
                    );
                }
            }
        }
    }


    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        String payload = message.getPayload();
        String username = users.get(session);
        if (username == null) return;
     // ✍️ TYPING INDICATOR
        if (payload.startsWith("TYPING:")) {

            String toUser = payload.split(":")[1];

            String typingMsg = "TYPING|" + username;

            for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {

                String connectedUser = entry.getValue();
                WebSocketSession userSession = entry.getKey();

                if (connectedUser.equals(toUser) && userSession.isOpen()) {
                    userSession.sendMessage(new TextMessage(typingMsg));
                }
            }

            return;
        }
        
     // 👁️ SEEN RECEIPT
        if (payload.startsWith("SEEN:")) {

            String fromUser = username;
            String toUser = payload.split(":")[1];

            // Mark BOTH sent and delivered messages as READ
            List<Message> delivered = messageRepository
                .findByReceiverAndStatus(fromUser, "DELIVERED");

            List<Message> sent = messageRepository
                .findByReceiverAndStatus(fromUser, "SENT");

            List<Message> allUnread = new java.util.ArrayList<>();
            allUnread.addAll(delivered);
            allUnread.addAll(sent);

            for (Message msg : allUnread) {
                if (msg.getSender().equals(toUser)) {
                    msg.setStatus("READ");
                    messageRepository.save(msg);
                }
            }

            String seenMsg = "SEEN|" + fromUser;
            System.out.println("SENDING SEEN TO: " + toUser + " | msg: " + seenMsg);

            for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {
                if (entry.getValue().equals(toUser) && entry.getKey().isOpen()) {
                    entry.getKey().sendMessage(new TextMessage(seenMsg));
                }
            }

            return;
        }

     // PRIVATE MESSAGE
     // PRIVATE MESSAGE
        if (payload.startsWith("DM:")) {

        	String[] parts = payload.split(":", 3);
        	if (parts.length == 3) {
        	    String toUser = parts[1];
        	    String msgBody = parts[2];
        	    String fromUser = username;

        	    // Parse optional file metadata appended as JSON
        	    String text = msgBody;
        	    String fileUrl = null;
        	    String fileType = null;
        	    String fileName = null;

        	    if (msgBody.startsWith("FILE:")) {
        	        // FORMAT: FILE:url|type|name|caption
        	        String fileData = msgBody.substring(5);
        	        String[] fileParts = fileData.split("\\|", 4);
        	        fileUrl = fileParts.length > 0 ? fileParts[0] : "";
        	        fileType = fileParts.length > 1 ? fileParts[1] : "";
        	        fileName = fileParts.length > 2 ? fileParts[2] : "";
        	        text = fileParts.length > 3 ? fileParts[3] : "";
        	    }

        	    boolean isBlocked =
        	        blockedUserRepository.existsByBlockerAndBlocked(fromUser, toUser)
        	        || blockedUserRepository.existsByBlockerAndBlocked(toUser, fromUser);

        	    if (isBlocked) return;

        	    String time = java.time.LocalTime.now()
        	        .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));

        	    Message dbMessage = new Message(fromUser, toUser, text, LocalDateTime.now());
        	    dbMessage.setFileUrl(fileUrl);
        	    dbMessage.setFileType(fileType);
        	    dbMessage.setFileName(fileName);
        	    dbMessage = messageRepository.save(dbMessage);

        	    String privateMsg =
        	        "PRIVATE|" + dbMessage.getId() + "|" +
        	        fromUser + "|" + toUser + "|" +
        	        (text != null ? text : "") + "|" +
        	        time + "|" +
        	        (fileUrl != null ? fileUrl : "") + "|" +
        	        (fileType != null ? fileType : "") + "|" +
        	        (fileName != null ? fileName : "");


                // 4️⃣ Check if receiver is online
                boolean receiverOnline = users.containsValue(toUser);

                if (receiverOnline) {

                    dbMessage.setStatus("DELIVERED");
                    messageRepository.save(dbMessage);

                    // Notify sender
                    for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {

                        if (entry.getValue().equals(fromUser)
                                && entry.getKey().isOpen()) {

                            entry.getKey().sendMessage(
                                new TextMessage("DELIVERED|" + dbMessage.getId())
                            );
                        }
                    }
                }

                // 5️⃣ Send message to both users
                sendPrivate(fromUser, toUser, privateMsg);
            }

            return;
        }

        
    }

    // BROADCAST TO ALL USERS
    private void broadcast(String message) throws Exception {
        for (WebSocketSession s : users.keySet()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(message));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {

        String username = users.remove(session);
        if (username != null) {
            try {
                broadcast(username + " left the chat");
                broadcastUserList();
            } catch (Exception ignored) {}
        }
    }

    // SEND ONLINE USERS LIST
    private void broadcastUserList() throws Exception {
        String userList = "USERS:" + String.join(",", users.values());

        for (WebSocketSession s : users.keySet()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(userList));
            }
        }
    }
 // 🔑 Generates a consistent key for a private chat (order-independent)
    private String getChatKey(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "|" + u2 : u2 + "|" + u1;
    }

    // 📩 Sends a private message only to sender and receiver
    private void sendPrivate(String from, String to, String msg) throws Exception {
        for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {
            String user = entry.getValue();
            WebSocketSession s = entry.getKey();

            if ((user.equals(from) || user.equals(to)) && s.isOpen()) {
                s.sendMessage(new TextMessage(msg));
            }
        }
    }
}
