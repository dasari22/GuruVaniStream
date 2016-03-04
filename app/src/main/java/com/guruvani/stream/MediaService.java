/**
 Copyright:
 2015/2016 Sunny Dasari
 developer.google.com

 This file is part of AustrianPublicStream.

 GuruVani is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 GuruVani is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with GuruVani.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.guruvani.stream;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * The media service class provides an interface to the VLC player as a service.
 *
 * It can be controlled via onCommand().
 * The telephone state is also handled here
 *
 * Via start/stopForeground the service can be brought to foreground, enabling a jitter free playback
 *
 * In some occasions there might be a problem with the wifi/wakelocks. Should be investigated sometime.
 */
public class MediaService extends Service implements IVLCVout.Callback, LibVLC.HardwareAccelerationError {
    public final static String ACTION_PLAY_PAUSE = "com.guruvani.stream.action.PLAYPAUSE";
    public final static String ACTION_PLAY = "com.guruvani.stream.action.PLAY";
    public final static String ACTION_PAUSE = "com.guruvani.stream.action.PAUSE";
    public final static String ACTION_STOP = "com.guruvani.stream.action.STOP";
    public final static String ACTION_SETTIME = "com.guruvani.stream.action.SETTIME";
    public final static String ACTION_LOAD = "com.guruvani.stream.action.LOAD";

    public final static String MEDIA_STATE_IDLE = "com.guruvani.stream.mediastate.IDLE";
    public final static String MEDIA_STATE_PLAYING = "com.guruvani.stream.mediastate.PLAYING";
    public final static String MEDIA_STATE_PAUSED = "com.guruvani.stream.mediastate.PAUSED";

    private MediaPlayer mMediaPlayer = null;
    private LibVLC libvlc;
    private String mState = MEDIA_STATE_IDLE;
    private String mStatePrevious = MEDIA_STATE_IDLE;
    private MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private boolean isLive = false;
    PhoneStateListener phoneStateListener;

