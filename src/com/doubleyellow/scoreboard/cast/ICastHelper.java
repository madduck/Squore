/*
 * Copyright (C) 2017  Iddo Hoeve
 *
 * Squore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.doubleyellow.scoreboard.cast;

import android.app.Activity;
import android.content.Context;
import android.view.Menu;

import com.doubleyellow.scoreboard.model.Model;
import com.doubleyellow.scoreboard.prefs.ColorPrefs;
import com.doubleyellow.scoreboard.timer.TimerView;

import java.util.Map;

public interface ICastHelper {
    /** Always called by the app */
    void initCasting(Activity activity);

    void initCastMenu(Activity activity, Menu menu/*, int iResIdMenuItem*/);

    void onActivityStart_Cast();
    void onActivityStop_Cast();
    void onActivityPause_Cast();
    void onActivityResume_Cast();

    boolean isCasting();


    void setModelForCast(Model matchModel);

    TimerView getTimerView();

    void castColors(Map<ColorPrefs.ColorTarget, Integer> mColors);

    void castDurationChronos();

    void castGamesWonAppearance();

    // Functions
    String Avatar_update          =         "Avatar.update";
    String Call_showDecision      =           "Call.showDecision";
    String CountDownTimer_cancel  = "CountDownTimer.cancel";
    String CountDownTimer_show    = "CountDownTimer.show";
    String Country_update         =        "Country.update";
    String GameGraph_Show         =      "GameGraph.show";
    String GameScores_display     =     "GameScores.display";
    String GameScores_update      =     "GameScores.update";
    String LogoSponsor_setSponsor =    "LogoSponsor.setSponsor";
    String LogoSponsor_setLogo    =    "LogoSponsor.setLogo";
    String Server_update          =         "Server.update";
    String GameChrono_update      =     "GameChrono.update";
    String SetChrono_update       =      "SetChrono.update";
    String MatchChrono_update     =    "MatchChrono.update";
    String GameChrono_hide        =     "GameChrono.hide";
    String SetChrono_hide         =      "SetChrono.hide";
    String MatchChrono_hide       =    "MatchChrono.hide";
    String Racketlon_updateDiscipline =    "Racketlon.updateDiscipline";

    // properties
    String Property_BGColor       = "background-color";
    String Property_Color         = "color";
    String Property_BGImage       = "background-image";
    String Property_BorderColor   = "border-color";
    String Property_Text          = "text";
    String Property_Display       = "display";
}
