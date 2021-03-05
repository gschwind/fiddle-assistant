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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class ScaleNotesView extends View {

    static double sclamp(double a, double x, double b)
    {
        if (x < a)
            return 0.0;
        if (x > b)
            return 1.0;
        return (x-a)/(b-a);
    }

    int width;
    int height;

    Paint blackColor;
    Paint grayColor;
    Paint cursorColor;

    float density;

    float current_note;

    // Store the previous note we show in case we get NaN on the next iteration
    float previous_note_shown;

    // To Avoid noise in the input signal we check if we have two times similar note to avoid scale
    // jump
    float previous_note;

    // The current scale position in diatonic notes.
    float scale_current_note;

    // The new taget note when note has changed
    float scale_goto_note;

    static String[] note_names_english = {"A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#"};
    static String[] note_names_french = {"La", "La#", "Si", "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#"};

    String[] note_names;

    public ScaleNotesView(Context context, AttributeSet attrs) {
        super(context, attrs);

        density = getResources().getDisplayMetrics().density;

        blackColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackColor.setARGB(255, 0, 0, 0);
        blackColor.setTextSize(20.0f*density);

        grayColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        grayColor.setARGB(255, 128, 128, 128);

        cursorColor = new Paint(Paint.ANTI_ALIAS_FLAG);

        scale_current_note = 48.0f;
        scale_goto_note = 48.0f;

        note_names = note_names_english;

    }


    @Override
    protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);

        float scale_half_tone_width = width/2.5f;

        float scale_height = height-blackColor.getTextSize();
        float baseline = scale_height/2f + blackColor.getTextSize();
        float large_bar_height = 0.7f*scale_height*0.5f;
        float thin_bar_height = 0.4f*scale_height*0.5f;
        float circle_radius = 0.3f*scale_height*0.5f;

        int nearest = Math.round(scale_current_note);
        for (int k = -2; k < 3; ++k) {
            int i = nearest+k;
            int o = (i+3) / 12; //octave
            int n = i % 12;     //note
            String text = String.format("%s %d", note_names[n], o);
            float textWidth = blackColor.measureText(text);
            canvas.drawText(text, width/2.0f+(i- scale_current_note)*scale_half_tone_width-textWidth/2.0f, blackColor.getTextSize(), blackColor);
            canvas.drawLine((width/2+(i- scale_current_note)*scale_half_tone_width), baseline-large_bar_height, width/2+(i- scale_current_note)*scale_half_tone_width, baseline+large_bar_height, grayColor);
            for (int l = 1; l < 10; l += 1) {
                canvas.drawLine(width/2.0f+scale_half_tone_width*l/10.0f+(i- scale_current_note)*scale_half_tone_width, baseline-thin_bar_height, (width/2+scale_half_tone_width*l*0.1f+(i- scale_current_note)*scale_half_tone_width), baseline+thin_bar_height, grayColor);
            }
        }

        if (!Float.valueOf(current_note).isNaN() && (Math.abs(current_note- scale_current_note) < 0.5)) {
            previous_note_shown = current_note;
            double pos = Math.min(0.5, Math.max(-0.5, previous_note_shown - scale_current_note));
            double alpha = sclamp(0.01, Math.abs(pos), 0.08);
            cursorColor.setARGB(255, (int)(255.0*Math.min(1.0, 2*alpha)), (int)(255.0*Math.min(1.0, 2*(1.0 - alpha))), 0);
            canvas.drawCircle((float)(width/2+pos*scale_half_tone_width), baseline, circle_radius, blackColor);
            canvas.drawCircle((float)(width/2+pos*scale_half_tone_width), baseline, circle_radius-2, cursorColor);
        } else if ((Math.abs(previous_note_shown - scale_current_note) < 0.5)) {
            double pos = Math.min(0.5, Math.max(-0.5, previous_note_shown - scale_current_note));
            double alpha = sclamp(0.01, Math.abs(pos), 0.08);
            cursorColor.setARGB(255, (int)(255.0*Math.min(1.0, 2*alpha)), (int)(255.0*Math.min(1.0, 2*(1.0 - alpha))), 0);
            canvas.drawCircle((float)(width/2+pos*scale_half_tone_width), baseline, circle_radius, blackColor);
            canvas.drawCircle((float)(width/2+pos*scale_half_tone_width), baseline, circle_radius-2, cursorColor);
        }

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = w;
        height = h;

        invalidate();
        requestLayout();

    }

    public void updateCurrentNote(double new_note) {

        if (!Double.valueOf(new_note).isNaN() && !Float.valueOf(current_note).isNaN()) {
            if (Math.abs(new_note-current_note)<4.0) {
                current_note += (new_note-current_note)*0.5;
            } else {
                current_note = (float) new_note;
            }
        } else {
            current_note = (float) new_note;
        }

        scale_current_note = scale_goto_note;

        if (!Float.valueOf(current_note).isNaN() && current_note > 12.0) {
            if (Math.abs(previous_note - current_note) < 0.5)
                scale_goto_note = Math.round(current_note);
            previous_note = current_note;
        }

        invalidate();
    }

    public void updateNoteNames(String v) {
        if (v.equals("english")) {
            note_names = note_names_english;
        } else if (v.equals("french")) {
            note_names = note_names_french;
        } else {
            note_names = note_names_english;
        }

        invalidate();
    }


}
