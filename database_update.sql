-- Friend System Database Updates
-- Bu dosyayı MySQL'de çalıştır

USE saferoom;

-- 1. friend_requests tablosundaki status enum'unu güncelle
ALTER TABLE friend_requests 
MODIFY COLUMN status ENUM('pending', 'accepted', 'rejected') DEFAULT 'pending';

-- 2. friend_requests tablosuna responded_at kolonu ekle
ALTER TABLE friend_requests 
ADD COLUMN responded_at TIMESTAMP NULL AFTER created_at;

-- 3. Indexes'leri güncelle (performans için)
CREATE INDEX IF NOT EXISTS idx_friend_requests_created ON friend_requests(created_at);

-- 4. Sample friend requests for testing
-- Önce mevcut test data'yı temizle
DELETE FROM friend_requests;
DELETE FROM friendships;

-- Test friend requests ekle
INSERT INTO friend_requests (sender, receiver, message, status, created_at) VALUES
('James', 'ryuzaki', 'Hi! Would you like to be friends?', 'pending', NOW() - INTERVAL 1 HOUR),
('shadowwolf2', 'ryuzaki', 'Hey there!', 'pending', NOW() - INTERVAL 2 HOUR),
('ryuzaki1', 'James', 'Friend request from ryuzaki1', 'pending', NOW() - INTERVAL 30 MINUTE),
('nightfox3', 'ryuzaki', '', 'pending', NOW() - INTERVAL 45 MINUTE);

-- Test friendships ekle (accepted friend requests'den sonra)
INSERT INTO friendships (user1, user2, created_at) VALUES
('James', 'ryuzaki1', NOW() - INTERVAL 1 DAY),
('nightfox3', 'shadowwolf2', NOW() - INTERVAL 2 DAY),
('ironclaw4', 'ryuzaki', NOW() - INTERVAL 3 DAY);

-- 5. Test data için activities güncelle
INSERT INTO user_activities (username, activity_type, activity_description) VALUES 
('ryuzaki', 'friend_request_received', 'Received a friend request'),
('James', 'friend_request_sent', 'Sent a friend request'),
('shadowwolf2', 'friend_request_sent', 'Sent a friend request'),
('ryuzaki1', 'friend_added', 'Added a new friend'),
('James', 'friend_added', 'Added a new friend');

-- 6. Database integrity check
SELECT 'Friend Requests Count:' as info, COUNT(*) as count FROM friend_requests;
SELECT 'Friendships Count:' as info, COUNT(*) as count FROM friendships;
SELECT 'Users Count:' as info, COUNT(*) as count FROM users;

-- 7. Test queries
SELECT 'Pending requests for ryuzaki:' as test;
SELECT fr.id, fr.sender, fr.message, fr.created_at, u.email 
FROM friend_requests fr
JOIN users u ON fr.sender = u.username
WHERE fr.receiver = 'ryuzaki' AND fr.status = 'pending'
ORDER BY fr.created_at DESC;

SELECT 'Friends of ryuzaki:' as test;
SELECT 
    CASE 
        WHEN f.user1 = 'ryuzaki' THEN f.user2 
        ELSE f.user1 
    END as friend_username,
    f.created_at as friendship_date,
    u.email, u.last_login, u.is_verified
FROM friendships f
JOIN users u ON (
    CASE 
        WHEN f.user1 = 'ryuzaki' THEN f.user2 = u.username
        ELSE f.user1 = u.username
    END
)
WHERE f.user1 = 'ryuzaki' OR f.user2 = 'ryuzaki'
ORDER BY f.created_at DESC;

-- 8. Check statistics
SELECT 
    'ryuzaki' as username,
    (SELECT COUNT(*) FROM friendships WHERE user1 = 'ryuzaki' OR user2 = 'ryuzaki') as total_friends,
    (SELECT COUNT(*) FROM friend_requests WHERE receiver = 'ryuzaki' AND status = 'pending') as pending_requests,
    (SELECT COUNT(*) FROM friend_requests WHERE sender = 'ryuzaki' AND status = 'pending') as sent_requests;
