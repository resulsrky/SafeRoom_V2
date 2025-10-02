package com.saferoom.file_transfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

public class NackListener implements Runnable{
	public final DatagramChannel channel;
	public final long fileId;
	public final int totalSeq;
	public final ConcurrentLinkedQueue<Integer> retxQueue;
	public final int backoffNs;
	
    public static final int DEFAULT_BACKOFF_NS = 200_000;

	public NackListener(DatagramChannel channel,
			long fileId,
			int totalSeq,
			ConcurrentLinkedQueue<Integer> retxQueue,
			int backoffNs){
        this.channel   = channel;
        this.fileId    = fileId;
        this.totalSeq  = totalSeq;
        this.retxQueue = retxQueue;
        this.backoffNs = backoffNs > 0 ? backoffNs : DEFAULT_BACKOFF_NS;
	}
	
	@Override
	public void run() {
		final ByteBuffer ctrl = ByteBuffer.allocateDirect(NackFrame.SIZE);
		while(!Thread.currentThread().isInterrupted()) {
			ctrl.clear();
			try {
				int r = channel.read(ctrl); //READ ONLY FROM CONNECTED PEER
				if(r <= 0) {
					LockSupport.parkNanos(backoffNs);
					continue;
				}
				if(ctrl.position() < NackFrame.SIZE){
					continue;
				}
				ctrl.flip();
				
				long fid = NackFrame.fileId(ctrl);
				if(fid != fileId) {
					continue;
				}
				int base = NackFrame.baseSeq(ctrl);
				long mask = NackFrame.mask64(ctrl);
				
				for(int i = 0; i < 64; i++){
					int seq = base + i;
					if(seq >= totalSeq) break;
					boolean received = ((mask >>> i) & 1L) == 1L;
					if(!received) {
						retxQueue.offer(seq);
					}
				}
				
			}catch(IOException e) {
				System.out.println("IO Error: " + e);
				LockSupport.parkNanos(backoffNs);
			}
		}
	}
}