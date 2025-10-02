package com.saferoom.file_transfer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NackFrame {
	public static final int SIZE = 20;
	
	public static ByteBuffer make(long fileId, int baseSeq, long mask64) {
		ByteBuffer b = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.BIG_ENDIAN);
		b.putLong(0, fileId);
		b.putInt(8, baseSeq);
        b.putLong(12, mask64);
        b.limit(SIZE);
        b.position(0);
        return b;
		}
	
    public static long fileId(ByteBuffer b) { return b.getLong(0); }
    public static int  baseSeq(ByteBuffer b) { return b.getInt(8); }
    public static long mask64(ByteBuffer b) { return b.getLong(12); }
	
}
