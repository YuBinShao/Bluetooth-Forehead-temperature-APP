package com.jetec.FORA_IR42;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class DeviceControlActivity extends AppCompatActivity {
    public static final String INTENT_KEY = "GET_DEVICE";
    public static final String TAG = DeviceControlActivity.class.getSimpleName() + "My";
    public BluetoothLeService mBluetoothLeService;
    public ScannedData selectedDevice;

    public TextView mConnectionState;
    public TextView mDataField;
    public ExpandableListView mGattServicesList;
    public ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    public BluetoothGattCharacteristic mNotifyCharacteristic;

    public TextView temperature,status;
    public TextView time,date;
    protected ImageView imageView3;

    protected SqlDataBaseHelper sqlDataBaseHelper;
    protected static final String DataBaseName = "TemperatureDataBase.db";
    protected static final int DataBaseVersion = 1;
    protected static final String DataBaseTable = "TemperatureRecord";

    public static float anstofloat;

    String Currenttime;

    /** Max_History_data_Count  */
    private int MaxHistoryCount = 50;

    BluetoothLeService bluetoothleservice = new BluetoothLeService();
    protected SQLiteDatabase db;

    Context fileContext;

    /** nfc */
    public TextView mNfcInfoText;
    public boolean IDflag = false;
    /**camera*/
    public SurfaceView mSurfaceView;
    public SurfaceHolder mSurfaceHolder;
    public Camera camera;
    public int cameraPosition = 1;//0代表前置攝像頭，1代表後置攝像頭
    static final int CARMERA_REQUEST_CODE = 1001;
//    @Override
//    protected void onResume() {
//        super.onResume();
//        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//        if (mBluetoothLeService != null) {
//            final boolean result = mBluetoothLeService.connect(selectedDevice.getAddress());
//        }
//    }

    public void ReturnContent(Context fileContext){
        this.fileContext = fileContext;
    }

    /**綁定IntentFilter，使手機端能監聽藍GATT的狀態*/
    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);//連接一個GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);//從GATT服務中斷開連接
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);//查找GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);//從服務中接受(收)數據
        return intentFilter;
    }

    /**此處為控制藍芽連線的部分*/
    public final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(selectedDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService.connect(selectedDevice.getAddress());

        }
    };//serviceConnection

    /**此處為顯示可用的服務延展式列表UI*/
    public void displayGattServices(List<BluetoothGattService> gattServices) {
        final String LIST_NAME = "NAME";
        final String LIST_UUID = "UUID";
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = "未知服務";
        String unknownCharaString = "未知裝置";
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.

            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        if (mGattCharacteristics != null) {
            final BluetoothGattCharacteristic characteristic =
                    mGattCharacteristics.get(3).get(0);
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                mBluetoothLeService.readCharacteristic(characteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = characteristic;
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }
    }

    public final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();


            /**如果有連接*/
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "藍芽已連線");

            }
            /**如果沒有連接*/
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "藍芽已斷開");
            }
            /**如果找到GATT服務*/
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "已搜尋到GATT服務");
                displayGattServices(mBluetoothLeService.getSupportedGattServices());

            }
            /**接收來自藍芽傳回的資料*/
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "接收到藍芽資訊");
                byte[] getByteData = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                final StringBuilder stringBuilder = new StringBuilder(getByteData.length);
                for (byte byteChar : getByteData)
                    stringBuilder.append(String.format("%02X ", byteChar));
                String stringData = new String(getByteData);
                displayData(stringData,byteArrayToHexStr(getByteData));
                mBluetoothLeService.disconnect();
            }
        }
    };//onReceive
    /**此處為接收藍芽回傳的內容*/
    @SuppressLint("SetTextI18n")
    public void displayData(String stringData, String byteArrayData){
        if (stringData != null) {
            byteArrayData=byteArrayData.substring(1, 4);
            int x=16,y=2,ans=0,z=0;
            for(int i=0 ; i<3 ; i++){
                if(byteArrayData.charAt(i)>'9'){
                    z=byteArrayData.charAt(i)-55;
                }
                else z=byteArrayData.charAt(i)-'0';
                if(i==0) z-=5;
                ans+=z*(Math.pow(x,y));
                y--;
            }
            anstofloat=ans;
            anstofloat/=10;
            byteArrayData=String.valueOf(anstofloat);
            Log.d(TAG, "///////////////"+byteArrayData);
            temperature.setText(byteArrayData+"°");
            if(anstofloat>=37.5){
                status.setText("fever");
                status.setTextColor(Color.rgb(230, 0, 0));
                temperature.setTextColor(Color.rgb(230, 0, 0));
            }
            else if(35<=anstofloat){
                status.setText("normal");
                status.setTextColor(Color.rgb(0, 130, 0));
                temperature.setTextColor(Color.rgb(0, 130, 0));
            }
            else{
                status.setText("low");
                status.setTextColor(Color.rgb(0, 0, 230));
                temperature.setTextColor(Color.rgb(0, 0, 230));
            }
            mNfcInfoText.setText("ID");
            String datetime = settime();


            String statusview = "";
            // senEmail(anstofloat);              //            send mail
            statusview.equals(status.getText());
            InsertData(byteArrayData);
            camera.takePicture(null, null, mCall);
            IDflag = true;
            /**countdown*/
            new CountDownTimer(15000, 1000) {
                public void onTick(long millisUntilFinished) {
//                    showToast("Please identify your ID card in 15 secs.");
                }
                public void onFinish() {
                    IDflag = false;
                }
            }.start();
        }

    }

 /*   private void senEmail( float anstofloat) {
        JavaMailAPI javaMailAPI = new JavaMailAPI(this, "yubinshao213@gmail.com", "額溫超標",
                mNfcInfoText + "為" + anstofloat + "度，該名學生體溫超過37度");


        javaMailAPI.execute();

    }*/

    private void showToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public String settime(){
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        String currenttime = formatter.format(new Date());
        time.setText(currenttime);
        SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy/MM/dd");
        String nowdate = formatter2.format(new Date());
        date.setText(nowdate);
        return nowdate + "  " + currenttime ;
    }

    private void InsertData(String byteArrayData){

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        this.Currenttime = formatter.format(new Date());
        SimpleDateFormat formatter2 = new SimpleDateFormat("MM/dd HH:mm");
        String datetime = formatter2.format(new Date());

        ContentValues contentValues = new ContentValues();
        contentValues.put("time",this.Currenttime);
        contentValues.put("date",datetime);
        contentValues.put("temperature",byteArrayData+"°");
        contentValues.put("status",status.getText().toString());
        contentValues.put("id","No ID");

        db.insert(DataBaseTable,null,contentValues);

        Log.d(TAG,"ppppppppppppppppppppppp        " + allCaseNum());
        if(allCaseNum() >  this.MaxHistoryCount) {
//            fileContext.deleteFile(getResult() + ".jpg");
            String sql = "DELETE FROM TemperatureRecord WHERE time = (SELECT min(time) FROM TemperatureRecord)";
            db.execSQL(sql);
        }


    }

    public long allCaseNum( ){
        String sql = "select count(*) from TemperatureRecord";
        Cursor cursor = db.rawQuery(sql, null);
        cursor.moveToFirst();
        long count = cursor.getLong(0);
        cursor.close();
        return count;
    }

    public String getResult()
    {
        String name = null;
        try
        {
            Cursor c = db.rawQuery("SELECT min(time) FROM TemperatureRecord", null);
            c.moveToFirst();

            name = c.getString(0);
            c.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return name;
    }

    /**cmaera*/
    Camera.PictureCallback mCall = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            // decode the data obtained by the camera into a Bitmap
            // display.setImageBitmap(photo);
            Bitmap bitmapPicture = BitmapFactory.decodeByteArray(data, 0,
                    data.length);
            Matrix m=new Matrix();
            int width=bitmapPicture.getWidth();
            //取得圖片的長度
            int height=bitmapPicture.getHeight();
            //逆時針旋轉90度
            m.setRotate(-90);
            m.postScale(-1, 1);
            //產生新的旋轉後Bitmap檔
            Bitmap b=Bitmap.createBitmap(bitmapPicture, 0, 0, width, height, m, true);
            camera.startPreview();
            storeImage(b);
            // Log.v("MyActivity","Length: "+data.length);
        }
    };

    private void storeImage(Bitmap image) {
        try {
            String mImageName= this.Currenttime +".jpg";

            FileOutputStream fos = fileContext.openFileOutput(mImageName, MODE_PRIVATE);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Byte轉16進字串工具
     */
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
}

