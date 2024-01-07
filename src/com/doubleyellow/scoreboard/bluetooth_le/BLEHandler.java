/*
 * Copyright (C) 2023  Iddo Hoeve
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
package com.doubleyellow.scoreboard.bluetooth_le;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.doubleyellow.scoreboard.bluetooth.BTMessage;
import com.doubleyellow.scoreboard.bluetooth.MessageSource;
import com.doubleyellow.scoreboard.main.ScoreBoard;

/**
 * Handler specific for BluetoothLE messages.
 */
public class BLEHandler extends Handler
{
    private static final String TAG = "SB." + BLEHandler.class.getSimpleName();

    private ScoreBoard sb = null;
    public BLEHandler(ScoreBoard scoreBoard) {
        super(Looper.getMainLooper());
        sb = scoreBoard;
    }

    @Override public void handleMessage(Message msg) {
        BTMessage btMessage = BTMessage.values()[msg.what];
        Log.d(TAG, "msg.what : " + msg.what + " : " + btMessage);
        String sMsg = String.valueOf(msg.obj);
        Log.d(TAG, "msg.obj  : " + msg.obj);
        //Log.d(TAG, "msg.arg1 : " + msg.arg1);
        //Log.d(TAG, "msg.arg2 : " + msg.arg2);
        if ( btMessage.equals(BTMessage.READ) ) {
            try {
                sb.interpretReceivedMessage(sMsg, MessageSource.BluetoothLE);
            } catch (Exception e) {
                e.printStackTrace();
                Log.w(TAG, "Message could not be understood :" + sMsg);
            }
        } else if ( btMessage.equals(BTMessage.STATE_CHANGE) ) {
            BLEState btState = BLEState.values()[msg.arg1];
            int iNrOfDevices = msg.arg2;
            Log.d(TAG, "new state  : " + btState);
            Log.d(TAG, "iNrOfDevices: " + iNrOfDevices);
            switch (btState) {
                case CONNECTED_DiscoveringServices:
                    break;
                case CONNECTED_TO_1_of_2:
                    sb.setBluetoothBLEIconVisibility(View.VISIBLE, 1);
                    break;
                case CONNECTED_ALL:
                    sb.setBluetoothBLEIconVisibility(View.VISIBLE, iNrOfDevices);
                    break;
                case DISCONNECTED:
                    sb.setBluetoothBLEIconVisibility(View.INVISIBLE, -1);
                    break;
                case CONNECTING:
                    sb.setBluetoothBLEIconVisibility(View.VISIBLE, iNrOfDevices);
                    break;
                case DISCONNECTED_Gatt:
                    // if one of the two devices
                    sb.setBluetoothBLEIconVisibility(View.VISIBLE, iNrOfDevices);
                    break;
                default:
                    Toast.makeText(sb, sMsg, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
/*
    private boolean m_bHandlingBluetoothMessageInProgress = false;
    boolean isHandlingMessage() {
        return m_bHandlingBluetoothMessageInProgress;
    }
}
