let username = localStorage.getItem("username");
let token = localStorage.getItem("jwt");
let currentOnlineUsers = [];
let allConversations = [];
let allUsers = [];

console.log("JWT FROM STORAGE:", token);

if (!username || !token) {
    window.location.href = "login.html";
}

function apiFetch(url, options = {}) {

    const defaultHeaders = {
        "Authorization": "Bearer " + token
    };

    options.headers = {
        ...defaultHeaders,
        ...options.headers
    };

    return fetch(url, options)
        .then(res => {

			if (res.status === 401) {

			    localStorage.clear();  
			    sessionStorage.setItem("sessionExpired", "true");
			    window.location.href = "login.html";

			    return Promise.reject("Unauthorized");
			}
            return res;
        })
        .catch(err => {
            console.error("API Error:", err);
            throw err;
        });
}

let selectedUser = null;
const chatStore = {};   // per-user chat history
const unread = {}; 
let chatBox;
let messageInput;
let sendBtn;
let onlineList;
let chatWith;    // unread message counters
const socket = new WebSocket(
    "ws://localhost:8080/chat?token=" + token
);

/* CONNECT DEBUG */
socket.onopen = () => {
    console.log("WEBSOCKET CONNECTED");
    socket.send(username);
};

socket.onerror = (e) => {
    console.log("WEBSOCKET ERROR", e);
};

socket.onclose = (e) => {
    console.log("WEBSOCKET CLOSED", e);
};


/* RECEIVE */
socket.onmessage = (event) => {
    const data = event.data;
    console.log("FROM SERVER:", data);


    // ONLINE USERS
	if (data.startsWith("USERS:")) {
	    currentOnlineUsers = data.replace("USERS:", "").split(",");
	    return;
	}
	
	// ✍️ TYPING INDICATOR
	if (data.startsWith("TYPING|")) {

	    const typingUser = data.split("|")[1];

	    if (typingUser === selectedUser) {

	        showTypingIndicator(typingUser);
	    }

	    return;
	}
	
	// 👁️ SEEN RECEIPT
	if (data.startsWith("SEEN|")) {

	    const seenByUser = data.split("|")[1];

	    if (seenByUser === selectedUser) {
	        showSeenIndicator();
	    }

	    return;
	}


    // PRIVATE MESSAGE
	if (data.startsWith("PRIVATE|")) {

	    const [, from, to, text, time] = data.split("|");
	    const otherUser = from === username ? to : from;

	    if (!chatStore[otherUser]) {
	        chatStore[otherUser] = [];
	    }

	    chatStore[otherUser].push({ from, text, time });

	    if (selectedUser === otherUser) {

	        addMessage(from === username, text, time);

	        // Clear unread when viewing
	        unread[otherUser] = 0;

	    } else {

	        unread[otherUser] = (unread[otherUser] || 0) + 1;
	    }

	    loadConversations(); // re-render sorted list

	    return;
	}
};

/* SEND */

function sendMessage() {
    const msg = messageInput.value.trim();

	if (!selectedUser) return;

	if (!msg) return;

	socket.send("DM:" + selectedUser + ":" + msg);
	messageInput.value = "";
}



