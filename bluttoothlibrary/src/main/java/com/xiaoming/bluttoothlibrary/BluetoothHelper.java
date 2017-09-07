package com.xiaoming.bluttoothlibrary;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by Administrator on 2017/8/15.
 */

public class BluetoothHelper {
    public String mAddress = "";
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_READY = 4;
    private int mState = STATE_DISCONNECTED;
    private boolean mNotify = false;
    private boolean mIndicate = false;

    private  GattAppService mService = null;
    private BluetoothGattCharacteristic mRCharacteristic = null;
    private BluetoothGattCharacteristic mWCharacteristic = null;
    private String ble_characteristic_ready_uuid;
    private String ble_characteristic_write_uuid;
    private String ble_ready_uuid;
    private String ble_write_uuid;
    private static Activity activity;
    private Intent bindIntent;
    private MyBluetoothCallBack myBluetoothCallBack;
    private boolean flag=false; // 服务启动标志
    private GetCodeTimeCount time;
    private final BroadcastReceiver GattStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            if (intent.getAction().equals(GattAppService.GATT_CONNECTION_STATE)) {
                String address = intent.getStringExtra(GattAppService.EXTRA_ADDR);
                if (address.equals(mAddress)) {
                    mState = intent.getBooleanExtra(GattAppService.EXTRA_CONNECTED, false) ? STATE_CONNECTED
                            : STATE_DISCONNECTED;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(mState==STATE_CONNECTED){
                                myBluetoothCallBack.connectSuccess(); //蓝牙连接成功
                            }else if(mState==STATE_DISCONNECTED) {
                                myBluetoothCallBack.connectFail();
                                mScanning=false;
                                Toast.makeText(context,"蓝牙连接失败或者已断开连接",Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    Log.e("mstate1::",mState+"");
                }
            } else if (intent.getAction().equals(GattAppService.GATT_SERVICES_REFRESHED)) {
                mState = STATE_READY;
                List<BluetoothGattService> services = mService.getServices();
                for (BluetoothGattService service : services) {
                    if(service.getUuid().toString().startsWith(ble_ready_uuid)){
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            if(characteristic.getUuid().toString().startsWith(ble_characteristic_ready_uuid)){
                                mRCharacteristic = mService.getCharacteristic(service.getUuid(), characteristic.getUuid());
                            }
                            Log.e("ble","Service:" + service.getUuid().toString() + ";" + characteristic.getUuid().toString());
                        }
                    }else if(service.getUuid().toString().startsWith(ble_write_uuid)){
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            if(characteristic.getUuid().toString().startsWith(ble_characteristic_write_uuid)){
                                mWCharacteristic = mService.getCharacteristic(service.getUuid(),characteristic.getUuid());
                            }
                            Log.e("ble","Service:" + service.getUuid().toString() + ";" + characteristic.getUuid().toString());
                        }
                    }
                }
//                mRCharacteristic = mService.getCharacteristic(UUID_R_SERVICE, UUID_R_CHARACTERISTIC);
//                mWCharacteristic = mService.getCharacteristic(UUID_W_SERVICE, UUID_W_CHARACTERISTIC);
                if (mRCharacteristic != null) {
                    if ((mRCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        mService.readCharacteristic(mRCharacteristic);
                    }
                    if ((mRCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        setNotify(true);
                    } else if ((mRCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        setIndicate(true);
                    }
                }
                Log.e("mstate2::",mState+"");
            } else if (intent.getAction().equals(GattAppService.GATT_CHARACTERISTIC_READ)) {
                Log.e("characteristic::::",intent.getStringExtra(GattAppService.EXTRA_UUID));
                final byte[] value = intent.getByteArrayExtra(GattAppService.EXTRA_VALUE);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setValue(value);
                    }
                });
            }
            Log.e("mstate::",mState+"");
        }
    };
    private synchronized void  setValue(byte[] value) {
        if (value == null || value.length == 0) {
            Toast.makeText(activity, "", Toast.LENGTH_SHORT).show();
            return;
        }
        if (value.length < 7) {
            Toast.makeText(activity, "消息长度不对", Toast.LENGTH_SHORT).show();
            return;
        }
        if (value[0] == (byte) (0xF5 & 0xFF)) {
            if (value[6] == (byte) (0xa1 & 0xFF)) {
                switch (value[7]) {
                    case 0x00:
                        Toast.makeText(activity, "成功", Toast.LENGTH_SHORT).show();
                        myBluetoothCallBack.handleSuccess();
                        break;
                    case 0x01:
                        Toast.makeText(activity, "失败，设备ID不一致", Toast.LENGTH_SHORT).show();
                        myBluetoothCallBack.handleFail();
                        break;
                    case 0x02:
                        Toast.makeText(activity, "失败，密钥不正确", Toast.LENGTH_SHORT).show();
                        myBluetoothCallBack.handleFail();
                        break;
                    case 0x03:
                        Toast.makeText(activity, "失败，其他", Toast.LENGTH_SHORT).show();
                        myBluetoothCallBack.handleFail();
                        break;
                    default:
                        Toast.makeText(activity, "应答错误", Toast.LENGTH_SHORT).show();
                        myBluetoothCallBack.handleFail();
                        break;
                }
            }
        } else {
            Toast.makeText(activity, "标记头发送错误", Toast.LENGTH_SHORT).show();
        }
    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((GattAppService.LocalBinder) rawBinder).getService();
            if (!mService.init()) {
                activity.finish();
                return;
            }
            mState = STATE_CONNECTING;
            mService.connect(mAddress);
            flag=true;
        }
        @Override
        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
            mState=STATE_DISCONNECTED;
            flag=false;
        }
    };


    public  BluetoothHelper (Activity mContext, MyBluetoothCallBack myBluetoothCallBack ){
        activity=mContext;
        bindIntent = new Intent(mContext, GattAppService.class);
        this.myBluetoothCallBack=myBluetoothCallBack;
        time = new GetCodeTimeCount(10000L, 1000L);
    }

    /**
     * 连接蓝牙
     *
     *  private String ble_characteristic_ready_uuid;
     private String ble_characteristic_write_uuid;
     private String ble_ready_uuid;
     private String ble_write_uuid;
     */
    public void connectBlueTooth(String mAddress,String ble_characteristic_ready_uuid,String ble_characteristic_write_uuid,
                                 String ble_ready_uuid,String ble_write_uuid){
        registerBroadcast();
        this.mAddress=mAddress;
        this.ble_characteristic_ready_uuid=ble_characteristic_ready_uuid;
        this.ble_characteristic_write_uuid=ble_characteristic_write_uuid;
        this.ble_ready_uuid=ble_ready_uuid;
        this.ble_write_uuid=ble_write_uuid;
        scanLeDevice(true);

    }

    /**
     * 断开连接
     */
    public void disConnectBlueTooth(){
        if(flag){
            mService.disconnect();
        }
    }
    /**
     * 退出销毁
     */
    public void unRegister(){
        if(flag){
            activity.unbindService(mServiceConnection);
            activity.unregisterReceiver(GattStatusReceiver);
            mService=null;
        }
    }
    /**
     * 广播注册
     */
    private void registerBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GattAppService.GATT_CONNECTION_STATE);
        intentFilter.addAction(GattAppService.GATT_SERVICES_REFRESHED);
        intentFilter.addAction(GattAppService.GATT_CHARACTERISTIC_READ);
        activity.registerReceiver(GattStatusReceiver, intentFilter);
    }

    private void setNotify(boolean enable) {
        if (mState == STATE_READY && mService != null && mRCharacteristic != null) {
            mNotify = enable;
            mService.enableNotification(mNotify, mRCharacteristic);
        } else {
            mNotify = false;
        }
    }

    private void setIndicate(boolean enable) {
        if (mState == STATE_READY && mService != null && mRCharacteristic != null) {
            mIndicate = enable;
            mService.enableIndication(mIndicate, mRCharacteristic);
        } else {
            mIndicate = false;
        }
    }

    /**
     * 开关车门
     * @param b
     * @param deviceId (设备号)
     * @param myPassword（密钥）
     */
    public void  handleDoor(int b, String deviceId,String myPassword) {
        deviceId = deviceId.replaceAll(" ", "");
        if (deviceId == null || deviceId.length() == 0){
            return;
        }
        if(Long.parseLong(deviceId) > Integer.MAX_VALUE) {
            Toast.makeText(activity, "数值超过范围", Toast.LENGTH_SHORT).show();
            return ;
        }
        byte[] deviceID = intToByteArray(Integer.parseInt(deviceId));
        if (deviceID.length == 0){
            return;
        }
        if(myPassword==null||myPassword.length()==0){
            return;
        }
        if(Long.parseLong(myPassword) > Integer.MAX_VALUE) {
            Toast.makeText(activity, "数值超过范围", Toast.LENGTH_SHORT).show();
            return ;
        }
        byte[] password = intToByteArray(Integer.parseInt(myPassword));
        if (password.length == 0){
            return;
        }
        byte[] data = new byte[12];
        data[0] = (byte) 0xF5;// 标记头
        data[1] = (byte)(10);// 长度
        System.arraycopy(deviceID, 0, data, 2, 4);  //设备标识
        data[6] = (byte) 0x09; // 命令字
        System.arraycopy(password,0,data,7,4); //控制密钥
        //控制指令
        if (b==0){
            data[11] = (byte) 0x00;
        }else if(b==1){
            data[11] = (byte) 0x01;
        }else if(b==2){
            data[11] = (byte) 0x02;
        }else if(b==3){
            data[11] = (byte) 0x03;
        }else if(b==4){
            data[11] = (byte) 0x04;
        }else if(b==5){
            data[11] = (byte) 0x05;
        }
        if(mWCharacteristic!=null){
            mWCharacteristic.setValue(data);
            mService.writeCharacteristic(mWCharacteristic);
        }
    }
    public byte[] toAsciiBytes(String str) {
        if ((str == null) || (str.length() < 1))
            return null;
        byte[] buf = new byte[str.length()];
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            buf[i] = ((byte) (chars[i] & 0xFF));
        }
        return buf;
    }

    /**
     * 整型转byte[]
     * @param a
     * @return
     */
    private byte[] intToByteArray(int a) {
        return new byte[] { (byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF) };
    }

    /**
     * 字符串转字节
     * @param hexString
     * @return
     */
    private byte[] parseHex(String hexString) {
        hexString = hexString.replaceAll("\\s", "").toUpperCase();
        String filtered = new String();
        for (int i = 0; i != hexString.length(); ++i) {
            if (hexVal(hexString.charAt(i)) != -1)
                filtered += hexString.charAt(i);
        }
        if (filtered.length() % 2 != 0) {
            char last = filtered.charAt(filtered.length() - 1);
            filtered = filtered.substring(0, filtered.length() - 1) + '0' + last;
        }
        return hexStringToByteArray(filtered);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private int hexVal(char ch) {
        return Character.digit(ch, 16);
    }

    private String valueToHex(byte[] value) {
        String hex = "";
        for (byte bb : value)
            hex += String.format("%02x ", bb);
        return hex.subSequence(0, hex.length() - 1).toString(); // Remove
    }

    /**
     * 16进制的字符串表示转成字节数组
     *
     * @param hexString
     *            16进制格式的字符串
     * @return 转换后的字节数组
     **/
    private byte[] toByteArray(String hexString) {
        if (TextUtils.isEmpty(hexString))
            throw new IllegalArgumentException("this hexString must not be empty");

        hexString = hexString.toLowerCase();
        final byte[] byteArray = new byte[hexString.length() / 2];
        int k = 0;
        for (int i = 0; i < byteArray.length; i++) {// 因为是16进制，最多只会占用4位，转换成字节需要两个16进制的字符，高位在先
            byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xff);
            byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xff);
            byteArray[i] = (byte) (high << 4 | low);
            k += 2;
        }
        return byteArray;
    }



    private static final long SCAN_PERIOD = 10000;
    private Handler mHandler= new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    if(flag){
                        mService.connect(mAddress);
                    }else {
                        activity.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                    }
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    time.cancel();
                    break;
                case 2:
                    Toast.makeText(activity,"未扫描到设备",Toast.LENGTH_SHORT).show();
                    myBluetoothCallBack.connectFail();
                    break;
                default:
                    break;
            }
        }
    };
    private boolean mScanning=false;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private void scanLeDevice(final boolean enable) {
        mScanning=!enable;
        if (enable) {
            time.start();
            mBluetoothAdapter.startLeScan(mLeScanCallback);

        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }



    private BluetoothAdapter.LeScanCallback mLeScanCallback =

            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    String blueMac = device.getAddress().replaceAll(":","");
                    if(blueMac.equalsIgnoreCase(mAddress)){
                        Log.e("device:::",device.getAddress());
                        if(!mScanning){
                            mAddress=device.getAddress();
                            mHandler.sendEmptyMessage(1);
                        }
                        mScanning=true;
                    }
                }

            };

    class GetCodeTimeCount extends CountDownTimer {
        public GetCodeTimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            if(!mScanning){
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mHandler.sendEmptyMessage(2);
            }
        }
    }
}
