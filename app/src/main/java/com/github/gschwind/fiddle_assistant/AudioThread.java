/*

Copyright (2020) Benoit Gschwind <gschwind@gnu-log.net>

This file is part of fiddle-assistant.

fiddle-assistant is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

fiddle-assistant is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fiddle-assistant.  If not, see <https://www.gnu.org/licenses/>.

 */

package com.github.gschwind.fiddle_assistant;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

public class AudioThread implements Runnable {
    static int NOTE_SAMPLE_RATE = 30;

    private long opaqueNativeHandle; // store the pointer
    private int length_of_sample; // store the pointer

    private int next_analisys_freq_counter;
    private int rate;

    private MainActivityHandler handler;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static final String LOG_TAG = "AudioThread";

    private AudioRecord audioRecord;
    private volatile boolean isAudioRecording;

    public AudioThread(MainActivityHandler handler) {
        this.handler = handler;
        this.opaqueNativeHandle = 0;

    }

    private static Pair<Integer, Integer> getValidSampleRates() {
        int[] rates = new int[] {48000, 44100, 22050, 16000, 11025, 8000};

        for (int rate : rates) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                return new Pair<Integer, Integer>(rate, bufferSize);
            }
        }

        return new Pair<Integer, Integer>(-1, -1);
    }

    public void stop() {
        Log.d(LOG_TAG, "audioRecord.stop()");
        isAudioRecording = false;
    }

    @Override
    public void run() {

        int bufferLength = 0;
        int bufferSize;
        short[] audioData;
        int bufferReadResult;

        {
            Pair<Integer, Integer> r = getValidSampleRates();
            rate = r.first;
            bufferSize = r.second; // scale to shorts
        }

        if (rate < 0) {
            // TODO: print error message.
            return;
        }

        int err = initSampleRate(rate);
        if(err < 0) {
            Log.e(LOG_TAG, "failled to initSampleRate");
            return;
        }

        try {
            // store 2 seconds of record to avoid much move/copy buffer.
            int buff_size_in_shorts = rate*2;
            int buf_offset = 0;

            /* set audio recorder parameters, and start recording */
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[buff_size_in_shorts];

            audioRecord.startRecording();
            Log.d(LOG_TAG, "audioRecord.startRecording()");

            isAudioRecording = true;

            next_analisys_freq_counter = rate/NOTE_SAMPLE_RATE; //at less wait for this amount of data before next analisys

            int next_analisys = length_of_sample;

            /* ffmpeg_audio encoding loop */
            while (isAudioRecording) {

                // Read all remining data to flush buffer if the computation is too slow.
                do {

                    if ((buf_offset + 8192) >= audioData.length) {
                        System.arraycopy(audioData, buf_offset-length_of_sample, audioData, 0, length_of_sample);
                        buf_offset = length_of_sample;
                    }

                    bufferReadResult = audioRecord.read(audioData, buf_offset, 8192, AudioRecord.READ_NON_BLOCKING);

                    if (bufferReadResult < 0 ) {
                        System.out.printf("AudioThreadError %d%n", bufferReadResult);
                        return;
                    }

                    buf_offset += bufferReadResult;
                    next_analisys -= bufferReadResult;

                } while (bufferReadResult > 0);

                if (next_analisys > 0) {

                    if ((buf_offset + next_analisys) >= audioData.length) {
                        System.arraycopy(audioData, buf_offset - length_of_sample, audioData, 0, length_of_sample);
                        buf_offset = length_of_sample;
                    }

                    bufferReadResult = audioRecord.read(audioData, buf_offset, next_analisys);

                    if (bufferReadResult < 0) {
                        System.out.printf("AudioThreadError %d%n", bufferReadResult);
                        return;
                    }

                    buf_offset += bufferReadResult;
                    next_analisys -= bufferReadResult;

                }

                if (next_analisys <= 0) {
                    double freq = computeFreq(audioData, buf_offset - length_of_sample, length_of_sample);
                    double energy = sampleEnergy(audioData, buf_offset - length_of_sample, length_of_sample);
                    handler.sendMessage(Message.obtain(handler, 2, new Pair<>(new Double(freq), new Double(energy))));
                    next_analisys = next_analisys_freq_counter;
                }

            }

            Log.d(LOG_TAG, "Stopping audioRecord");

            /* encoding finish, release recorder */
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                    audioRecord.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                audioRecord = null;
            }

            // clear allocated C++ stuff
            dispose();

        } catch (Exception e) {
            Log.e(LOG_TAG, "get audio data failed:"+e.getMessage()+e.getCause()+e.toString());
        }

    }

    public void updateSampleFrequency(int f) {
        NOTE_SAMPLE_RATE = f;
        next_analisys_freq_counter = rate / NOTE_SAMPLE_RATE;
    }

    public native int initSampleRate(int sampleRate);
    public native float computeFreq(short[] arr, int offset, int length);
    public native float sampleEnergy(short[] arr, int offset, int length);
    public native void dispose();


}
