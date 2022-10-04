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

package com.doubleyellow.scoreboard.model;

import android.util.Log;

import com.doubleyellow.scoreboard.Brand;

public class ModelFactory
{
    private static final String TAG = "SB." + ModelFactory.class.getSimpleName();

    public static Model getModel(SportType type) {
        Model m = null;
        switch (type) {
            case Squash:
                m = new SquashModel();
                break;
            case Racketlon:
                m = new RacketlonModel();
                break;
            case Tabletennis:
                m = new TabletennisModel();
                break;
            case Badminton:
                m = new BadmintonModel();
                break;
            case Racquetball:
                m = new RacquetballModel();
                break;
            case Padel:
                m = new PadelModel();
                break;
            case TennisPadel:
                m = new GSMModel();
                break;
        }
        return m;
    }

    private static SquashModel      mTmpSquash      = null;
    private static RacketlonModel   mTmpRacketlon   = null;
    private static TabletennisModel mTmpTabletennis = null;
    private static BadmintonModel   mTmpBadminton   = null;
    private static RacquetballModel mTmpRacketball  = null;
    private static GSMModel         mGSMModel       = null;
    private static PadelModel       mPadelModel     = null;
    /** Returns an existing model and calls 'clean' on it. Use with care. Only to be used to communicate json between activities */
    public static Model getTemp(String sPurpose) {
        Log.i(TAG, "Using singleton tmp model " + sPurpose);
        return getTemp(Brand.brand);
    }
    private static Model getTemp(Brand type) {
        Model mTmp = null;
        switch (type.getSportType()) {
            case Squash:
                if ( mTmpSquash == null ) {
                    mTmpSquash = new SquashModel(false);
                }
                mTmp = mTmpSquash;
                break;
            case Racketlon:
                if ( mTmpRacketlon == null ) {
                    mTmpRacketlon = new RacketlonModel();
                }
                mTmp = mTmpRacketlon;
                break;
            case Tabletennis:
                if ( mTmpTabletennis == null ) {
                    mTmpTabletennis = new TabletennisModel();
                }
                mTmp = mTmpTabletennis;
                break;
            case Badminton:
                if ( mTmpBadminton == null ) {
                    mTmpBadminton = new BadmintonModel();
                }
                mTmp = mTmpBadminton;
                break;
            case Racquetball:
                if ( mTmpRacketball == null ) {
                    mTmpRacketball = new RacquetballModel();
                }
                mTmp = mTmpRacketball;
                break;
            case TennisPadel:
                if ( mGSMModel == null ) {
                    mGSMModel = new GSMModel();
                }
                mTmp = mGSMModel;
                break;
            case Padel:
                if ( mPadelModel == null ) {
                    mPadelModel = new PadelModel();
                }
                mTmp = mPadelModel;
                break;
        }
        mTmp.clear();
        return mTmp;
    }
}
