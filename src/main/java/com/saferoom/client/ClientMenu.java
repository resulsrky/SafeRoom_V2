package com.saferoom.client;

import com.saferoom.grpc.SafeRoomProto;
import com.saferoom.grpc.SafeRoomProto.Request_Client;
import com.saferoom.grpc.SafeRoomProto.Status;
import com.saferoom.grpc.UDPHoleGrpc;
import com.saferoom.grpc.SafeRoomProto.Verification;
import com.saferoom.server.SafeRoomServer;
import com.saferoom.natghost.NatAnalyzer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class ClientMenu{
	public static String Server = SafeRoomServer.ServerIP;
	public static int Port = SafeRoomServer.grpcPort;
	public static int UDP_Port = SafeRoomServer.udpPort1;

	public static ManagedChannel channel;
	static{
		channel = ManagedChannelBuilder.forAddress(Server, Port)
					.usePlaintext()
					.build();
	}
		public static String Login(String username, String Password)
		{
		try {
			UDPHoleGrpc.UDPHoleBlockingStub client = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS);
			SafeRoomProto.Menu main_menu = SafeRoomProto.Menu.newBuilder()
				.setUsername(username)
				.setHashPassword(Password)
				.build();
			SafeRoomProto.Status stats = client.menuAns(main_menu);
			
			String message = stats.getMessage();
			int code = stats.getCode();
			switch(code){
				case 0:
					System.out.println("Success!");
					System.out.printf("Logged in as: %s%n", username);
					return message; // Server'dan gelen eksik bilgiyi döndür (email veya username)
				case 1:
					if(message.equals("N_REGISTER")){
						System.out.println("Not Registered");
						return "N_REGISTER";
					}else if(message.equals("WRONG_PASSWORD")){
						System.out.println("Wrong Password");
						return "WRONG_PASSWORD";
						}else{
							System.out.println("Blocked User");
							return "BLOCKED_USER";
						}
			default:
					System.out.println("Message has broken");
					return "ERROR";					
				}
			}catch(Exception e){
				return e.toString();
			}
		}

	public static int register_client(String username, String password, String mail)
	{
		try {
			UDPHoleGrpc.UDPHoleBlockingStub stub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.Create_User insert_obj = SafeRoomProto.Create_User.newBuilder()
				.setUsername(username)
				.setEmail(mail)
				.setPassword(password)
				.setIsVerified(false)
				.build();
			SafeRoomProto.Status stat = stub.insertUser(insert_obj);

			int code = stat.getCode();
			String message = stat.getMessage();
			
			switch(code){
				case 0:
					System.out.println("Success!");
					return 0;
				case 2:
					if(message.equals("VUSERNAME")){
						System.out.println("Username already taken");
						return 1;
					}else{
						System.out.println("Invalid E-mail");
						return 2;
					}
				default:
					System.out.println("Message has broken");
					return 3;					
			}
		} catch(Exception e){
			System.err.println("Register Error: " + e);
			return -5;

		}
	}
	public static int verify_user(String username, String verify_code) {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub stub = UDPHoleGrpc.newBlockingStub(channel)
					.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			Verification verification_info = Verification.newBuilder()
					.setUsername(username)
					.setVerify(verify_code)
					.build();
			
			SafeRoomProto.Status response = stub.verifyUser(verification_info);
			
			int code = response.getCode();
			
			switch(code) {
			case 0:
				System.out.println("Verification Completed");
				return 0;
			case 1:
				System.out.println("Not Matched");
				return 1;
			
			default:
				System.out.println("Connection is not safe");
				return 2;
			}
		}catch(Exception e){
			System.err.println("Verificate Client Error: " + e);
			return -7;
		}
	}

	public static boolean verify_email(String mail){
		try{
			UDPHoleGrpc.UDPHoleBlockingStub client = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS);
			
			SafeRoomProto.Request_Client request = SafeRoomProto.Request_Client.newBuilder()
				.setUsername(mail)
				.build();
			
			SafeRoomProto.Status status = client.verifyEmail(request);

			int code = status.getCode();

			if(code == 1){
				return true;
			}
			
		}catch(Exception e){
			System.err.println("Verify Channel Error: " + e);
		}
		return false;
	}

	public static int changePassword(String email, String newPassword) {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub stub = UDPHoleGrpc.newBlockingStub(channel);
			// Format: "email:newpassword"
			String requestData = email + ":" + newPassword;
			Request_Client request = Request_Client.newBuilder()
				.setUsername(requestData)
				.build();
			
			Status response = stub.changePassword(request);
			int code = response.getCode();
			String message = response.getMessage();
			
			System.out.println("Change Password Response: " + message + " (Code: " + code + ")");
			
			return code;
			
		} catch (Exception e) {
			System.err.println("Change Password Channel Error: " + e);
			return 2; // Error code
		}
	}

	public static java.util.List<java.util.Map<String, Object>> searchUsers(String searchTerm, String currentUser) throws Exception {
		try {
				UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.SearchRequest request = SafeRoomProto.SearchRequest.newBuilder()
				.setSearchTerm(searchTerm)
				.setCurrentUser(currentUser)
				.build();
				
			SafeRoomProto.SearchResponse response = blockingStub.searchUsers(request);
			
			java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
			if (response.getSuccess()) {
				for (SafeRoomProto.UserResult user : response.getUsersList()) {
					java.util.Map<String, Object> userMap = new java.util.HashMap<>();
					userMap.put("username", user.getUsername());
					userMap.put("email", user.getEmail());
					userMap.put("isOnline", user.getIsOnline());
					userMap.put("lastSeen", user.getLastSeen());
					userMap.put("is_friend", user.getIsFriend());
					userMap.put("has_pending_request", user.getHasPendingRequest());
					
					// Debug log
					System.out.println("Search Result for " + user.getUsername() + ":");
					System.out.println("  - is_friend: " + user.getIsFriend());
					System.out.println("  - has_pending_request: " + user.getHasPendingRequest());
					
					results.add(userMap);
				}
			}
			return results;
		}catch(Exception e){
			System.err.println("Search User Client Error: " + e);
			return null;
		}
	}

	public static SafeRoomProto.ProfileResponse getProfile(String targetUsername, String currentUser) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.ProfileRequest request = SafeRoomProto.ProfileRequest.newBuilder()
				.setUsername(targetUsername)
				.setRequestedBy(currentUser)
				.build();
				
			SafeRoomProto.ProfileResponse response = blockingStub.getProfile(request);
			return response;
		}catch(Exception e){
			System.err.println("Get Profile Error: " + e);
			return null;
		}
		}

	public static SafeRoomProto.FriendResponse sendFriendRequest(String fromUser, String toUser) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.FriendRequest request = SafeRoomProto.FriendRequest.newBuilder()
				.setSender(fromUser)
				.setReceiver(toUser)
				.setMessage("") // Boş mesaj
				.build();
				
			SafeRoomProto.FriendResponse response = blockingStub.sendFriendRequest(request);
			return response;
		}catch(Exception e){
			System.err.println("Send Friend Request Error: " + e);
			return null;
		} 
		}

	// ===============================
	// FRIEND SYSTEM CLIENT METHODS
	// ===============================

	/**
	 * Bekleyen arkadaşlık isteklerini getir (gelen istekler)
	 */
	public static SafeRoomProto.PendingRequestsResponse getPendingFriendRequests(String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.Request_Client request = SafeRoomProto.Request_Client.newBuilder()
				.setUsername(username)
				.build();
				
			return blockingStub.getPendingFriendRequests(request);
		}catch(Exception e ){
			System.err.println("Get Pending Request Error: " + e);
			return null;
		}
		}

	/**
	 * Gönderilen arkadaşlık isteklerini getir (giden istekler)
	 */
	public static SafeRoomProto.SentRequestsResponse getSentFriendRequests(String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.Request_Client request = SafeRoomProto.Request_Client.newBuilder()
				.setUsername(username)
				.build();
				
			return blockingStub.getSentFriendRequests(request);
		}catch(Exception e){
			System.err.println("Get Friend Request Error: " + e);
			return null;
		} 
		}

	/**
	 * Arkadaşlık isteğini kabul et
	 */
	public static SafeRoomProto.Status acceptFriendRequest(int requestId, String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.FriendRequestAction request = SafeRoomProto.FriendRequestAction.newBuilder()
				.setRequestId(requestId)
				.setUsername(username)
				.build();
				
			return blockingStub.acceptFriendRequest(request);
		}catch(Exception e){
			System.err.println("Accept Friend Error: " + e);
			return null;
			}
		}

	/**
	 * Arkadaşlık isteğini reddet
	 */
	public static SafeRoomProto.Status rejectFriendRequest(int requestId, String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.FriendRequestAction request = SafeRoomProto.FriendRequestAction.newBuilder()
				.setRequestId(requestId)
				.setUsername(username)
				.build();
				
			return blockingStub.rejectFriendRequest(request);
		}catch(Exception e){
			System.err.println("Reject Frind Client Error: " + e);
			return null;
		}	
	}

	/**
	 * Gönderilen arkadaşlık isteğini iptal et
	 */
	public static SafeRoomProto.Status cancelFriendRequest(int requestId, String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.FriendRequestAction request = SafeRoomProto.FriendRequestAction.newBuilder()
				.setRequestId(requestId)
				.setUsername(username)
				.build();
				
			return blockingStub.cancelFriendRequest(request);
		}catch(Exception e){
			System.err.println("Cancel Frind Client Error: " + e);
			return null;
		}
		}

	/**
	 * Arkadaş listesini getir
	 */
	public static SafeRoomProto.FriendsListResponse getFriendsList(String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.Request_Client request = SafeRoomProto.Request_Client.newBuilder()
				.setUsername(username)
				.build();
				
			return blockingStub.getFriendsList(request);
		}catch(Exception e){
			System.err.println("Get Friend List: " + e);
			return null;
		} 
		}

	/**
	 * Arkadaşı kaldır
	 */
	public static SafeRoomProto.Status removeFriend(String user1, String user2) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.RemoveFriendRequest request = SafeRoomProto.RemoveFriendRequest.newBuilder()
				.setUser1(user1)
				.setUser2(user2)
				.build();
				
			return blockingStub.removeFriend(request);
		}catch(Exception e){
			System.err.println("Remove Friend Client Error: " + e);
			return null;
			} 
		}

	/**
	 * Arkadaşlık istatistiklerini getir
	 */
	public static SafeRoomProto.FriendshipStatsResponse getFriendshipStats(String username) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);
			
			SafeRoomProto.Request_Client request = SafeRoomProto.Request_Client.newBuilder()
				.setUsername(username)
				.build();
				
			return blockingStub.getFriendshipStats(request);
		}catch(Exception e){
			System.err.println("GetFriendShipStats Client Error: " + e);
			return null;
		} 
		}

	/**
	 * Heartbeat gönder
	 */
	public static SafeRoomProto.HeartbeatResponse sendHeartbeat(String username, String sessionId) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(5, TimeUnit.SECONDS);
			
			SafeRoomProto.HeartbeatRequest request = SafeRoomProto.HeartbeatRequest.newBuilder()
				.setUsername(username)
				.setSessionId(sessionId)
				.build();
				
			return blockingStub.sendHeartbeat(request);
		}catch(Exception e){
			System.err.println("Heartbeat Error Client: " + e);
			return null;
		} 
		}

	/**
	 * User session'ını sonlandır
	 */
	public static SafeRoomProto.Status endUserSession(String username, String sessionId) throws Exception {
		try {
			UDPHoleGrpc.UDPHoleBlockingStub blockingStub = UDPHoleGrpc.newBlockingStub(channel)
				.withDeadlineAfter(5, TimeUnit.SECONDS);
			
			SafeRoomProto.HeartbeatRequest request = SafeRoomProto.HeartbeatRequest.newBuilder()
				.setUsername(username)
				.setSessionId(sessionId)
				.build();
				
			return blockingStub.endUserSession(request);
		}catch(Exception e){
			System.err.println("End User Sessions Error: " + e);
			return null;
		}	}
	
	// ============================================
	// P2P HOLE PUNCHING METHODS
	// ============================================
	
	/**
	 * Register user with P2P signaling server on application startup
	 * @param username Username to register
	 * @return true if registration successful
	 */
	public static boolean registerP2PUser(String username) {
		try {
			System.out.println("[P2P] Registering user with server: " + username);
			
			// Set current username in ChatService for message rendering
			com.saferoom.gui.service.ChatService.getInstance().setCurrentUsername(username);
			
			// WEBRTC P2P: Initialize P2PConnectionManager for messaging
			System.out.println("[P2P] Initializing WebRTC P2P messaging for: " + username);
			
			// Initialize P2PConnectionManager (shares WebRTCSignalingClient with CallManager)
			com.saferoom.p2p.P2PConnectionManager.getInstance().initialize(username);
			
			System.out.println("[P2P] WebRTC P2P ready (connections will establish when friends come online)");
			
			// WebRTC callbacks are registered in registerP2PUser() method
			// (See setupWebRTCCallbacks in P2PConnectionManager)
			
			return true;
			
		} catch (Exception e) {
			System.err.println("[P2P] Error during user registration: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * ASYNC NON-BLOCKING P2P Connection Request
	 * Start hole punch asynchronously and return CompletableFuture
	 * 
	 * @param myUsername Current user's username
	 * @param targetUsername Target user to connect to
	 * @return CompletableFuture<Boolean> that completes when P2P establishes
	 */
	public static java.util.concurrent.CompletableFuture<Boolean> startP2PHolePunchAsync(String myUsername, String targetUsername) {
		try {
			System.out.println("[P2P]Initiating ASYNC P2P request: " + myUsername + " -> " + targetUsername);
			
			// Create signaling server address
			InetSocketAddress signalingServer = new InetSocketAddress(Server, UDP_Port);
			
			// Use new async unidirectional P2P request system
			return NatAnalyzer.requestP2PConnectionAsync(myUsername, targetUsername, signalingServer)
				.thenApply(success -> {
					if (success) {
						System.out.println("[P2P]Async P2P connection successful");
					} else {
						System.out.println("[P2P]Async P2P failed - will use server relay");
					}
					return success;
				})
				.exceptionally(e -> {
					System.err.println("[P2P]Error during async P2P: " + e.getMessage());
					return false;
				});
			
		} catch (Exception e) {
			System.err.println("[P2P]Error initializing async P2P: " + e.getMessage());
			e.printStackTrace();
			return java.util.concurrent.CompletableFuture.completedFuture(false);
		}
	}
	
	/**
	 * 🔴 DEPRECATED - Start P2P hole punching process with target user (BLOCKING VERSION)
	 * Use startP2PHolePunchAsync() instead to avoid ForkJoinPool exhaustion
	 * 
	 * @param myUsername Current user's username
	 * @param targetUsername Target user to connect to
	 * @return true if hole punch successful, false if should use server relay
	 * @deprecated Use {@link #startP2PHolePunchAsync(String, String)} instead
	 */
	@Deprecated
	public static boolean startP2PHolePunch(String myUsername, String targetUsername) {
		try {
			System.out.println("[P2P] Initiating UNIDIRECTIONAL P2P request: " + myUsername + " -> " + targetUsername);
			
			// Create signaling server address - use correct P2P signaling port
			InetSocketAddress signalingServer = new InetSocketAddress(Server, UDP_Port); // P2PSignalingServer.SIGNALING_PORT
			
			// Use new unidirectional P2P request system
			boolean success = NatAnalyzer.requestP2PConnection(myUsername, targetUsername, signalingServer);
			
			if (success) {
				System.out.println("[P2P] Unidirectional P2P connection successful");
				return true;
			} else {
				System.out.println("[P2P] Unidirectional P2P failed - will use server relay");
				return false;
			}
			
		} catch (Exception e) {
			System.err.println("[P2P] Error during unidirectional P2P: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Legacy hole punch method for backward compatibility
	 * @param myUsername Current user's username
	 * @param targetUsername Target user to connect to
	 * @return true if hole punch successful, false if should use server relay
	 */
	public static boolean startLegacyP2PHolePunch(String myUsername, String targetUsername) {
		try {
			System.out.println("[P2P] Initiating LEGACY hole punch: " + myUsername + " -> " + targetUsername);
			
			// Create signaling server address - use correct P2P signaling port
			InetSocketAddress signalingServer = new InetSocketAddress(Server, UDP_Port); // P2PSignalingServer.SIGNALING_PORT
			
			// Perform hole punch using NatAnalyzer (old method)
			boolean success = NatAnalyzer.performHolePunch(myUsername, targetUsername, signalingServer);
			
			if (success) {
				System.out.println("[P2P] Legacy hole punch successful - P2P connection established");
				return true;
			} else {
				System.out.println("[P2P] Legacy hole punch failed - will use server relay");
				return false;
			}
			
		} catch (Exception e) {
			System.err.println("[P2P] Error during legacy hole punch: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Analyze NAT type for current network
	 * @return NAT type: 0x00=Full Cone/Restricted, 0x11=Symmetric, 0xFE=Error
	 */
	public static byte analyzeNAT() {
		try {
			System.out.println("[P2P] Analyzing NAT type...");
			byte natType = NatAnalyzer.analyzeSinglePort(NatAnalyzer.stunServers);
			
			String natTypeStr = switch (natType) {
				case (byte)0x00 -> "Full Cone/Restricted NAT (P2P Friendly)";
				case (byte)0x11 -> "Symmetric NAT (P2P Challenging)";
				case (byte)0xFE -> "NAT Analysis Failed";
				default -> "Unknown NAT Type";
			};
			
			System.out.println("[P2P] NAT Type: " + natTypeStr);
			return natType;
			
		} catch (Exception e) {
			System.err.println("[P2P] NAT analysis error: " + e.getMessage());
			return (byte)0xFE;
		}
	}
	
	/**
	 * Get current public IP and port info
	 * @return String array [publicIP, publicPort, natType] or null if failed
	 */
	public static String[] getPublicInfo() {
		try {
			byte natType = analyzeNAT();
			if (natType == (byte)0xFE || NatAnalyzer.myPublicIP == null || NatAnalyzer.Public_PortList.isEmpty()) {
				return null;
			}
			
			return new String[] {
				NatAnalyzer.myPublicIP,
				String.valueOf(NatAnalyzer.Public_PortList.get(0)),
				String.format("0x%02X", natType)
			};
			
		} catch (Exception e) {
			System.err.println("[P2P] Error getting public info: " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Send P2P message if connection is active with specific peer
	 * @param sender Current user's username
	 * @param receiver Target user's username  
	 * @param message Text message to send
	 * @return true if sent via P2P, false if should use server relay
	 */
	public static boolean sendP2PMessage(String sender, String receiver, String message) {
		if (NatAnalyzer.isP2PActive(receiver)) {
			return NatAnalyzer.sendP2PMessage(sender, receiver, message);
		} else {
			System.out.printf("[P2P] No active P2P connection with %s - use server relay%n", receiver);
			return false;
		}
	}
	
	/**
	 * Check if P2P messaging is available with specific peer
	 */
	public static boolean isP2PMessagingAvailable(String username) {
		// Use WebRTC DataChannel (P2PConnectionManager) instead of NatAnalyzer
		return com.saferoom.p2p.P2PConnectionManager.getInstance().hasActiveConnection(username);
	}
	
	/**
	 * Check if any P2P messaging is available
	 */
	public static boolean isP2PMessagingAvailable() {
		// WebRTC DataChannel doesn't need global check - check per user
		return true; // If user has P2P button, they can attempt connection
	}

}


	
