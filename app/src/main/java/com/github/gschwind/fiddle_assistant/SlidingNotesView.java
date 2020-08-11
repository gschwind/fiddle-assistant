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
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.io.Console;
import java.util.LinkedList;
import java.util.ListIterator;


public class SlidingNotesView extends View {
    static boolean BLACK_BACKGROUND = false;

    int POINT_HEIGHT;
    int POINT_BORDER_WIDTH;
    int LINE_SPACING;
    int LEFT_SPACING;

    LinkedList<Float> notes;

    Paint grayColor;
    Paint blackColor;
    Paint minorColor;
    Paint majorColor;
    Paint referColor;
    Paint notesColor;
    Paint highColor;

    Paint cursorColor;

    int width;
    int height;

    float density;

    int base_line;

    float max_count;
    float base_note;
    float goto_note;

    float previous_note;

    float last_valid_note;
    float goto_valid_note;

    static String[] note_names_english = {"A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#"};
    static String[] note_names_french = {"La", "La#", "Si", "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#"};

    String[] note_names = null;

    Paint[] lineNoteColor;


    public SlidingNotesView(Context context, AttributeSet attrs) {
        super(context, attrs);

        density = getResources().getDisplayMetrics().density;

        LINE_SPACING = (int)(density*15.0f)+1;
        POINT_HEIGHT = (int)(density*4.0f)+1;
        POINT_BORDER_WIDTH = (int)(density*1.0f)+1;

        notes = new LinkedList<Float>();

        grayColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        grayColor.setARGB(255, 128, 128, 128);
        grayColor.setTextSize(density*12.0f);

        blackColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackColor.setARGB(255, 0, 0, 0);
        blackColor.setTextSize(density*12.0f);

        minorColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        minorColor.setARGB(255, 224, 224, 224);

        majorColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        majorColor.setARGB(255, 190, 190, 190);
        majorColor.setTextSize(density*12.0f);

        notesColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        notesColor.setARGB(255, 0, 0, 255);

        referColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        referColor.setARGB(255, 128, 128, 128);
        referColor.setTextSize(density*12.0f);

        highColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        highColor.setARGB(255, 0, 0, 255);
        highColor.setTextSize(density*12.0f);
        highColor.setTypeface(Typeface.DEFAULT_BOLD);

        cursorColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorColor.setARGB(255, 255, 245, 200);
        cursorColor.setTextSize(density*12.0f);
        cursorColor.setTypeface(Typeface.DEFAULT_BOLD);


        lineNoteColor = new Paint[] {majorColor, minorColor, majorColor, referColor, minorColor,
                majorColor, minorColor, majorColor, majorColor, minorColor, majorColor, minorColor};

        base_note = 0.0f;
        goto_note = 0.0f;
        last_valid_note = 48f;

        notes.addFirst(Float.NaN);

        updateNoteNames("english");

    }

    private void clipNotesList(int count) {
        ListIterator<Float> iter = notes.listIterator();
        int i = 0;
        while (iter.hasNext()) {
            iter.next();
            if (i >= count)
                iter.remove();
            ++i;
        }
    }

    private void drawNotesBlackBackground(Canvas canvas) {
        int local_base_line = height - 6;

        int i = 0;
        ListIterator<Float> iter = notes.listIterator();
        while (iter.hasNext()) {
            Float f = iter.next();
            if (!f.isNaN()) {
                double y = (f - base_note) * LINE_SPACING;
                if (y > -2.0 * LINE_SPACING && y < height + 2 * LINE_SPACING) {
                    canvas.drawRect((i * density * 2 + LEFT_SPACING - POINT_HEIGHT), (int) Math.round(local_base_line - y - POINT_HEIGHT), (i * density * 2 + LEFT_SPACING + POINT_HEIGHT), (int) Math.round(local_base_line - y + POINT_HEIGHT), blackColor);
                }
            }
            ++i;
        }
    }

