/*
 * Copyright (C) 2019  Iddo Hoeve
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

package com.doubleyellow.scoreboard.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.widget.RadioButton;

import com.doubleyellow.android.view.SelectObjectView;

import java.util.List;
/** Used by SelectDeviceDialog */
class SelectDeviceView extends SelectObjectView<BluetoothDevice> {
    public SelectDeviceView(Context context, List<BluetoothDevice> devices, BluetoothDevice fChecked) {
        super(context, devices, fChecked);
    }

    @Override protected RadioButtonDecorator<BluetoothDevice> getDefaultDecorator() {
        return new RadioButtonDecorator<BluetoothDevice>() {
            @Override public void decorateGuiItem(int iSeqNr_1Based, BluetoothDevice device, RadioButton rb) {
                String sName    = device.getName();
                String sAddress = device.getAddress();
                rb.setText(sName + " (" + sAddress + ")");

                try {
                    rb.setTag(device.getAddress());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }
}