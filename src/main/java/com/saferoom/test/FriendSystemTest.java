package com.saferoom.test;

import com.saferoom.client.ClientMenu;
import com.saferoom.grpc.SafeRoomProto;

public class FriendSystemTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 Friend System Test Starting...");
        
        String testUser1 = "testuser1";
        String testUser2 = "testuser2";
        
        try {
            // Test 1: Send Friend Request
            System.out.println("\n1️⃣ Testing Send Friend Request...");
            SafeRoomProto.FriendResponse friendResponse = 
                ClientMenu.sendFriendRequest(testUser1, testUser2);
            
            if (friendResponse.getSuccess()) {
                System.out.println("✅ Friend request sent successfully!");
                System.out.println("Status: " + friendResponse.getStatus());
                System.out.println("Message: " + friendResponse.getMessage());
            } else {
                System.out.println("❌ Failed to send friend request: " + friendResponse.getMessage());
            }
            
            // Test 2: Get Pending Requests
            System.out.println("\n2️⃣ Testing Get Pending Requests...");
            SafeRoomProto.PendingRequestsResponse pendingResponse = 
                ClientMenu.getPendingFriendRequests(testUser2);
            
            if (pendingResponse.getSuccess()) {
                System.out.println("✅ Pending requests retrieved successfully!");
                System.out.println("Found " + pendingResponse.getRequestsCount() + " pending requests");
                
                for (SafeRoomProto.FriendRequestInfo request : pendingResponse.getRequestsList()) {
                    System.out.println("  - From: " + request.getSender());
                    System.out.println("    Message: " + request.getMessage());
                    System.out.println("    Sent: " + request.getSentAt());
                }
            } else {
                System.out.println("❌ Failed to get pending requests: " + pendingResponse.getMessage());
            }
            
            // Test 3: Get Friends List
            System.out.println("\n3️⃣ Testing Get Friends List...");
            SafeRoomProto.FriendsListResponse friendsResponse = 
                ClientMenu.getFriendsList(testUser1);
            
            if (friendsResponse.getSuccess()) {
                System.out.println("✅ Friends list retrieved successfully!");
                System.out.println("Found " + friendsResponse.getFriendsCount() + " friends");
                
                for (SafeRoomProto.FriendInfo friend : friendsResponse.getFriendsList()) {
                    System.out.println("  - Friend: " + friend.getUsername());
                    System.out.println("    Email: " + friend.getEmail());
                    System.out.println("    Verified: " + friend.getIsVerified());
                }
            } else {
                System.out.println("❌ Failed to get friends list: " + friendsResponse.getMessage());
            }
            
            // Test 4: Get Friendship Stats
            System.out.println("\n4️⃣ Testing Get Friendship Stats...");
            SafeRoomProto.FriendshipStatsResponse statsResponse = 
                ClientMenu.getFriendshipStats(testUser1);
            
            if (statsResponse.getSuccess()) {
                System.out.println("✅ Friendship stats retrieved successfully!");
                SafeRoomProto.FriendshipStats stats = statsResponse.getStats();
                System.out.println("  - Total Friends: " + stats.getTotalFriends());
                System.out.println("  - Pending Requests: " + stats.getPendingRequests());
                System.out.println("  - Sent Requests: " + stats.getSentRequests());
            } else {
                System.out.println("❌ Failed to get friendship stats: " + statsResponse.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n🏁 Friend System Test Completed!");
    }
}