    @Override
    protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);

        if (BLACK_BACKGROUND) {
            canvas.drawRect(0, 0, width, height, blackColor);
        }

        int local_base_line = height - 6;

        base_note += 0.1*(goto_note-base_note);
        last_valid_note += 0.1*(goto_valid_note-last_valid_note);

        canvas.drawRect(0, (local_base_line-(last_valid_note-base_note-1.5f)*LINE_SPACING), width, (local_base_line-(last_valid_note-base_note+1.5f)*LINE_SPACING), cursorColor);

        for (int i = 0; i < 120; ++i) {
            float y = (i-base_note) * LINE_SPACING;

            if (y > -2.0f*LINE_SPACING && y < height+2.0f*LINE_SPACING) {
                canvas.drawRect(LEFT_SPACING, (local_base_line - y - 1), width, (local_base_line - y + 1), lineNoteColor[i % 12]);
                canvas.drawText(note_names[i % 12], density*3.0f, (local_base_line - y + grayColor.getTextSize()/2.0f), grayColor);
            }
        }

        int count = Math.min((width-LEFT_SPACING)/2, notes.size())+2;

        clipNotesList(count);

        if (!BLACK_BACKGROUND) {
            drawNotesBlackBackground(canvas);
        }

        int i = 0;
        ListIterator<Float> iter = notes.listIterator();
        while (iter.hasNext()) {
            Float f = iter.next();
            if (!f.isNaN()) {
//                double x = ((f+12.0*100-0.5 - (Math.floor((f+12.0*100+0.5)/12.0)*12.0))+0.5) * LINE_SPACING;
                double y = (f - base_note) * LINE_SPACING;
                if (y > -2.0f * LINE_SPACING && y < height + 2.0f * LINE_SPACING) {
                    double note = Math.round(f);
                    double pos = Math.min(0.5, Math.max(-0.5, f - note));
                    double alpha = Math.min(1.0, Math.abs(pos * 3.0));
                    notesColor.setARGB(255, (int) (255.0 * Math.min(1.0, 2 * alpha)), (int) (255.0 * Math.min(1.0, 2 * (1.0 - alpha))), 0);
                    canvas.drawRect((i * density * 2 + LEFT_SPACING - POINT_HEIGHT + POINT_BORDER_WIDTH), (int) Math.round(local_base_line - y - POINT_HEIGHT + POINT_BORDER_WIDTH), (i * density * 2 + LEFT_SPACING + POINT_HEIGHT - POINT_BORDER_WIDTH), (int) Math.round(local_base_line - y + POINT_HEIGHT - POINT_BORDER_WIDTH), notesColor);
                }
            }
            ++i;
        }

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = w;
        height = h;

        base_line = height/2;

        max_count = (float)Math.floor((height-12)/LINE_SPACING);

        invalidate();
        requestLayout();

    }

    public void updateNoteNames(String v) {
        if (v.equals("english")) {
            note_names = note_names_english;
        } else if (v.equals("french")) {
            note_names = note_names_french;
        } else {
            note_names = note_names_english;
        }

        // Compute max length of notes names.
        LEFT_SPACING = 0;
        for (int i = 0; i < note_names.length; ++i) {
            int m = 1+(int)grayColor.measureText(note_names[i]);
            if (LEFT_SPACING < m) {
                LEFT_SPACING = m;
            }
        }
        LEFT_SPACING += 4.0*density;

        invalidate();
    }

    public void appendDouble(double f) {

        float current_note = (float)f;

        if (!Float.valueOf(current_note).isNaN()) {
            if (Math.abs(previous_note - current_note) < 0.5 || true) {
                notes.addFirst(new Float(current_note));
                goto_valid_note = current_note;
            } else {
                notes.addFirst(Float.NaN);
            }
        } else {
            notes.addFirst(Float.NaN);
        }

        previous_note = current_note;


        if (current_note > base_note+max_count) {
            goto_note = base_note + 4;
        }

        if (current_note < base_note+2) {
            goto_note = Math.max(0.0f, base_note-4);
        }

        invalidate();
    }

}
