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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    AudioThread audioThread = null;

    MainActivityHandler handler;

    SlidingNotesView slidingNotesView;
    ScaleNotesView scaleNotesView;

    TextView freqView;
    TextView energyView;

    float base_frequency;

    private double frequency_to_diatonic_note(double frequency)
    {
        return 12.0 * Math.log(frequency / base_frequency) / Math.log(2.0) + 60.0;
    }

    private double diatonic_note_to_frequency(double diatonic_note)
    {
        return base_frequency * Math.pow(2.0, (diatonic_note - 60.0) / 12.0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Prevent the screen to go turn off when the app is in foreground
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        slidingNotesView = findViewById(R.id.slidingNotesView);
        scaleNotesView = findViewById(R.id.scaleNotesView);

        freqView = findViewById(R.id.textView1);
        energyView = findViewById(R.id.textView3);

        handler = new MainActivityHandler(Looper.getMainLooper(), this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        double d = Double.valueOf(sharedPreferences.getString("min_volume_sensitivity", "5"));
        audioThread = new AudioThread(handler, d);

        slidingNotesView.updateNoteNames(sharedPreferences.getString("music_notation", "english"));
        scaleNotesView.updateNoteNames(sharedPreferences.getString("music_notation", "english"));

        base_frequency = Float.valueOf(sharedPreferences.getString("base_frequency", "440"));
        if (base_frequency <= 55.0f)
            base_frequency = 440.0f;

        int i = Integer.valueOf(sharedPreferences.getString("sample_frequency", "30"));
        audioThread.updateSampleFrequency(i);

        freqView.setText(String.format("%.2f Hz", base_frequency));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MainActivity", "onStart");
        if (!permissionToRecordAccepted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            new Thread(audioThread, "AudioThread").start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MainActivity", "onStop");
        if (audioThread != null) {
            audioThread.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();


        // start the audio Thread.
        new Thread(audioThread, "AudioThread").start();

    }

    void updateView(Pair<Double,Double> v) {
        // Example of a call to a native method
//        freqView.setText(String.format("%.2f Hz", v.first));

        energyView.setText(String.format("%.0f Hz", v.second));

        double diatonic_note = frequency_to_diatonic_note(v.first);
        slidingNotesView.appendDouble(diatonic_note);
        scaleNotesView.updateCurrentNote(diatonic_note);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.activity_main_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("MainActivity", String.format("Changed key: %s", key));

        if (key.equals("music_notation")) {
            String v = sharedPreferences.getString("music_notation", "english");
            slidingNotesView.updateNoteNames(v);
            scaleNotesView.updateNoteNames(v);
        } else if (key.equals("base_frequency")) {
            base_frequency = Float.valueOf(sharedPreferences.getString("base_frequency", "440"));
            if (base_frequency <= 55.0f)
                base_frequency = 440.0f;
            freqView.setText(String.format("%.2f Hz", base_frequency));
        } else if (key.equals("sample_frequency")) {
            int i = Integer.valueOf(sharedPreferences.getString("sample_frequency", "30"));
            if (audioThread != null)
                audioThread.updateSampleFrequency(i);
        } else if (key.equals("min_volume_sensitivity")) {
            double d = Double.valueOf(sharedPreferences.getString("min_volume_sensitivity", "5"));
            if (audioThread != null)
                audioThread.setMinVolumeSensitivity(d);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }


}
