package com.saferoom.file_transfer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SHA256_Packet {
	public static final int OFF_FILE_ID = 0;
	public static final int OFF_FILE_SIZE = 8;
	public static final int OFF_TOTAL_SEQ = 16;
	public static final int OFF_SIGNATURE = 20;
	
	public static final int HEADER_SIZE = 52;
	
	private final ByteBuffer header;
	
	public SHA256_Packet() {
		this.header = ByteBuffer.allocateDirect(52).order(ByteOrder.BIG_ENDIAN);
	}
	
	public ByteBuffer get_header()
	{
		return header;
	}
	
	public void fill_signature(long fileId, long fileSize, int totalSeq, byte[] signature) {
		header.clear();
		
		
		header.putLong(OFF_FILE_ID, fileId);
		header.putLong(OFF_FILE_SIZE, fileSize);
		header.putInt(OFF_TOTAL_SEQ, totalSeq);
		
		header.position(OFF_SIGNATURE);
		header.put(signature);
		
		header.limit(HEADER_SIZE);
		header.position(0);
		
	}
	
	public void reset_for_retransmitter() {
			header.position(0).limit(HEADER_SIZE);
	}

}
