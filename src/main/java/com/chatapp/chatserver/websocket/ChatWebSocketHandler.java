package com.chatapp.chatserver.websocket;
import org.springframework.stereotype.Component;
import com.chatapp.chatserver.model.Message;
import com.chatapp.chatserver.repository.MessageRepository;
import java.time.LocalDateTime;
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

        // Username was set during JWT handshake
        String username = (String) session.getAttributes().get("username");

        if (username == null) {
            session.close();
            return;
        }

        users.put(session, username);
        broadcast(username + " joined the chat");
        broadcastUserList();
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

            String seenMsg = "SEEN|" + fromUser;

            for (Map.Entry<WebSocketSession, String> entry : users.entrySet()) {

                String connectedUser = entry.getValue();
                WebSocketSession userSession = entry.getKey();

                if (connectedUser.equals(toUser) && userSession.isOpen()) {
                    userSession.sendMessage(new TextMessage(seenMsg));
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
                String text = parts[2];
                String fromUser = username;

                boolean isBlocked =
                        blockedUserRepository.existsByBlockerAndBlocked(fromUser, toUser)
                        || blockedUserRepository.existsByBlockerAndBlocked(toUser, fromUser);

                if (isBlocked) {
                    System.out.println("Message blocked due to block relationship");
                    return;
                }

                String time = java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));

                // 1️⃣ Create message entity
                Message dbMessage = new Message(
                        fromUser,
                        toUser,
                        text,
                        LocalDateTime.now()
                );

                // 2️⃣ Save message (status = SENT)
                dbMessage = messageRepository.save(dbMessage);

                // 3️⃣ Now build private message WITH ID
                String privateMsg =
                        "PRIVATE|" + dbMessage.getId() + "|" +
                        fromUser + "|" +
                        toUser + "|" +
                        text + "|" +
                        time;

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
