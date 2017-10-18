package org.phlo.AirReceiver;

public class CurrentTrack {
    private static SongInfo songInfo;

    public static synchronized SongInfo getSongInfo() {
        return songInfo;
    }

    public static void setSongInfo(SongInfo songInfo) {
        CurrentTrack.songInfo = songInfo;
    }
}