/* MESSAGES */
function addMessage(isMe, text, time, id) {

    const msg = document.createElement("div");
    msg.className = "message " + (isMe ? "me" : "other");

    if (id) {
        msg.dataset.id = id;
    }

    msg.innerHTML = `
        <div class="bubble">
            <span class="message-text">${text}</span>
            <span class="timestamp">${time}</span>
        </div>
    `;

    // 🗑 Add delete button only for my messages
	if (isMe && id) {

	    // Add dropdown indicator
	    const dropdownIndicator = document.createElement("span");
	    dropdownIndicator.className = "message-dropdown";
	    dropdownIndicator.innerHTML = "▾";

	    msg.querySelector(".bubble").appendChild(dropdownIndicator);

	    msg.onclick = (e) => {
	        e.stopPropagation();
	        showMessageMenu(id, msg);
	    };
	}

    chatBox.appendChild(msg);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function renderChat(user) {
    chatBox.innerHTML = "";

    (chatStore[user] || []).forEach(msg => {
        addMessage(msg.from === username, msg.text, msg.time);
    });

    unread[user] = 0;
    updateUsersListUI();
}

function updateUsersListUI() {
    const items = document.querySelectorAll("#onlineList li");

    items.forEach(li => {
        const user = li.dataset.user;

        if (unread[user] > 0) {
            li.classList.add("unread");
        } else {
            li.classList.remove("unread");
        }
    });
}

function updateUserList(users) {
    const userList = document.getElementById("onlineList");
    userList.innerHTML = "";

    users.forEach(user => {
        if (user === username) return;

        const li = document.createElement("li");
        li.textContent = user;
        li.dataset.user = user;

        if (unread[user] > 0) {
            li.classList.add("unread");
        }

        li.onclick = () => {
            selectedUser = user;
            chatWith.textContent = "Chat with " + user;
			
            messageInput.disabled = false;
            messageInput.focus();
			
            loadConversation(username, user);
        };


        userList.appendChild(li);
    });
}

function loadConversation(user1, user2) {

    console.log("LOADING CONVERSATION:", user1, user2);

	apiFetch(`/api/messages/${user1}/${user2}`)
    .then(res => res.json())
    .then(messages => {

        console.log("MESSAGES RECEIVED:", messages);

        chatBox.innerHTML = "";

        messages.forEach(msg => {
            const isMe = msg.sender === username;
			addMessage(isMe, msg.content, formatTime(msg.timestamp), msg.id);
        });

    })
    .catch(err => {
        console.error("Failed to load conversation", err);
    });
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit'
    });
}

function loadConversations() {

	apiFetch(`/api/messages/conversations/${username}`)
    .then(res => res.json())
    .then(users => {

        // For each user, fetch last message
        const promises = users.map(user => {

            return apiFetch(`/api/messages/${username}/${user}`)
            .then(res => res.json())
            .then(messages => {

                const lastMessage = messages[messages.length - 1];

                return {
                    user,
                    lastMessage: lastMessage ? lastMessage.content : "",
                    timestamp: lastMessage ? lastMessage.timestamp : 0
                };
            });
        });

        Promise.all(promises).then(conversations => {

            // Sort by newest message
            conversations.sort((a, b) => b.timestamp - a.timestamp);

            renderConversationList(conversations);
        });

    });
}

function renderConversationList(conversations) {

    onlineList.innerHTML = "";

    conversations.forEach(conv => {
		const user = conv.user;
		const lastMessage = conv.lastMessage;
		const time = conv.timestamp ? formatTime(conv.timestamp) : "";
		const unreadCount = unread[user] || 0;
        const isOnline = currentOnlineUsers.includes(user);

        const li = document.createElement("li");

		li.innerHTML = `
		  <div class="avatar-wrapper">
		    <div class="avatar-circle">
		      ${user.charAt(0).toUpperCase()}
		    </div>
		  </div>

		  <div class="convo-content">

		    <div class="convo-header">
		      <span class="convo-name ${unreadCount > 0 ? 'unread-name' : ''}">
		        ${user}
		      </span>

		      <span class="convo-time">
		        ${time}
		      </span>
		    </div>

		    <div class="convo-footer">
		      <span class="convo-preview">
		        ${lastMessage}
		      </span>

		      ${unreadCount > 0 ? 
		        `<span class="unread-badge">${unreadCount}</span>` 
		        : ""}
		    </div>

		  </div>
		`;		
        li.dataset.user = user;

        li.onclick = () => {
			unread[user] = 0;
            selectedUser = user;
            chatWith.textContent = user;
			const chatAvatar = document.getElementById("chatAvatar");
			chatAvatar.textContent = user.charAt(0).toUpperCase();
            messageInput.disabled = false;
			sendBtn.disabled = false;
            messageInput.focus();
            loadConversation(username, user);
            socket.send("SEEN:" + user);
			updateBlockButton();
        };

        onlineList.appendChild(li);
    });
}

function updateOnlineStatus(onlineUsers) {

    const items = document.querySelectorAll("#onlineList li");

    items.forEach(li => {
        const user = li.dataset.user;

        if (onlineUsers.includes(user)) {
            li.classList.add("online");
        } else {
            li.classList.remove("online");
        }
    });
}

function loadAllUsers() {
	apiFetch("/api/users")
    .then(res => res.json())
    .then(users => {
        allUsers = users.filter(u => u !== username);
    });
}

