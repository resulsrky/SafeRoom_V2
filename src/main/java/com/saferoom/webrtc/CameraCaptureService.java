package com.saferoom.webrtc;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.media.MediaDevices;
import dev.onvoid.webrtc.media.video.VideoCaptureCapability;
import dev.onvoid.webrtc.media.video.VideoDevice;
import dev.onvoid.webrtc.media.video.VideoDeviceSource;
import dev.onvoid.webrtc.media.video.VideoTrack;

import java.util.List;

/**
 * Tek noktadan kamera capture kaynağı oluşturan yardımcı servis.
 * DM ve grup görüşmeleri aynı çözünürlük/FPS/GPU ayarlarını buradan alır.
 */
public final class CameraCaptureService {

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;
    private static final int DEFAULT_FPS = 30;

    private CameraCaptureService() {}

    public static CameraCaptureResource createCameraTrack(String trackId) {
        return createCameraTrack(trackId, new CaptureProfile(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS));
    }

    public static CameraCaptureResource createCameraTrack(String trackId, CaptureProfile profile) {
        System.out.println("[CameraCaptureService] ═══════════════════════════════════════════");
        System.out.println("[CameraCaptureService] Creating camera track: " + trackId);
        
        PeerConnectionFactory factory = WebRTCClient.getFactory();
        if (factory == null) {
            throw new IllegalStateException("[CameraCaptureService] PeerConnectionFactory bulunamadı");
        }

        // List all available cameras
        List<VideoDevice> cameras = MediaDevices.getVideoCaptureDevices();
        System.out.printf("[CameraCaptureService] Found %d camera(s):%n", cameras.size());
        for (int i = 0; i < cameras.size(); i++) {
            System.out.printf("  [%d] %s%n", i, cameras.get(i).getName());
        }
        
        if (cameras.isEmpty()) {
            throw new IllegalStateException("[CameraCaptureService] Kamera bulunamadı");
        }

        VideoDevice camera = cameras.get(0);
        System.out.println("[CameraCaptureService] Selected camera: " + camera.getName());

        VideoDeviceSource source = new VideoDeviceSource();
        source.setVideoCaptureDevice(camera);
        System.out.println("[CameraCaptureService] VideoDeviceSource created");

        VideoCaptureCapability capability = new VideoCaptureCapability(
            profile.width(),
            profile.height(),
            profile.fps()
        );
        source.setVideoCaptureCapability(capability);
        System.out.printf("[CameraCaptureService] Capture capability set: %dx%d@%dfps%n",
            profile.width(), profile.height(), profile.fps());

        VideoTrack track = factory.createVideoTrack(trackId, source);
        track.setEnabled(true);
        System.out.printf("[CameraCaptureService] VideoTrack created: id=%s, enabled=%b%n", 
            track.getId(), track.isEnabled());

        try {
            source.start();
            System.out.println("[CameraCaptureService] ✅ Camera capture STARTED successfully");
        } catch (Exception e) {
            System.err.printf("[CameraCaptureService] ❌ Failed to start camera: %s%n", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Camera start failed", e);
        }
        
        System.out.println("[CameraCaptureService] ═══════════════════════════════════════════");
        return new CameraCaptureResource(source, track);
    }

    public static final class CameraCaptureResource {
        private final VideoDeviceSource source;
        private final VideoTrack track;

        public CameraCaptureResource(VideoDeviceSource source, VideoTrack track) {
            this.source = source;
            this.track = track;
        }

        public VideoDeviceSource getSource() {
            return source;
        }

        public VideoTrack getTrack() {
            return track;
        }
    }

    public record CaptureProfile(int width, int height, int fps) {}
}

