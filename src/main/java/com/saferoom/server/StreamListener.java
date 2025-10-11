package com.saferoom.server;

import com.saferoom.grpc.UDPHoleImpl;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class StreamListener extends Thread{
	// SafeRoomServer'dan port bilgisini al
	public static int grpcPort = SafeRoomServer.grpcPort;

	public void run(){
		try {
		Server server = ServerBuilder.forPort(grpcPort)
				.addService(new UDPHoleImpl())
				.build()
				.start();
		
		System.out.println("✅ gRPC Server Started on port " + grpcPort);
		server.awaitTermination();
	}catch(Exception e) {
		System.err.println("Server Builder [ERROR]: " + e);
	}
	

}
	}
