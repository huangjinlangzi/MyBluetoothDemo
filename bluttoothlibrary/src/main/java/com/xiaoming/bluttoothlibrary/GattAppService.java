/******************************************************************************
 *
 *  Copyright (C) 2012-2013 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
package com.xiaoming.bluttoothlibrary;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class GattAppService extends Service {

	/**
	 * Intents to indicate GATT state
	 */
	public static final String GATT_DEVICE_FOUND = "com.broadcom.gatt.device_found";
	public static final String GATT_CONNECTION_STATE = "com.broadcom.gatt.connection_state";
	public static final String GATT_SERVICES_REFRESHED = "com.broadcom.gatt.refreshed";
	public static final String GATT_CHARACTERISTIC_READ = "com.broadcom.gatt.read";

	/**
	 * Intent extras
	 */
	public static final String EXTRA_DEVICE = "DEVICE";
	public static final String EXTRA_RSSI = "RSSI";
	public static final String EXTRA_SOURCE = "SOURCE";
	public static final String EXTRA_ADDR = "ADDRESS";
	public static final String EXTRA_CONNECTED = "CONNECTED";
	public static final String EXTRA_STATUS = "STATUS";
	public static final String EXTRA_UUID = "UUID";
	public static final String EXTRA_VALUE = "VALUE";

	/**
	 * Source of device entries in the device list
	 */
	public static final int DEVICE_SOURCE_SCAN = 0;
	public static final int DEVICE_SOURCE_BONDED = 1;
	public static final int DEVICE_SOURCE_CONNECTED = 2;

	/**
	 * Descriptor used to enable/disable notifications/indications
	 */
	private static final UUID CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private final IBinder binder = new LocalBinder();
	private BluetoothManager mBluetoothManager = null;
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothGatt mBluetoothGatt = null;
	private String mDevice = null;
	private boolean mReconnect = false;

	private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			BluetoothDevice device = gatt.getDevice();
			Intent intent = new Intent(GATT_CONNECTION_STATE);
			intent.putExtra(EXTRA_ADDR, device.getAddress());
			intent.putExtra(EXTRA_CONNECTED, newState == BluetoothProfile.STATE_CONNECTED);
			intent.putExtra(EXTRA_STATUS, status);
			sendBroadcast(intent);

			if (newState == BluetoothProfile.STATE_CONNECTED && mBluetoothGatt != null) {
				sendDeviceFoundIntent(device, 255, DEVICE_SOURCE_CONNECTED);
				mBluetoothGatt.discoverServices();
			}

			if (newState == BluetoothProfile.STATE_DISCONNECTED && mBluetoothGatt != null) {
				if (getReconnect()) {
					mBluetoothGatt.connect();
				} else {
					mBluetoothGatt.disconnect();
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			BluetoothDevice device = gatt.getDevice();
			Intent intent = new Intent(GATT_SERVICES_REFRESHED);
			intent.putExtra(EXTRA_ADDR, device.getAddress());
			intent.putExtra(EXTRA_STATUS, status);
			sendBroadcast(intent);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == 0) {
				Intent intent = new Intent(GATT_CHARACTERISTIC_READ);
				intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
				intent.putExtra(EXTRA_STATUS, status);
				intent.putExtra(EXTRA_VALUE, characteristic.getValue());
				sendBroadcast(intent);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			onCharacteristicRead(gatt, characteristic, 0);
		}
	};

	public class LocalBinder extends Binder {
		GattAppService getService() {
			return GattAppService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		if (mBluetoothGatt != null) {
			mBluetoothGatt.close();
		}

		super.onDestroy();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	public boolean init() {
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null)
				return false;
		}
		if (mBluetoothAdapter == null) {
			mBluetoothAdapter = mBluetoothManager.getAdapter();
			if (mBluetoothAdapter == null)
				return false;
		}

		return true;
	}

	public void setReconnect(boolean reconnect) {
		mReconnect = reconnect;
	}

	public boolean getReconnect() {
		return mReconnect;
	}


	public void connect(String address) {
		if (mBluetoothAdapter == null)
			return;

		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			return;
		}
		if (address.equals(mDevice) && mBluetoothGatt != null) {
			mBluetoothGatt.connect();
		} else {
			mBluetoothGatt = device.connectGatt(this, true, mGattCallbacks);
		}

//		mDevice = address;
	}

	/**
	 * 蓝牙断开连接
	 */
	public void disconnect() {
		if (mBluetoothGatt == null)
			return;
		mBluetoothGatt.disconnect();
	}

	public void doneWithDevice() {
		if (mBluetoothGatt == null)
			return;
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	public void discover() {
		if (mBluetoothGatt == null)
			return;
		mBluetoothGatt.discoverServices();
	}

	public List<BluetoothGattService> getServices() {
		if (mBluetoothGatt == null)
			return null;
		return mBluetoothGatt.getServices();
	}

	public BluetoothGattCharacteristic getCharacteristic(UUID serviceUuid, UUID charUuid) {
		if (mBluetoothGatt == null)
			return null;
		BluetoothGattService service = mBluetoothGatt.getService(serviceUuid);
		if (service == null)
			return null;
		return service.getCharacteristic(charUuid);
	}

	public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothGatt == null)
			return false;
		return mBluetoothGatt.readCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothGatt == null)
			return false;
		return mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public boolean enableNotification(boolean enable, BluetoothGattCharacteristic characteristic) {
		if (mBluetoothGatt == null)
			return false;
		if (!mBluetoothGatt.setCharacteristicNotification(characteristic, enable))
			return false;

//		BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(CLIENT_CONFIG_UUID);
//		if (clientConfig == null)
//			return false;
//
//		if (enable) {
//			clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//		} else {
//			clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
//		}
//		return mBluetoothGatt.writeDescriptor(clientConfig);
		return true;
	}

	public boolean enableIndication(boolean enable, BluetoothGattCharacteristic characteristic) {
		if (mBluetoothGatt == null)
			return false;
		if (!mBluetoothGatt.setCharacteristicNotification(characteristic, enable))
			return false;

		BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(CLIENT_CONFIG_UUID);
		if (clientConfig == null)
			return false;

		if (enable) {
			clientConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		} else {
			clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
		}
		return mBluetoothGatt.writeDescriptor(clientConfig);
	}

	private void sendDeviceFoundIntent(BluetoothDevice device, int rssi, int source) {
		Intent intent = new Intent(GATT_DEVICE_FOUND);
		intent.putExtra(EXTRA_DEVICE, device);
		intent.putExtra(EXTRA_RSSI, rssi);
		intent.putExtra(EXTRA_SOURCE, source);
		sendBroadcast(intent);
	}
}