    @Override
    public IBinder onBind(Intent intent) {
        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "StreamWifiLock");
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "StreamWakeLock");

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    mStatePrevious = mState;
                    onCommand(ACTION_PAUSE,"");
                } else if(state == TelephonyManager.CALL_STATE_IDLE) {
                    if(mStatePrevious.equals(MEDIA_STATE_PLAYING)) onCommand(ACTION_PLAY,"");
                } else if(state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    mStatePrevious = mState;
                    onCommand(ACTION_PAUSE,"");
                }
                super.onCallStateChanged(state, incomingNumber);
            }
        };
        //return new LocalBinder<MediaService>(this);
        return new LocalBinder<>(this);
    }


    public boolean onCommand(String command, String parameter) {
        TelephonyManager mgr;
        switch(command) {
            case ACTION_PLAY_PAUSE:
                switch(mState) {
                    case MEDIA_STATE_PAUSED:
                        wifiLock.acquire();
                        wakeLock.acquire();
                        mMediaPlayer.play();
                        mState = MEDIA_STATE_PLAYING;

                        mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if(mgr != null) {
                            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                        }
                        break;
                    case MEDIA_STATE_PLAYING:
                        mMediaPlayer.pause();
                        mState = MEDIA_STATE_PAUSED;
                        wifiLock.release();
                        wakeLock.release();

                        mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if(mgr != null) {
                            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                        }
                        break;
                }
                break;
            case ACTION_PAUSE:
                switch(mState) {
                    case MEDIA_STATE_PLAYING:
                        mMediaPlayer.pause();
                        mState = MEDIA_STATE_PAUSED;
                        wifiLock.release();
                        wakeLock.release();

                        mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if(mgr != null) {
                            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                        }
                        break;
                }
                break;
            case ACTION_PLAY:
                switch(mState) {
                    case MEDIA_STATE_PAUSED:
                        wifiLock.acquire();
                        wakeLock.acquire();
                        mMediaPlayer.play();
                        mState = MEDIA_STATE_PLAYING;

                        mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if(mgr != null) {
                            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                        }
                        break;
                }
                break;
            case ACTION_STOP:
                switch(mState) {
                    case MEDIA_STATE_PAUSED:
                    case MEDIA_STATE_PLAYING:
                        mMediaPlayer.stop();
                        mState = MEDIA_STATE_IDLE;
                        wifiLock.release();
                        wakeLock.release();

                        mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if(mgr != null) {
                            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                        }
                        break;
                }
                break;
            case ACTION_LOAD:
                    isLive = parameter.equals(ORFParser.ORF_LIVE_URL);
                    createPlayer(parameter);
                    wifiLock.acquire();
                    wakeLock.acquire();
                    mState = MEDIA_STATE_PLAYING;

                    mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                    if(mgr != null) {
                        mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                    }
                break;
            case ACTION_SETTIME:
                //float position = Float.valueOf(parameter);
                if(mMediaPlayer != null) {
                    mMediaPlayer.setPosition(Float.valueOf(parameter));
                }
                break;
            default:
                return false;

        }
        return true;
    }

    @Override
    public void onDestroy() {
        if(mMediaPlayer != null) {
            try {
                mMediaPlayer.release();
            } catch (Exception e) {
                Log.e("MEDIASERVICE_DESTROY",e.getMessage());
            }
            try {
                wifiLock.release();
            } catch (Exception e) {
                Log.e("MEDIASERVICE_DESTROY",e.getMessage());
            }
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e("MEDIASERVICE_DESTROY",e.getMessage());
            }
            mMediaPlayer = null;
        }
    }

    public int getDuration() {
        switch(mState) {
            case MEDIA_STATE_PLAYING:
                try {
                    return safeLongToInt(mMediaPlayer.getLength());
                } catch (NullPointerException e) {
                    return -1;
                }
            case MEDIA_STATE_PAUSED:
                return -2;
            default:
                return -1;
        }
    }

    public int getCurrentPosition() {
        switch(mState) {
            case MEDIA_STATE_PLAYING:
                try {
                    return safeLongToInt(mMediaPlayer.getTime());
                } catch (NullPointerException e) {
                    return 0;
                }
            case MEDIA_STATE_PAUSED:
                return -2;
            default:
                return -1;
        }
    }

    //these methods are not used, it works with the internal service methods start-/stopForeground()
    /*public void startForegroundMedia(int notificationId, Notification notification) {
        this.startForeground(notificationId,notification);
    }

    public void stopForegroundMedia() {
        this.stopForeground(false);
    }*/

    @Override
    public void onNewLayout(IVLCVout ivlcVout, int i, int i1, int i2, int i3, int i4, int i5) {

    }

    @Override
    public void onSurfacesCreated(IVLCVout ivlcVout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout ivlcVout) {

    }

    @Override
    public void eventHardwareAccelerationError() {
        Toast.makeText(MediaService.this, "HardwareAccelerationError...", Toast.LENGTH_SHORT).show();
    }

    private void createPlayer(String media) {
        releasePlayer();
        try {
            // Create LibVLC
            ArrayList<String> options = new ArrayList<>();
            options.add("--aout=opensles");
            options.add("--audio-time-stretch"); // time stretching
            options.add("-vvv"); // verbosity
            //options.add("--network-caching=" + MEDIA_BUFFER_MS);

            libvlc = new LibVLC(options);
            libvlc.setOnHardwareAccelerationError(this);

            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);

            Media m;
            if(media.contains("http")) {
                m = new Media(libvlc, Uri.parse(media));
            } else {
                m = new Media(libvlc,media);
            }
            //m.setHWDecoderEnabled(false,false);
            m.addOption(":network-caching=6000");
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(MediaService.this, "Error creating player...", Toast.LENGTH_SHORT).show();
            Log.e("STREAM", "Error creating player: " + e.getMessage());
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        libvlc.release();
        libvlc = null;
    }

    public static int safeLongToInt(long l) {
        int i = (int)l;
        if ((long)i != l) {
            throw new IllegalArgumentException(l + " cannot be cast to int without changing its value.");
        }
        return i;
    }

    public void setState(String state) {
        this.mState = state;
    }

    public boolean isLive() {
        return isLive;
    }

    private static class MyPlayerListener implements MediaPlayer.EventListener {
        private WeakReference<MediaService> mOwner;

        public MyPlayerListener(MediaService owner) {
            //mOwner = new WeakReference<MediaService>(owner);
            mOwner = new WeakReference<>(owner);
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            MediaService player = mOwner.get();

            switch(event.type) {
                case MediaPlayer.Event.EndReached:
                    player.setState(MediaService.MEDIA_STATE_IDLE);
                    player.releasePlayer();
                    break;
                case MediaPlayer.Event.Playing:
                case MediaPlayer.Event.Paused:
                case MediaPlayer.Event.Stopped:
                default:
                    break;
            }
        }
    }
}