document.addEventListener("DOMContentLoaded", function () {
	
	
	//block button
	const blockUserBtn = document.getElementById("blockUserBtn");

	blockUserBtn.addEventListener("click", () => {

	    if (!selectedUser) return;

	    const isBlocking = blockUserBtn.textContent === "Block";

	    const url = isBlocking ? "/api/users/block" : "/api/users/unblock";
	    const method = isBlocking ? "POST" : "DELETE";

	    apiFetch(url, {
	        method: method,
	        headers: {
	            "Content-Type": "application/json"
	        },
	        body: JSON.stringify({
	            blocker: username,
	            blocked: selectedUser
	        })
	    })
	    .then(res => res.text())
	    .then(msg => {
			showToast(msg, msg.toLowerCase().includes("success") ? "success" : "error");
	        updateBlockButton();
	    });
	});

    chatBox = document.getElementById("chat-box");
    messageInput = document.getElementById("messageInput");
    sendBtn = document.getElementById("sendBtn");
	sendBtn.disabled = true;
    onlineList = document.getElementById("onlineList");
    chatWith = document.getElementById("chat-with");
	sendBtn.onclick = sendMessage;

	messageInput.onkeydown = (e) => {
	    if (e.key === "Enter") sendMessage();
	};

	messageInput.addEventListener("input", () => {
	    if (!selectedUser) return;
	    socket.send("TYPING:" + selectedUser);
	});
	loadConversations();
	loadAllUsers();
	attachUIEvents();
	
	const hamburger = document.getElementById("hamburger");
	const sidePanel = document.getElementById("sidePanel");
	const overlay = document.getElementById("overlay");
	const logoutBtnPanel = document.getElementById("logoutBtnPanel");
	const panelAvatar = document.getElementById("panelAvatar");
	const panelUsername = document.getElementById("panelUsername");

	panelAvatar.textContent = username.charAt(0).toUpperCase();
	panelUsername.textContent = username;

	hamburger.addEventListener("click", function () {
	    sidePanel.classList.add("open");
	    overlay.classList.add("show");
	});

	overlay.addEventListener("click", function () {
	    sidePanel.classList.remove("open");
	    overlay.classList.remove("show");
	});

	logoutBtnPanel.addEventListener("click", function () {
	    localStorage.clear();
	    window.location.href = "login.html";
	});
	
	// ===== NEW CHAT MODAL =====

	const newChatBtn = document.getElementById("newChatBtn");
	const newChatModal = document.getElementById("newChatModal");
	const newChatSearch = document.getElementById("newChatSearch");
	const newChatUserList = document.getElementById("newChatUserList");
	const closeNewChatBtn = document.getElementById("closeNewChatBtn");

	// Open modal
	newChatBtn.addEventListener("click", () => {
	    newChatModal.classList.add("show");
	    newChatSearch.value = "";
	    renderUserSearchList(allUsers);
	});

	// Close modal
	closeNewChatBtn.addEventListener("click", () => {
	    newChatModal.classList.remove("show");
	});

	// Live search filter
	newChatSearch.addEventListener("input", () => {
	    const query = newChatSearch.value.toLowerCase();

	    const filtered = allUsers.filter(user =>
	        user.toLowerCase().includes(query)
	    );

	    renderUserSearchList(filtered);
	});

	function renderUserSearchList(users) {

	    newChatUserList.innerHTML = "";

	    users.forEach(user => {

	        const li = document.createElement("li");
	        li.textContent = user;

	        li.onclick = () => {

	            selectedUser = user;
	            chatWith.textContent = user;
	            messageInput.disabled = false;
				sendBtn.disabled = false;
	            messageInput.focus();

	            loadConversation(username, user);

	            newChatModal.classList.remove("show");
				updateBlockButton();
	        };

	        newChatUserList.appendChild(li);
	    });
	}
	
	// ===== SETTINGS & MODALS =====

	const settingsModal = document.getElementById("settingsModal");
	const passwordModal = document.getElementById("passwordModal");
	const blockedModal = document.getElementById("blockedModal");

	const openSettingsBtn = document.getElementById("openSettings");
	const changePasswordBtn = document.getElementById("changePassword");
	const viewBlockedBtn = document.getElementById("viewBlocked");

	const closeSettingsBtn = document.getElementById("closeSettingsBtn");
	const closePasswordBtn = document.getElementById("closePasswordBtn");
	const closeBlockedBtn = document.getElementById("closeBlockedBtn");

	const currentUsernameInput = document.getElementById("currentUsername");
	const blockedList = document.getElementById("blockedList");

	// Open Settings
	openSettingsBtn.addEventListener("click", () => {
	    currentUsernameInput.value = username;
		settingsModal.classList.add("show");
	});

	// Open Change Password
	changePasswordBtn.addEventListener("click", () => {
		passwordModal.classList.add("show");
	});

	// Open Blocked Users
	viewBlockedBtn.addEventListener("click", () => {

		apiFetch(`/api/users/blocked/${username}`)
	    .then(res => res.json())
	    .then(users => {

	        blockedList.innerHTML = "";

	        users.forEach(user => {
	            const li = document.createElement("li");
	            li.innerHTML = `
	                <div class="blocked-row">
	                    <div class="blocked-user-left">
	                        <div class="blocked-avatar">
	                            ${user.charAt(0).toUpperCase()}
	                        </div>
	                        <span class="blocked-name">${user}</span>
	                    </div>
	                    <button class="unblock-btn" onclick="unblockUser('${user}')">
	                        Unblock
	                    </button>
	                </div>
	            `;
	            blockedList.appendChild(li);
	        });

	        blockedModal.classList.add("show");
	    });
	});

	// Close modals
	closeSettingsBtn.addEventListener("click", () => {
	    settingsModal.classList.remove("show");
	});

	closePasswordBtn.addEventListener("click", () => {
	    passwordModal.classList.remove("show");
	});

	closeBlockedBtn.addEventListener("click", () => {
	    blockedModal.classList.remove("show");
	});

  
});

