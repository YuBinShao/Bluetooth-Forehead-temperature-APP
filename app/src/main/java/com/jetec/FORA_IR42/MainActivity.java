package com.jetec.FORA_IR42;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private Activity activity;
    private static final String TAG = MainActivity.class.getSimpleName()+"My";
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final int REQUEST_FINE_LOCATION_PERMISSION = 102;
    private static final int REQUEST_ENABLE_BT = 2;
    private boolean isScanning = false;
    Button historybtn;

    DeviceControlActivity devicecontrol = new DeviceControlActivity();
    ScannedData scandevice;
    /**nfc*/
    private NfcAdapter mNfcAdapter;
    PendingIntent mPendingIntent;
    IntentFilter[] mFilters;
    String[][] mTechLists;

    /**camera*/
    public static final int PermissionCode = 1000;
    public static final int GetPhotoCode = 1001;
    private static boolean isCameraPermission = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicecontrol.ReturnContent(getApplicationContext());

        devicecontrol.sqlDataBaseHelper = new SqlDataBaseHelper(this,devicecontrol.DataBaseName,
                null,devicecontrol.DataBaseVersion,devicecontrol.DataBaseTable);
        devicecontrol.db = devicecontrol.sqlDataBaseHelper.getWritableDatabase();

        devicecontrol.temperature = findViewById(R.id.temperature);
        devicecontrol.status = findViewById(R.id.status);
        /**權限相關認證*/
        checkPermission();
        /**初始藍牙掃描及掃描開關之相關功能*/
        bluetoothScan();

        devicecontrol.imageView3 = findViewById(R.id.imageView3);
        devicecontrol.time = findViewById(R.id.time);
        devicecontrol.date = findViewById(R.id.date);
        devicecontrol.settime();

        historybtn = findViewById(R.id.history);
        historybtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToast("請稍等...資料讀取中...");
                startActivity(new Intent(MainActivity.this, ActivityH.class));

            }
        });
        /**nfc*/
        NfcManager manager = (NfcManager) getSystemService(NFC_SERVICE);
        mNfcAdapter = manager.getDefaultAdapter();
        mPendingIntent = PendingIntent.getActivity(
                this,0,new Intent(this,getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        mFilters = new IntentFilter[]{
                ndef,
        };
        mTechLists = new String[][]{new String[]{NfcA.class.getName()}};
        devicecontrol.mNfcInfoText = findViewById(R.id.ID);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (null == mNfcAdapter) {
            devicecontrol.mNfcInfoText.setText("Not support NFC!");
            showToast("Not support NFC!");
            return;
        }

        if (!mNfcAdapter.isEnabled()) {
            devicecontrol.mNfcInfoText.setText("Please open NFC!");
            showToast("Please open NFC!");
            return;
        }

        if (getIntent() != null) {
            processIntent(getIntent());
        }
        /**camera*/
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            isCameraPermission = true;
        }
        devicecontrol.mSurfaceView = (SurfaceView) this.findViewById(R.id.preview);
        devicecontrol.mSurfaceHolder = devicecontrol.mSurfaceView.getHolder();
        devicecontrol.mSurfaceHolder.addCallback(this);
        activity = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "22222222222222222222222222222");
        mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        registerReceiver(devicecontrol.mGattUpdateReceiver, devicecontrol.makeGattUpdateIntentFilter());
        if (devicecontrol.mBluetoothLeService != null) {
            final boolean result = devicecontrol.mBluetoothLeService.connect(devicecontrol.selectedDevice.getAddress());
        }
        /**nfc*/
        mNfcAdapter.enableForegroundDispatch(this,mPendingIntent,mFilters,mTechLists);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "0000000000000000000000000000000");

        /**nfc*/
        mNfcAdapter.disableForegroundDispatch(this);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (devicecontrol.mBluetoothLeService == null) return;
        devicecontrol.mBluetoothLeService.disconnect();
        unbindService(devicecontrol.mServiceConnection);
        devicecontrol.mBluetoothLeService = null;
        unregisterReceiver(devicecontrol.mGattUpdateReceiver);
        devicecontrol.sqlDataBaseHelper.close();

    }

    /**權限相關認證*/
    private void checkPermission() {
        /**確認手機版本是否在API18以上，否則退出程式*/
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            /**camera*/
            /**確認是否已開啟取得手機位置功能以及權限*/
            int hasGone = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            if (hasGone != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.CAMERA},
                        REQUEST_FINE_LOCATION_PERMISSION);
            }
            /**確認手機是否支援藍牙BLE*/
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this,"Not support Bluetooth", Toast.LENGTH_SHORT).show();
                finish();
            }
            /**開啟藍芽適配器*/
            if(!mBluetoothAdapter.isEnabled()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent,REQUEST_ENABLE_BT);
            }
        }else finish();
    }
    /**初始藍牙掃描及掃描開關之相關功能*/
    private void bluetoothScan() {
        /**啟用藍牙適配器*/
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        /**開始掃描*/
        /**新版*/
        mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        /**舊版*/
        //mBluetoothAdapter.startLeScan(mLeScanCallback);
        isScanning = true;
    }

    @Override
    protected void onStart() {
        super.onStart();


        isScanning = true;

        Log.d(TAG, "111111111111111111111111111111111");
        /**新版*/
        mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        /**舊版*/
//        mBluetoothAdapter.startLeScan(mLeScanCallback);
//        /**camera*/
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            int hasCameraPermission = checkSelfPermission(Manifest.permission.CAMERA);
//            List<String> permissions = new ArrayList<String>();
//
//            if (hasCameraPermission != PackageManager.PERMISSION_GRANTED) {
//                permissions.add(Manifest.permission.CAMERA);
//            }
//            if (!permissions.isEmpty()) {
//                requestPermissions(permissions.toArray(new String[permissions.size()]), 111);
//            }
//        }
    }
    /**避免跳轉後掃描程序係續浪費效能，因此離開頁面後即停止掃描*/
    @Override
    protected void onStop() {
        super.onStop();
        /**關閉掃描*/
        /**新版*/
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
        /**舊版*/
        //mBluetoothAdapter.stopLeScan(mLeScanCallback);

        unregisterReceiver(devicecontrol.mGattUpdateReceiver);
        Log.d(TAG, "==================================");

    }


    //ScanCallback 是蓝牙扫描返回结果的回调，可以通过回调获取扫描结果。
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            new Thread(()->{
                /**如果裝置沒有名字，就不顯示*/
                if (result.getDevice().getName()!= null){
                    if(result.getDevice().getName().equals("FORA IR42")){
                        scandevice = new ScannedData(result.getDevice().getName()
                                , String.valueOf(result.getRssi())
                                , byteArrayToHexStr(result.getScanRecord().getBytes())
                                , result.getDevice().getAddress());
                        transport();
                    }

                }
            }).start();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            //在此返回一个包含所有扫描结果的列表集，包括以往扫描到的结果。
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            //扫描失败后的处理。
        }
    };

    public void transport(){
        /**取得所選中的藍芽裝置之相關資料*/
        runOnUiThread(()->{
            /**將陣列送到RecyclerView列表中*/

            if(scandevice!=null) {
                Log.d(TAG, "//////////////////"+scandevice.getDeviceName());
                devicecontrol.selectedDevice = scandevice;
                //getIntent().getSerializableExtra(devicecontrol.INTENT_KEY);
                /**清除之前儲存過的UUID們*/
                if (SampleGattAttributes.myGatt.size() > 0) {
                    SampleGattAttributes.myGatt.clear();
                }
                /**綁定Server:BluetoothLeServer.java*/
                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, devicecontrol.mServiceConnection, BIND_AUTO_CREATE);
                if (devicecontrol.mBluetoothLeService != null) {
                    final boolean result = devicecontrol.mBluetoothLeService.connect(devicecontrol.selectedDevice.getAddress());
                }
            }
        });
    }

    public static String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }

        StringBuilder hex = new StringBuilder(byteArray.length * 2);
        for (byte aData : byteArray) {
            hex.append(String.format("%02X", aData));
        }
        String gethex = hex.toString();
        return gethex;
    }
    /**nfc*/
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            processIntent(intent);
        }
    }

    private void processIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        byte[] extraId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);

        // Id
        if (extraId != null) {
            String hex = encodeHexString(extraId);
            int decimal=Integer.parseInt(hex,16);
            boolean sendmail_flag = false;
            String StudentID = "";
            if (devicecontrol.IDflag==true){
                StudentID = findid(String.valueOf(decimal));
                devicecontrol.mNfcInfoText.setText(StudentID);

                String UpdateSql = "update TemperatureRecord set id =" + StudentID + " where  (time = (SELECT max(time) FROM TemperatureRecord)) " ;
                devicecontrol.db.execSQL(UpdateSql);

                sendmail_flag = true ;

            }
            else{
                showToast("Please take your temperature first");
            }

            if(sendmail_flag && devicecontrol.IDflag && DeviceControlActivity.anstofloat>= 37){

                senEmail( StudentID , DeviceControlActivity.anstofloat);
                sendmail_flag = false ;
            }

            Log.d(TAG,"/////////////"+decimal);
        }

    }

    private void senEmail( String IDtext ,float anstofloat) {
        JavaMailAPI javaMailAPI = new JavaMailAPI(this, "yubinshao213@gmail.com", "額溫超標",
                IDtext  + "為" + anstofloat + "度，該名學生體溫超過37度");


        javaMailAPI.execute();

    }


    public String findid(String n){
        try{
            AssetManager am = getAssets();
            InputStream is =am.open("stud_inf.xls");
            Workbook wb = Workbook.getWorkbook(is);
            Sheet s = wb.getSheet(0);
            int row = s.getRows();
            int col = s.getColumns();
            String ID = "";
            for(int i=0;i<row;i++){
                Cell z = s.getCell(1,i);
                if(z.getContents().equals(n)){
                    Cell zz = s.getCell(0,i);
                    ID += zz.getContents();
                    break;
                }
            }
            return ID;
        }
        catch (Exception e){
            return String.valueOf(e);
        }
    }

    private String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    private String encodeHexString(byte[] byteArray) {
        StringBuilder hexStringBuffer = new StringBuilder();
        for (byte aByteArray : byteArray) {
            hexStringBuffer.insert(0,byteToHex(aByteArray));
        }
        return hexStringBuffer.toString();
    }

    private void showToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**camera*/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //假如允許了
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            isCameraPermission = true;
            Log.d(TAG,"@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            //do something
            Toast.makeText(this, "感謝賜予權限！", Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent(MainActivity.this, MainActivity.class), GetPhotoCode);
        }
        //假如拒絕了
        else {
            isCameraPermission = false;
            //do something
            Toast.makeText(this, "CAMERA權限FAIL，請給權限", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        if(isCameraPermission){
            android.hardware.Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation;
            rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(result);
        }
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(isCameraPermission){
            int cameraCount = 0;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            cameraCount = Camera.getNumberOfCameras();//得到攝像頭的個數
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);//得到每一個攝像頭的資訊
                if (devicecontrol.cameraPosition == 1) {
                    //現在是後置，變更為前置
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表攝像頭的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK後置
                        devicecontrol.camera = Camera.open(i);//開啟當前選中的攝像頭
                        setCameraDisplayOrientation(this,cameraCount-1,devicecontrol.camera);
                        try {
                            devicecontrol.camera.setPreviewDisplay(holder);
                        } catch (IOException e) {
                            devicecontrol.camera.release();
                            devicecontrol.camera = null;
                        }
                        devicecontrol.cameraPosition = 1;
                    }
                }
            }

            Log.d("MYLOG","SurfaceView is Creating!");
        }
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("MYLOG","SurfaceView is Change!");
        if(isCameraPermission){
            devicecontrol.camera.startPreview();
        }
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("MYLOG","SurfaceView is Destroyed!");
        if(isCameraPermission){
            devicecontrol.camera.release();
        }
        devicecontrol.camera = null;
    }
}
