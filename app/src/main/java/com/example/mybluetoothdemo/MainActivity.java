package com.example.mybluetoothdemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.xiaoming.bluttoothlibrary.BluetoothHelper;
import com.xiaoming.bluttoothlibrary.CheckPermisssion;
import com.xiaoming.bluttoothlibrary.MyBluetoothCallBack;

public class MainActivity extends AppCompatActivity {
    private BluetoothHelper bluetoothHelper;
    private Button bt_connect;
    private Button bt_disconnect;
    private Button bt_opendoor;
    private final int REQUEST_ENABLE_BT=10001;
    private Context context;
    private ProgressDialog progressDialog;
    private boolean isConnect;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initEvent();
    }

    private void initEvent() {
        progressDialog = new ProgressDialog(MainActivity.this,R.style.AppTheme);
        bt_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               openBluetooth();  // 检测蓝牙有没有打开
            }
        });
        bt_opendoor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isConnect){
                    bluetoothHelper.handleDoor(1,"12","12");
                }else {
                    Toast.makeText(context, "请先连接蓝牙1", Toast.LENGTH_SHORT).show();
                }
            }
        });

        bt_disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isConnect){
                    bluetoothHelper.disConnectBlueTooth();
                }else {
                    Toast.makeText(context, "蓝牙尚未连接", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initView() {
        context= MainActivity.this;
        bt_connect= (Button) findViewById(R.id.bt_connect);
        bt_disconnect= (Button) findViewById(R.id.bt_disconnect);
        bt_opendoor= (Button) findViewById(R.id.bt_open_door);
        bluetoothHelper=new BluetoothHelper(MainActivity.this, new MyBluetoothCallBack() {
            @Override
            public void connectSuccess() {
                isConnect=true;
                progressDialog.dismiss();
            }
            @Override
            public void connectFail() {
                isConnect=false;
                progressDialog.dismiss();
            }

            @Override
            public void handleSuccess() {
                progressDialog.dismiss();
            }

            @Override
            public void handleFail() {
                progressDialog.dismiss();
            }
        });
    }
    private void showDialog(String message){
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMessage(message);
        Window window = progressDialog.getWindow();
        window.setGravity(Gravity.CENTER);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        progressDialog.show();
    }


    /**
     *1、 检测蓝牙是否可用2、弹框开启蓝牙功能
     */
    private void openBluetooth(){
        if(CheckPermisssion.checkBtIsValueble()){
            //蓝牙检测是否开启，如果开启就直接弹加载框
            if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }else {
                showDialog("蓝牙连接中");
                bluetoothHelper.connectBlueTooth("20:91:48:55:69:C6".replaceAll(":", ""),
                        "0000ffe4", "0000ffe9", "000ffe0", "0000ffe5"); // 测试
            }
        }else {
            Toast.makeText(context, "亲，该设备蓝牙不可用...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //判断是不是启动蓝牙的结果
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                //成功
                Toast.makeText(context, "蓝牙开启成功...", Toast.LENGTH_SHORT).show();
                showDialog("蓝牙连接中");
                bluetoothHelper.connectBlueTooth("20:91:48:55:69:C6".replaceAll(":", ""),
                        "0000ffe4", "0000ffe9", "000ffe0", "0000ffe5"); // 测试
            } else {
                //失败
                Toast.makeText(context, "蓝牙开启失败...", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