function updateBlockButton() {
    const btn = document.getElementById("blockUserBtn");

    if (!selectedUser) {
        btn.style.display = "none";
        return;
    }

    btn.style.display = "inline-block";

	apiFetch(`/api/users/is-blocked/${username}/${selectedUser}`)
    .then(res => res.json())
    .then(data => {
        btn.textContent = data.blocked ? "Unblock" : "Block";
    });
}

function unblockUser(user) {

	apiFetch("/api/users/unblock", {
	    method: "DELETE",
	    headers: {
	        "Content-Type": "application/json"
	    },
		body: JSON.stringify({
		    blocker: username,
		    blocked: user
		})
	})
    .then(res => res.text())
    .then(data => {
		showToast(
		    data,
		    data.toLowerCase().includes("success") ? "success" : "error"
		);
		updateBlockButton();
    });
}

function showTypingIndicator(user) {

    let typingDiv = document.getElementById("typingIndicator");

    if (!typingDiv) {
        typingDiv = document.createElement("div");
        typingDiv.id = "typingIndicator";
        typingDiv.style.fontSize = "12px";
        typingDiv.style.opacity = "0.6";
        typingDiv.style.margin = "6px 18px";
        typingDiv.style.fontStyle = "italic";
        document.querySelector(".chat-container").appendChild(typingDiv);
    }

    typingDiv.textContent = user + " is typing...";

    clearTimeout(window.typingTimeout);

    window.typingTimeout = setTimeout(() => {
        typingDiv.textContent = "";
    }, 1500);
}

function showSeenIndicator() {

    let seenDiv = document.getElementById("seenIndicator");

    if (!seenDiv) {
        seenDiv = document.createElement("div");
        seenDiv.id = "seenIndicator";
        seenDiv.style.fontSize = "11px";
        seenDiv.style.opacity = "0.6";
        seenDiv.style.marginRight = "20px";
        seenDiv.style.textAlign = "right";
        seenDiv.style.marginBottom = "8px";
        document.getElementById("chat-box").appendChild(seenDiv);
    }

    seenDiv.textContent = "Seen";
}

let messageToDeleteId = null;
let messageToDeleteElement = null;

function deleteMessage(id, messageElement) {

    messageToDeleteId = id;
    messageToDeleteElement = messageElement;

    document.getElementById("deleteModal").classList.add("show");
}

