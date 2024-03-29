/*
 * Copyright (C) 2024  Iddo Hoeve
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.doubleyellow.scoreboard.R;
import com.doubleyellow.scoreboard.bluetooth.BTMessage;
import com.doubleyellow.scoreboard.model.Player;
import com.doubleyellow.scoreboard.prefs.PreferenceValues;
import com.doubleyellow.util.ListUtil;
import com.doubleyellow.util.MapUtil;
import com.doubleyellow.util.Params;
import com.doubleyellow.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and interprets message from BLE device.
 * TODO: 2 modes
 * - amateur: every correct message is a change in score
 * - pro    : a correct message from one BLE is request in change of score for requester, a confirmation message from different BLE devices is needed to actually change the score
 */
public class BLEReceiverManager
{
    private final static String TAG = "SB.BLEReceiverManager";

    private final String CCCD_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"; // Client Characteristic Configuration

    private final String BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb";
    private final String BATTERY_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb";
    private String sReadingBatterLevelOf = null;

    private final BluetoothLeScanner bluetoothLeScanner;
    private       BLEHandler         mHandler;
    /** one or two devices with time we 'saw' them */
    private final Map<String, Long>  mDevicesUsed       = new HashMap<>();
    /** just a map to reduce logging */
    private final Map<String, Long>  mDevicesSeen       = new HashMap<>();

    private final Context context;

    /** the one or two devices we want to connect to */
    private       List<String> saDeviceAddresses         = new ArrayList<>();
    private final JSONObject   mServicesAndCharacteristicsConfig;

    private final Map<String, BluetoothGatt> mDevice2gatt = new HashMap<>();
    private final Map<Player, BluetoothGatt> mPlayer2gatt = new HashMap<>();

    public BLEReceiverManager( Context context, BluetoothAdapter bluetoothAdapter
                             , String sBluetoothLEDeviceA, String sBluetoothLEDeviceB
                             , JSONObject mServicesAndCharacteristicsConfig)
    {
        this.context              = context;
        this.bluetoothLeScanner   = bluetoothAdapter.getBluetoothLeScanner();

        this.saDeviceAddresses.add(sBluetoothLEDeviceA);
        this.saDeviceAddresses.add(sBluetoothLEDeviceB);
        saDeviceAddresses = ListUtil.removeDuplicates(saDeviceAddresses);
        ListUtil.removeEmpty(this.saDeviceAddresses);

        this.mServicesAndCharacteristicsConfig = mServicesAndCharacteristicsConfig;
    }

    /*
     * @param handler A Handler to send messages back to the UI Activity
     */
    public void setHandler(BLEHandler handler) {
        this.mHandler = handler;
    }

    public void setState(BLEState state, String sAddress, int iDeviceCount) {
        if ( mHandler == null ) { return; }
        Message message = mHandler.obtainMessage(BTMessage.STATE_CHANGE.ordinal(), state.ordinal(), iDeviceCount, sAddress);
        message.sendToTarget();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if ( result.isConnectable() == false ) {
                    return;
                }
            }
            ParcelUuid[] uuids = result.getDevice().getUuids();
            if ( uuids != null ) {
                for(int i=0; i < uuids.length; i++) {
                    Log.i(TAG, "uuids[" + i + "]:" + uuids[i].toString());
                }
            }

            // does not seem to give much more info
            ScanRecord scanRecord = result.getScanRecord();
            if ( scanRecord != null ) {
                SparseArray<byte[]> manufacturerSpecificData = scanRecord.getManufacturerSpecificData();
                if (manufacturerSpecificData != null ) {
                    for(int i=0; i < manufacturerSpecificData.size(); i++) {
                        byte[] bytes = manufacturerSpecificData.get(i);
                        if ( bytes == null ) { continue; }
                        String s = new String(bytes);
                        Log.i(TAG, "manufacturerSpecificData[" + i + "]:" + s);
                    }
                }
            }

            BluetoothDevice btDevice = result.getDevice();
            String devName = btDevice.getName();
            if (StringUtil.isEmpty(devName) ) {
                devName = btDevice.getAddress();
            }

            long lNow = System.currentTimeMillis();
            Long lSeen = mDevicesSeen.get(devName);
            if ( lSeen == null || lNow - lSeen > 20 * 1000L ) {
                Log.d(TAG, String.format("Scan is seeing %s ( address : %s)", devName, btDevice.getAddress()));
                mDevicesSeen.put(devName, lNow);
            }

