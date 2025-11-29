# SafeRoom - Encrypted Persistent Messaging Layer

## 📋 Overview

Tam şifreli, persistent (kalıcı) mesajlaşma altyapısı. RAM-based mevcut sistemi korurken SQLite + SQLCipher ile disk persistence ekler.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                           UI Layer                               │
│  ListView<Message> + MessageCell (Reactive UI with Bindings)    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                      RAM Layer (ChatService)                     │
│  Map<String, ObservableList<Message>> channelMessages           │
│  - In-memory message storage                                    │
│  - Auto-notify UI via JavaFX Properties                         │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                   Architecture Glue                              │
│  - MessagePersister (RAM → Disk)                                │
│  - PersistentChatLoader (Disk → RAM)                            │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                 Repository Layer                                 │
│  LocalMessageRepository (Async operations, Thread-safe)         │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                    Storage Layer                                 │
│  - LocalDatabase (SQLite + SQLCipher)                           │
│  - MessageDao (CRUD operations)                                  │
│  - FTS5SearchService (Full-text search)                         │
│  - MediaExtractorService (Thumbnails)                           │
│  - SqlCipherHelper (Encryption keys)                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Security

### Encryption

- **SQLCipher AES-256** encryption
- **PBKDF2** key derivation (250,000 iterations)
- **Master password** → AES key derivation
- Salt: SHA-256(username)

### Key Derivation

```java
String encryptionKey = SqlCipherHelper.deriveKey(username, password);
// Returns hex-encoded 256-bit key
```

---

## 📦 Components

### 1. Storage Layer

**LocalDatabase.java**
- SQLite connection with SQLCipher
- Schema management
- Transaction support

**MessageDao.java**
- CRUD operations
- Serialization/deserialization
- Thumbnail handling

**SqlCipherHelper.java**
- Key derivation (PBKDF2)
- Conversation ID generation

### 2. Repository Layer

**LocalMessageRepository.java**
- Async persistence API
- Thread-safe operations
- Batch operations

### 3. Search & Media

**FTS5SearchService.java**
- Full-text search
- Ranked results
- Snippet highlighting

**MediaExtractorService.java**
- PDF thumbnails (PDFBox)
- Video thumbnails (FFmpeg)
- Image scaling

### 4. Architecture Glue

**PersistentChatLoader.java**
- Load history on startup
- Hydrate RAM from disk
- Pagination support

**MessagePersister.java**
- Auto-persist on send/receive
- Thumbnail extraction
- Batch operations

---

## 🚀 Integration Guide

### Step 1: Initialize Database (Login)

```java
// After user login
String userDataDir = System.getProperty("user.home") + "/.saferoom/data";
LocalDatabase database = LocalDatabase.initialize(username, password, userDataDir);
```

### Step 2: Initialize Repository & Glue

```java
LocalMessageRepository repository = LocalMessageRepository.initialize(database);
MessagePersister persister = MessagePersister.initialize(repository);
PersistentChatLoader loader = new PersistentChatLoader(repository);
```

### Step 3: Connect to ChatService

```java
ChatService chatService = ChatService.getInstance();
chatService.setCurrentUsername(username);
chatService.initializePersistence(persister, loader);
```

### Step 4: Load History for Active Chats

```java
// Load history for a conversation
chatService.loadConversationHistory("remoteUsername")
    .thenAccept(count -> {
        System.out.println("Loaded " + count + " messages");
    });
```

### Step 5: Messages Auto-Persist

```java
// Send message - automatically persists
chatService.sendMessage(channelId, "Hello!", currentUser);

// Receive message - automatically persists
chatService.receiveP2PMessage(sender, receiver, messageText);
```

---

## 🔍 Full-Text Search

### Search Within Conversation

```java
FTS5SearchService searchService = new FTS5SearchService(database);
List<SearchResult> results = searchService.searchInConversation(
    "search query", 
    conversationId, 
    50 // limit
);

for (SearchResult result : results) {
    System.out.println("Message: " + result.getContent());
    System.out.println("Rank: " + result.getRank());
}
```

### Get Highlighted Snippet

```java
String snippet = searchService.getHighlightedSnippet(query, messageId);
// Returns: "...found <mark>search</mark> term in message..."
```

---

## 🎨 Shared Media

### Load Media Messages

```java
repository.loadMediaMessagesAsync(conversationId)
    .thenAccept(mediaMessages -> {
        Platform.runLater(() -> {
            // Display in GridView
            mediaMessages.forEach(msg -> {
                Image thumbnail = msg.getAttachment().getThumbnail();
                // Add to UI
            });
        });
    });
```

