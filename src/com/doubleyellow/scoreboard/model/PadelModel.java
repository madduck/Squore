/*
 * Copyright (C) 2020  Iddo Hoeve
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

package com.doubleyellow.scoreboard.model;

public class PadelModel extends GSMModel {

    PadelModel() {
        super();

        // sometimes a 'gold point' (no advantage) is used for deciding a game, but not in official rules
        setGoldenPointToWinGame(false);
    }
    @Override public SportType getSport() {
        return SportType.Padel;
    }
}