            // connectGatt if it is matches one of the 2 device names/mac addresses
            for ( int i=0; i < saDeviceAddresses.size(); i++ ) {
                String sLookingFor = saDeviceAddresses.get(i);
                if( sLookingFor.equalsIgnoreCase(devName) || sLookingFor.equalsIgnoreCase(btDevice.getAddress()) ) {
                    if ( mDevicesUsed.containsKey(sLookingFor) ) { continue; }

                    mDevicesUsed.put(sLookingFor, System.currentTimeMillis());
                    Log.i(TAG, "Connecting GAT to device " + devName);
                    Player eachPlayerHasADevicePlayer = saDeviceAddresses.size() == 1 ? null : Player.values()[i];
                    btDevice.connectGatt(context,false, new MyBluetoothGattCallback(btDevice.getAddress(), eachPlayerHasADevicePlayer));

                    boolean bAllDevicesFound = MapUtil.size(mDevicesUsed) == saDeviceAddresses.size();
                    if ( bAllDevicesFound ) {
                        bluetoothLeScanner.stopScan(this);
                        Log.i(TAG, "Stopped scanning. All devices found = " + ListUtil.join(saDeviceAddresses, ","));
                        setState(BLEState.CONNECTED_ALL, btDevice.getAddress(), MapUtil.size(mDevicesUsed));
                    } else {
                        List<String> lOther = new ArrayList<>(saDeviceAddresses);
                        lOther.remove(sLookingFor);
                        Log.i(TAG, "Continue scanning for second device " + ListUtil.join(lOther, ","));
                        setState(BLEState.CONNECTED_TO_1_of_2, lOther.get(0), MapUtil.size(mDevicesUsed));
                    }
                }
            }
        }

        @Override public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(TAG, "onBatchScanResults : #"  + ListUtil.size(results));
        }
        @Override public void onScanFailed      (int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(TAG, "onScanFailed : "  + errorCode); // e.g. when bluetooth is turned off on BLE peripheral emulator?
        }
    };


    /** one instance created per BLE devices connected, so 1 or 2 */
    private class MyBluetoothGattCallback extends BluetoothGattCallback
    {
        private              int currentConnectionAttempt    = 1;
        private final static int MAXIMUM_CONNECTION_ATTEMPTS = 5;

        private int lastValueReceived = 0;

        private BluetoothGattCharacteristic characteristicPlayerType = null;
        private final String sDeviceAddress;
        private final Player player ;
        MyBluetoothGattCallback(String sAddress, Player player) {
            this.sDeviceAddress = sAddress;
            this.player         = player;
        }

        @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if ( status == BluetoothGatt.GATT_SUCCESS ) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED /* 2 */: {
                        Log.i(TAG, "Discovering services ... " + this.sDeviceAddress);
                        setState(BLEState.CONNECTED_DiscoveringServices, this.sDeviceAddress, 1);
                        gatt.discoverServices();
                        mDevice2gatt.put(this.sDeviceAddress, gatt); //this.gatt = gatt;
                        if ( player != null ) {
                            mPlayer2gatt.put(player, gatt);
                        }
                        break;
                    }
                    case BluetoothProfile.STATE_DISCONNECTED /* 0 */: {
                        Log.w(TAG, String.format("onConnectionStateChange NEW state %d (%s)", newState, this.sDeviceAddress));
                        gatt.close();
                        mDevice2gatt.remove(this.sDeviceAddress);
                        mDevicesUsed.remove(this.sDeviceAddress);
                        if ( player != null ) {
                            mPlayer2gatt.remove(player);
                        }
                        setState(BLEState.DISCONNECTED_Gatt, this.sDeviceAddress, MapUtil.size(mDevicesUsed));
                        if ( saDeviceAddresses.size() - MapUtil.size(mDevice2gatt) == 1 ) {
                            bluetoothLeScanner.startScan(null, BLEUtil.scanSettings, scanCallback);
                            Log.w(TAG, "Restart scanning. Not all devices found. Devices still found = " + ListUtil.join(mDevice2gatt.keySet(), ","));
                        }
                        break;
                    }
                    default: {
                        Log.w(TAG, String.format("onConnectionStateChange new state %d (%s)", newState, this.sDeviceAddress));
                    }
                }
            } else {
                Log.w(TAG, String.format("onConnectionStateChange status :%d (%s)", status, this.sDeviceAddress)); // status = 8 if e.g. device turned off
                mDevice2gatt.remove(this.sDeviceAddress);
                if ( player != null ) {
                    mPlayer2gatt.remove(player);
                }
                mDevicesUsed.remove(this.sDeviceAddress);
                mDevicesSeen.remove(this.sDeviceAddress);
                gatt.close();
                currentConnectionAttempt+=1;
                Log.i(TAG, String.format("Attempting to connect %d/%d", currentConnectionAttempt, MAXIMUM_CONNECTION_ATTEMPTS));
                setState(BLEState.DISCONNECTED_Gatt, this.sDeviceAddress, MapUtil.size(mDevicesUsed));
                if ( currentConnectionAttempt <= MAXIMUM_CONNECTION_ATTEMPTS ) {
                    startReceiving();
                } else {
                    Log.w(TAG, "Could not connect to ble device ... " + this.sDeviceAddress);
                    //setState(BLEState.NONE, this.sDeviceAddress); // TODO: required?
                }
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BLEUtil.printGattTable(gatt);
            if ( false && sDeviceAddress.toLowerCase().startsWith("iho") == false ) {
                // for BLE emulator as peripheral this does not trigger onMtuChanged
                boolean bReq = gatt.requestMtu(517); // could remain default of 20
                Log.d(TAG, "requestMtu : " + bReq);
            } else {
                enableNotification(gatt);
            }
        }
        @Override public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            enableNotification(gatt);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //int    iValue  = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
            //String sValue  = characteristic.getStringValue(0);
            byte[] baValue = characteristic.getValue();
            //Integer    iValue  = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0); // returns null
            translateToBTMessageAndSendToMain(characteristic, baValue);
        }
        @Override public void onCharacteristicChanged (@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            //super.onCharacteristicChanged(gatt, characteristic, value);
            translateToBTMessageAndSendToMain(characteristic, value);
        }
        void showInvalidActiveConfig() {
            Message msg = mHandler.obtainMessage(BTMessage.INFO.ordinal(), R.string.ble_active_config_not_suitable_for_x_devices, saDeviceAddresses.size());
            mHandler.sendMessage(msg);
        }

        void translateToBTMessageAndSendToMain(BluetoothGattCharacteristic characteristic, byte[] value) {
            if ( PreferenceValues.currentDateIsTestDate() ) {
                Log.d(TAG, "value[0] : " + value[0]);
                Log.d(TAG, "sValue   : " + new String(value));
                for (int i = 0; i < value.length; i++) {
                    Log.d(TAG, "b[" + i + "] : " + value[i]);
                }
            }
            //Log.d(TAG, String.format("onCharacteristicChanged B: characteristic : %s, writetype : %d, instance_id : %d", characteristic.getUuid(), characteristic.getWriteType(), characteristic.getInstanceId())); // e.g. write type 2 for indicate, no notify also results in '2' ?

            try {
                JSONObject joServiceCfg = mServicesAndCharacteristicsConfig.optJSONObject(characteristic.getService().getUuid().toString().toLowerCase());
                if ( joServiceCfg != null ) {
                    JSONArray  jaCharacteristicCfg = joServiceCfg.optJSONArray(characteristic.getUuid().toString().toLowerCase());
                    if ( jaCharacteristicCfg != null ) {
                        JSONObject joCharacteristicCfg = jaCharacteristicCfg.optJSONObject(saDeviceAddresses.size() - 1);
                        if ( joCharacteristicCfg == null ) {
                            showInvalidActiveConfig();
                            return;
                        }
                        JSONArray saMessageFormat = joCharacteristicCfg.optJSONArray(BLEUtil.Keys.TranslateToBTMessage.toString());
                        if ( saMessageFormat == null || saMessageFormat.length() == 0 ) {
                            showInvalidActiveConfig();
                            return;
                        }
                        final int iValueIn = value[0];
                        int iValueToHandle = iValueIn;
                        int iHandleOnReleaseValue = joCharacteristicCfg.optInt(BLEUtil.Keys.HandleOnReleaseValue.toString(), -1);
                        final boolean bHandleOnRelease = iHandleOnReleaseValue >= 0;
                        if ( bHandleOnRelease ) {
                            // only act on 'buttons released' if a value for release is specified
                            if ( ( this.lastValueReceived != iHandleOnReleaseValue)
                              && (iValueIn               == iHandleOnReleaseValue)
                            ) {
                                iValueToHandle = this.lastValueReceived;
                            } else if ( iValueIn > lastValueReceived ) {
                                // assuming value to actually handle 'on release' increases if multiple buttons are pressed
                                lastValueReceived = iValueIn;
                                iValueToHandle = -1;
                            }
                        }

                        if ( (iValueToHandle >= 0) && (iValueToHandle < saMessageFormat.length() ) ) {
                            String sMessageFormat = saMessageFormat.getString(iValueToHandle);
                            if ( sMessageFormat == null || sMessageFormat.startsWith("-") ) {
                                //Log.d(TAG, "Ignoring message " + sMessageFormat + " " + iValue);
                            } else {
                                sMessageFormat = sMessageFormat.replaceAll("#.*", "").trim();
                                String sMessage = String.format(sMessageFormat, (this.player==null?"":player), value[0]);
                                Message message = mHandler.obtainMessage(BTMessage.READ.ordinal(), sMessage );
                                message.sendToTarget();
                            }
                            if ( bHandleOnRelease ) {
                                this.lastValueReceived = 0;
                            }
                        } else {
                            //Log.d(TAG, "Ignoring value " + iValue);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override public void onDescriptorRead        (         BluetoothGatt gatt,          BluetoothGattDescriptor     descriptor    , int status)                        { super.onDescriptorRead     (gatt, descriptor, status);            }
        @Override public void onDescriptorRead        (@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor     descriptor    , int status, @NonNull byte[] value) { super.onDescriptorRead     (gatt, descriptor, status, value);     }
        @Override public void onPhyUpdate             (         BluetoothGatt gatt, int txPhy, int rxPhy, int status)                                                       { super.onPhyUpdate          (gatt, txPhy, rxPhy, status);          }
        @Override public void onPhyRead               (         BluetoothGatt gatt, int txPhy, int rxPhy, int status)                                                       { super.onPhyRead            (gatt, txPhy, rxPhy, status);          }
        @Override public void onCharacteristicRead    (         BluetoothGatt gatt,          BluetoothGattCharacteristic characteristic, int status)                        {
            //super.onCharacteristicRead (gatt, characteristic, status);
            byte[] value = characteristic.getValue();
            if ( value.length != 0 ) {
                Log.d(TAG, "onCharacteristicRead A value " + value[0]);
            }
            if ( characteristic.getUuid().toString().equals(BATTERY_CHARACTERISTIC_UUID) ) {
                Integer intValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                Message msg = mHandler.obtainMessage(BTMessage.INFO.ordinal(), R.string.ble_battery_level, intValue, sReadingBatterLevelOf);
                mHandler.sendMessage(msg);
            } else {
                writePlayerInfo(gatt, characteristic, value);
            }
        }
        @Override public void onCharacteristicRead    (@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            //super.onCharacteristicRead (gatt, characteristic, value, status); // gives error?!
            Log.d(TAG, "onCharacteristicRead B " + gatt.getDevice().getAddress() + " - " + characteristic.getUuid() + " : " + status + " value :" + value[0]);
            writePlayerInfo(gatt, characteristic, value);
        }
        @Override public void onCharacteristicWrite   (         BluetoothGatt gatt,          BluetoothGattCharacteristic characteristic, int status)                        {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite " + gatt.getDevice().getAddress() + " - " + characteristic.getUuid() + " : " + status);
        }
        @Override public void onDescriptorWrite       (         BluetoothGatt gatt,          BluetoothGattDescriptor     descriptor    , int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite " + gatt.getDevice().getAddress() + " - " + descriptor.getUuid() + " : " + status);

            if ( false ) {
                byte[] baValue = descriptor.getValue();
                for ( int i = 0; i < baValue.length; i++ ) {
                    Log.d(TAG, "b[" + i + "] : " + baValue[i]);
                }
            }
            if ( this.characteristicPlayerType != null && descriptor.getCharacteristic().equals(this.characteristicPlayerType) == false ) {
                // with actual device this needs to be done AFTER updating characteristics of 'scoring'
                readPlayerInfo(gatt, characteristicPlayerType);
            }
        }
        @Override public void onReliableWriteCompleted(         BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.i(TAG, "onReliableWriteCompleted " + gatt.getDevice().getAddress());
        }
        @Override public void onReadRemoteRssi        (         BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.i(TAG, "onReadRemoteRssi " + gatt.getDevice().getAddress());
        }
        @Override public void onServiceChanged        (@NonNull BluetoothGatt gatt) {
            super.onServiceChanged(gatt);
            Log.i(TAG, "onServiceChanged " + gatt.getDevice().getAddress());
        }

        private void enableNotification(BluetoothGatt gatt)
        {
            Map<String, List<String>> mServices2Characteristics = BLEUtil.getServiceUUID2CharUUID(mServicesAndCharacteristicsConfig);

            for(String sServiceUUID: mServices2Characteristics.keySet() ) {
                List<String> lCharacteristicUUID = mServices2Characteristics.get(sServiceUUID);
                if ( lCharacteristicUUID == null ) {
                    Log.w(TAG, String.format("No found characteristics for %s", sServiceUUID));
                    continue;
                }
                for (String sCharacteristicUUID : lCharacteristicUUID) {
                    BluetoothGattCharacteristic characteristic = findCharacteristics(gatt, sServiceUUID, sCharacteristicUUID);
                    if ( characteristic == null ) {
                        String errorMessage = String.format("Could not find service %s with characteristic %s", sServiceUUID, sCharacteristicUUID);
                        Log.w(TAG, errorMessage);
                    } else {
                        int properties = characteristic.getProperties();
                        BluetoothGattDescriptor cccdDescriptor = characteristic.getDescriptor(UUID.fromString(CCCD_DESCRIPTOR_UUID)); // NOT REQUIRED to receive INDICATE ?! unclear
                        if ( cccdDescriptor == null ) {
                            Log.w(TAG, String.format("cccdDescriptor=null for %s", characteristic.getUuid()));
                        }

                        if ( (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 ) {
                            if ( gatt.setCharacteristicNotification(characteristic, true) ) {
                                if ( cccdDescriptor != null ) {
                                    Log.i(TAG, "Writing value PROPERTY_INDICATE to descriptor");
                                    cccdDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); // 2,0
                                    gatt.writeDescriptor(cccdDescriptor); // triggers onDescriptorWrite()
                                }
                            }
                        } else {
                            if ( (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ) {
                                if ( gatt.setCharacteristicNotification(characteristic, true) ) {
                                    if ( cccdDescriptor != null ) {
                                        Log.i(TAG, "Writing value PROPERTY_NOTIFY to descriptor");
                                        cccdDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); // 2,0
                                        gatt.writeDescriptor(cccdDescriptor); // triggers onDescriptorWrite()
                                    }
                                }
                            } else {
                                characteristicPlayerType = characteristic; // to trigger readPlayerInfo() later
                                //readPlayerInfo(gatt, characteristic);
                            }
                        }
                    }
                }
            }
        }

        /** to trigger writePlayerInfo if incorrect */
        private void readPlayerInfo(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if ( gatt.readCharacteristic(characteristic) ) {
                // actual value must be 'catched' in callback: onCharacteristicRead()
            } else {
                Log.w(TAG, String.format("Reading value of characteristic %s failed", characteristic.getUuid()));
            }
        }
        private void writePlayerInfo(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] currentValue) {
            String sWriteIndicator = null;
            UUID uuid = characteristic.getUuid();
            try {
                String sServiceUuid = characteristic.getService().getUuid().toString();
                sWriteIndicator = mServicesAndCharacteristicsConfig.optJSONObject(sServiceUuid).optString(uuid.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            byte[] value = new byte[1];
            if ( player != null ) {
                value[0] = (byte) player.ordinal();
            } else {
                value[0] = (byte) Player.values().length;
            }
            if ( currentValue != null && currentValue.length > 0 && currentValue[0] == value[0] ) {
                Log.i(TAG, String.format("No write required %s. Value already OK : %d", sWriteIndicator, value[0]));
                return;
            }

            if ( (sWriteIndicator == null) || (sWriteIndicator.startsWith("WRITE") == false) ) {
                Log.w(TAG, String.format("Json config does not specify WRITE for e.g. player type for %s", uuid));
                return;
            }
            int properties = characteristic.getProperties();
            int iSupportsWrite = properties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE);
            if ( iSupportsWrite != 0 ) {
                int mWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                if ( (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ) {
                    mWriteType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                }
                if ( (properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0 ) {
                    mWriteType = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
                }
                characteristic.setValue(value);
                characteristic.setWriteType(mWriteType);
                if ( gatt.writeCharacteristic(characteristic) ) {
                    Log.i(TAG, String.format("writing value %d to characteristic %s", value[0], uuid));
                } else {
                    Log.e(TAG, String.format("ERROR: writeCharacteristic failed for characteristic: %s", uuid));
                }
            }
        }
    }

    public boolean writeToBLE(Player p) {
        BluetoothGatt gatt = mPlayer2gatt.get(p);
        if ( gatt == null ) { return false; }

        // TODO: write some indication to wristband of other player let them know they need to
        // - confirm / cancel score entered by opponent ?
        // - notify them score was confirmed / canceled ?

        return true;
    }

    public String readBatteryLevel() {
        for ( String sDevice: mDevice2gatt.keySet() ) {
            BluetoothGatt gatt = mDevice2gatt.get(sDevice);

            BluetoothGattCharacteristic batteryCharacteristics = findCharacteristics(gatt, BATTERY_SERVICE_UUID, BATTERY_CHARACTERISTIC_UUID);
            if ( batteryCharacteristics == null ) {
                continue;
            }
            if ( mDevice2gatt.size() == 2 ) {
                if ( sReadingBatterLevelOf != null && sReadingBatterLevelOf.equals(sDevice) ) {
                    continue;
                }
            }
            sReadingBatterLevelOf = sDevice;
            if ( gatt.readCharacteristic(batteryCharacteristics) ) {
                // actual value must be 'catched' in callback: onCharacteristicRead()
                return sReadingBatterLevelOf;
            }
        }
        return null;
    }

    public void startReceiving() {
        //mDevicesUsed.clear();
        bluetoothLeScanner.startScan(null, BLEUtil.scanSettings, scanCallback);
        setState(BLEState.CONNECTING, ListUtil.join(saDeviceAddresses, ","), MapUtil.size(mDevicesUsed));
    }

/*
    public void reconnect() {
        if ( MapUtil.isEmpty(mDevice2gatt) ) { return; }
        for(BluetoothGatt gatt : mDevice2gatt.values() ) {
            gatt.connect();
        }
    }

    public void disconnect() {
        if (MapUtil.isEmpty(mDevice2gatt)) { return; }
        for(BluetoothGatt gatt : mDevice2gatt.values() ) {
            gatt.disconnect();
        }
    }
*/

    public void closeConnection() {
        this.bluetoothLeScanner.stopScan(scanCallback);

        for(String sDeviceAddress : mDevice2gatt.keySet() ) {
            BluetoothGatt gatt = mDevice2gatt.get(sDeviceAddress);
            Map<String, List<String>> mServices2Characteristics = BLEUtil.getServiceUUID2CharUUID(mServicesAndCharacteristicsConfig);
            for(String sServiceUUID: mServices2Characteristics.keySet() ) {
                List<String> lCharacteristicUUID = mServices2Characteristics.get(sServiceUUID);
                if ( lCharacteristicUUID == null ) { continue; }

                for (String sCharacteristicUUID : lCharacteristicUUID) {
                    BluetoothGattCharacteristic characteristic = findCharacteristics(gatt, sServiceUUID, sCharacteristicUUID);
                    if ( characteristic != null ) {
                        disconnectCharacteristic(gatt, characteristic);
                        mDevicesUsed.remove(sDeviceAddress);

                        setState(BLEState.DISCONNECTED_Gatt, sDeviceAddress, MapUtil.size(mDevicesUsed));
                    }
                }
            }
            gatt.close();
        }
        mDevicesUsed.clear();
        mDevicesSeen.clear();
        setState(BLEState.DISCONNECTED, null, 0);
    }

    private void disconnectCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic ){
        UUID cccdUuid = UUID.fromString(CCCD_DESCRIPTOR_UUID);
        if ( gatt.setCharacteristicNotification(characteristic,false) ) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
            if ( descriptor != null ) {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
    }

    private BluetoothGattCharacteristic findCharacteristics(BluetoothGatt gatt, String serviceUUID, String characteristicsUUID)
    {
        Params mCharsFoundLog = new Params();
        for ( BluetoothGattService service: gatt.getServices() ) {
            String tmpServiceUuid = service.getUuid().toString();
            mCharsFoundLog.increaseCounter(tmpServiceUuid);
            if ( tmpServiceUuid.equalsIgnoreCase(serviceUUID) ) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for ( BluetoothGattCharacteristic characteristic: characteristics ) {
                    String tmpCharacteristicUuid = characteristic.getUuid().toString();
                    mCharsFoundLog.increaseCounter(tmpServiceUuid + "__" + tmpCharacteristicUuid);
                    if ( tmpCharacteristicUuid.equalsIgnoreCase(characteristicsUUID) ) {
                        return characteristic;
                    }
                }
            }
        }
        // log if nothing appropriate was found
        Log.w(TAG, String.format("Nothing found for %s __ %s", serviceUUID, characteristicsUUID));
        Log.d(TAG, "We did find " + mCharsFoundLog);
        return null;
    }
}