function editMessage(id, messageElement) {

    const bubble = messageElement.querySelector(".bubble");
    const textSpan = bubble.querySelector(".message-text");

    const oldText = textSpan.textContent;

    const input = document.createElement("input");
    input.type = "text";
    input.value = oldText;
    input.style.width = "70%";
    input.style.fontSize = "12px";

    bubble.innerHTML = "";
    bubble.appendChild(input);

    input.focus();

    input.addEventListener("keydown", (e) => {

        if (e.key === "Enter") {

			apiFetch(`/api/messages/${id}`, {
			    method: "PUT",
			    headers: {
			        "Content-Type": "application/json"
			    },
				body: JSON.stringify({
				    content: input.value
				})
			})
            .then(res => res.json())
            .then(updated => {

                bubble.innerHTML = `
                    <span class="message-text">${updated.content}</span>
                    <span class="timestamp">${formatTime(updated.timestamp)}</span>
                `;
            });
        }
    });
}

function showMessageMenu(id, messageElement) {

    removeExistingMenu();

    const menu = document.createElement("div");
    menu.className = "message-menu";

    menu.innerHTML = `
        <div class="menu-item" onclick="handleEdit(${id})">Edit</div>
        <div class="menu-item delete" onclick="handleDelete(${id})">Delete</div>
    `;

    messageElement.appendChild(menu);

    setTimeout(() => {
        document.addEventListener("click", removeExistingMenu, { once: true });
    }, 0);
}

function removeExistingMenu() {
    const existing = document.querySelector(".message-menu");
    if (existing) existing.remove();
}

function handleEdit(id) {
    removeExistingMenu();
    const messageElement = document.querySelector('[data-id="' + id + '"]');
    editMessage(id, messageElement);
}

function handleDelete(id) {
    removeExistingMenu();
    const messageElement = document.querySelector('[data-id="' + id + '"]');
    deleteMessage(id, messageElement);
}

function attachUIEvents() {

    const savePasswordBtn = document.getElementById("savePasswordBtn");
    const passwordMessage = document.getElementById("passwordMessage");
    const saveUsernameBtn = document.getElementById("saveUsernameBtn");
    const usernameMessage = document.getElementById("usernameMessage");
    const passwordModal = document.getElementById("passwordModal");
    const settingsModal = document.getElementById("settingsModal");

    savePasswordBtn.addEventListener("click", () => {

        const oldPassword = document.getElementById("oldPasswordInput").value.trim();
        const newPassword = document.getElementById("newPasswordInput").value.trim();
        const confirmPassword = document.getElementById("confirmPasswordInput").value.trim();

        if (!oldPassword || !newPassword || !confirmPassword) {
            passwordMessage.textContent = "All fields are required";
            return;
        }

        if (newPassword !== confirmPassword) {
            passwordMessage.textContent = "Passwords do not match";
            return;
        }

		apiFetch("/api/users/change-password", {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ oldPassword, newPassword })
        })
        .then(res => res.text())
        .then(msg => {
            passwordMessage.textContent = msg;

            if (msg === "Password changed successfully") {
                setTimeout(() => {
                    passwordModal.classList.remove("show");
                }, 1200);
            }
        });
    });

    saveUsernameBtn.addEventListener("click", () => {

        const newUsername = document.getElementById("newUsernameInput").value.trim();

        if (!newUsername) {
            usernameMessage.textContent = "Username cannot be empty";
            return;
        }
		
		if (newUsername === username) {
		    usernameMessage.textContent = "New username must be different";
		    return;
		}

		apiFetch("/api/users/update-username", {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ newUsername })
        })
        .then(res => res.text())
        .then(msg => {
            usernameMessage.textContent = msg;

            if (msg === "Username updated successfully") {
                setTimeout(() => {
                    localStorage.clear();
                    window.location.href = "login.html";
                }, 1500);
            }
        });
    });
}

function showToast(message, type = "success") {

    const toast = document.createElement("div");
    toast.className = "toast " + type;
    toast.textContent = message;

    document.body.appendChild(toast);

    setTimeout(() => {
        toast.classList.add("show");
    }, 10);

    setTimeout(() => {
        toast.classList.remove("show");
        setTimeout(() => toast.remove(), 300);
    }, 2000);
}