---

## 📊 Database Schema

### Messages Table

```sql
CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    type TEXT NOT NULL,
    content TEXT,              -- Encrypted JSON
    thumbnail BLOB,
    file_path TEXT,
    is_outgoing INTEGER NOT NULL,
    sender_id TEXT NOT NULL,
    sender_avatar_char TEXT
);

CREATE INDEX idx_conv_time ON messages(conversation_id, timestamp ASC);
```

### FTS5 Virtual Table

```sql
CREATE VIRTUAL TABLE messages_fts USING fts5(
    message_id UNINDEXED,
    content,
    conversation_id UNINDEXED,
    tokenize = 'unicode61'
);
```

### Triggers (Auto-sync FTS)

```sql
CREATE TRIGGER messages_ai AFTER INSERT ON messages 
BEGIN
    INSERT INTO messages_fts(rowid, message_id, content, conversation_id)
    VALUES (new.rowid, new.id, new.content, new.conversation_id);
END;
```

---

## ⚡ Performance

- **WAL mode** for concurrent reads
- **Async writes** (non-blocking UI)
- **Batch operations** with transactions
- **Cell recycling** (ListView virtualization)
- **Lazy loading** for long conversations

---

## 🧪 Testing

### Test Persistence

```java
// Disable persistence for testing
MessagePersister.getInstance().setEnabled(false);

// Re-enable
MessagePersister.getInstance().setEnabled(true);
```

---

## 📝 Migration from RAM-Only

**No Breaking Changes!**
- Existing code works as-is
- Persistence is opt-in
- If not initialized, system works in RAM-only mode

---

## 🛠️ Dependencies Added

```gradle
// SQLite + Encryption
implementation 'org.xerial:sqlite-jdbc:3.44.1.0'

// JSON
implementation 'com.google.code.gson:gson:2.10.1'

// Crypto
implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
```

---

## 🔒 Best Practices

1. **Always call** `loadConversationHistory()` before displaying chat
2. **Never block UI** thread - all disk ops are async
3. **Use transactions** for batch inserts
4. **Close database** on logout:
   ```java
   LocalDatabase.getInstance().close();
   ```

---

## 📂 File Structure

```
src/main/java/com/saferoom/
├── storage/
│   ├── LocalDatabase.java
│   ├── MessageDao.java
│   ├── LocalMessageRepository.java
│   ├── SqlCipherHelper.java
│   ├── FTS5SearchService.java
│   └── MediaExtractorService.java
└── chat/
    ├── MessagePersister.java
    └── PersistentChatLoader.java
```

---

## 🎯 Next Steps

1. **UI Integration** - Add search bar to ChatView
2. **Shared Media Panel** - GridView for media messages
3. **Export/Import** - Backup/restore conversations
4. **Message Reactions** - Store in separate table
5. **Read Receipts** - Timestamp tracking

---

## 💡 Example: Complete Integration

```java
// Login flow
public void onUserLogin(String username, String password) {
    // 1. Initialize database
    String dataDir = System.getProperty("user.home") + "/.saferoom/data";
    LocalDatabase db = LocalDatabase.initialize(username, password, dataDir);
    
    // 2. Initialize layers
    LocalMessageRepository repo = LocalMessageRepository.initialize(db);
    MessagePersister persister = MessagePersister.initialize(repo);
    PersistentChatLoader loader = new PersistentChatLoader(repo);
    
    // 3. Connect to ChatService
    ChatService chat = ChatService.getInstance();
    chat.setCurrentUsername(username);
    chat.initializePersistence(persister, loader);
    
    // 4. Load all active conversations
    for (String contact : activeContacts) {
        chat.loadConversationHistory(contact);
    }
}

// Logout flow
public void onUserLogout() {
    LocalDatabase.getInstance().close();
    LocalMessageRepository.getInstance().shutdown();
    MediaExtractorService.getInstance().shutdown();
}
```

---

## 🚨 Troubleshooting

**Database locked?**
- Check if another instance is running
- Ensure WAL mode is enabled

**FFmpeg not found?**
- Install FFmpeg: `sudo apt install ffmpeg`
- Check with: `MediaExtractorService.getInstance().isFFmpegAvailable()`

**Performance issues?**
- Use batch operations for bulk inserts
- Optimize FTS index: `searchService.optimizeIndex()`

---

✅ **Status:** Production Ready
📅 **Version:** 1.0.0
🔐 **Security:** AES-256 Encrypted

