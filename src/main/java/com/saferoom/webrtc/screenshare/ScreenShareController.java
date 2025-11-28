package com.saferoom.webrtc.screenshare;

import dev.onvoid.webrtc.RTCRtpSender;
import dev.onvoid.webrtc.media.video.VideoTrack;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Thin façade around {@link ScreenShareManager}. Keeps UI code free from media-specific
 * details while still exposing the lifecycle operations that higher layers need.
 */
public final class ScreenShareController implements AutoCloseable {

    private final ScreenShareManager manager;

    public ScreenShareController(ScreenShareManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public CompletableFuture<Void> startScreenShare() {
        return manager.startScreenShare();
    }

    public CompletableFuture<Void> startScreenShare(ScreenSourceOption option) {
        return manager.startScreenShare(option);
    }

    public CompletableFuture<Void> stopScreenShare() {
        return manager.stopScreenShare();
    }

    public boolean isSharing() {
        return manager.isScreenShareActive();
    }

    public void registerCameraSource(RTCRtpSender sender, VideoTrack track) {
        manager.registerCameraSource(sender, track);
    }

    @Override
    public void close() {
        manager.close();
    }
}
