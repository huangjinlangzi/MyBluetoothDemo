package com.xiaoming.bluttoothlibrary;

import android.bluetooth.BluetoothAdapter;

/**
 * Created by Administrator on 2017/8/15.
 */

public class CheckPermisssion {
    /**

     * 参数 无

     * 返回值 true 表示可以用嘛，否则不可以

     * 异常 无

     * 描述：这个方法用于检查蓝牙是否可用

     */

    public static boolean checkBtIsValueble() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        } else {
            return true;
        }

    }

    public static void openBuletooth(){
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            BluetoothAdapter.getDefaultAdapter().enable();
        }
    }
}
