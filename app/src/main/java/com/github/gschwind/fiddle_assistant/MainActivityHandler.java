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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

public class MainActivityHandler extends Handler {
    MainActivity self;

    MainActivityHandler(Looper loop, MainActivity self) {
        super(loop);
        this.self = self;
    }

    @Override
    public void handleMessage(Message inputMessage) {

        switch(inputMessage.what) {
            case 2:
                self.updateView((Pair<Double, Double>)inputMessage.obj);
                break;
            default:
                super.handleMessage(inputMessage);
        }

    }

}
