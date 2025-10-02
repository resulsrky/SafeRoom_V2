package com.saferoom.file_transfer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class CRC32C_Packet {
    public static final int OFF_FILE_ID  = 0;
    public static final int OFF_SEQ      = 8;
    public static final int OFF_TOTAL    = 12;
    public static final int OFF_PLEN     = 16;
    public static final int OFF_CRC      = 18;
    public static final int HEADER_SIZE  = 22;

    private final ByteBuffer header;

    public CRC32C_Packet() {
        this.header = ByteBuffer.allocateDirect(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    public ByteBuffer headerBuffer() {
        return header;
    }

    public void fillHeader(long fileId, int seqNo, int totalSeq, int payloadLen, int crc32c) {
        header.clear(); 

        header.putLong(OFF_FILE_ID, fileId);
        header.putInt (OFF_SEQ,     seqNo);
        header.putInt (OFF_TOTAL,   totalSeq);
        header.putShort(OFF_PLEN,   (short) (payloadLen & 0xFFFF));
        header.putInt (OFF_CRC,     crc32c);

        header.limit(HEADER_SIZE);
        header.position(0);
    }

    public void resetForRetry() {
        header.position(0).limit(HEADER_SIZE);
    }
}
