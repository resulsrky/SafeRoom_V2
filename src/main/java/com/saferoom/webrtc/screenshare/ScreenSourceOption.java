package com.saferoom.webrtc.screenshare;

import dev.onvoid.webrtc.media.video.desktop.DesktopSource;

import java.util.Objects;

/**
 * Represents a cross-platform, user-facing capture target. Wraps dev.onvoid {@link DesktopSource}
 * instances for Windows/macOS while providing symbolic entries (such as "Entire Screen") for Linux.
 */
public final class ScreenSourceOption {

    public enum Kind {
        AUTO,
        LINUX_ENTIRE_DESKTOP,
        ONVOID_SCREEN,
        ONVOID_WINDOW
    }

    private final Kind kind;
    private final DesktopSource desktopSource;
    private final String title;
    private final int monitorIndex;

    private ScreenSourceOption(Kind kind, DesktopSource desktopSource, String title, int monitorIndex) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.desktopSource = desktopSource;
        this.title = title != null ? title : defaultTitle(kind, desktopSource, monitorIndex);
        this.monitorIndex = monitorIndex;
    }

    public static ScreenSourceOption auto() {
        return new ScreenSourceOption(Kind.AUTO, null, null, -1);
    }

    public static ScreenSourceOption linuxEntireDesktop(String title) {
        return new ScreenSourceOption(Kind.LINUX_ENTIRE_DESKTOP, null, title, -1);
    }

    public static ScreenSourceOption linuxMonitor(String title, int monitorIndex) {
        return new ScreenSourceOption(Kind.LINUX_ENTIRE_DESKTOP, null, title, monitorIndex);
    }

    public static ScreenSourceOption onvoidScreen(DesktopSource source) {
        return new ScreenSourceOption(Kind.ONVOID_SCREEN, Objects.requireNonNull(source, "source"),
            source.title, -1);
    }

    public static ScreenSourceOption onvoidWindow(DesktopSource source) {
        return new ScreenSourceOption(Kind.ONVOID_WINDOW, Objects.requireNonNull(source, "source"),
            source.title, -1);
    }

    public Kind kind() {
        return kind;
    }

    public DesktopSource desktopSource() {
        return desktopSource;
    }

    public String title() {
        return title;
    }

    public boolean isWindow() {
        return kind == Kind.ONVOID_WINDOW;
    }

    public boolean isLinux() {
        return kind == Kind.LINUX_ENTIRE_DESKTOP;
    }

    public int monitorIndex() {
        return monitorIndex;
    }

    private static String defaultTitle(Kind kind, DesktopSource source, int monitorIndex) {
        if (source != null && source.title != null && !source.title.isBlank()) {
            return source.title;
        }
        return switch (kind) {
            case LINUX_ENTIRE_DESKTOP -> monitorIndex >= 0
                ? "Monitor " + (monitorIndex + 1)
                : "Entire Screen";
            case ONVOID_SCREEN -> "Screen";
            case ONVOID_WINDOW -> "Window";
            default -> "Automatic";
        };
    }
}