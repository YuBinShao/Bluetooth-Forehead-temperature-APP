package com.jetec.FORA_IR42;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ActivityH extends AppCompatActivity {
    private static ListView listView;
    public static String[] AccountArray,AccountID,PasswordArray,IDArray;
    private static final String DataBaseTable = "TemperatureRecord";
    DeviceControlActivity devicecontrol = new DeviceControlActivity();
    Button back;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_h);

        back = findViewById(R.id.back);
        listView = findViewById(R.id.HistoryList);

        devicecontrol.sqlDataBaseHelper = new SqlDataBaseHelper(this,devicecontrol.DataBaseName,null,devicecontrol.DataBaseVersion,devicecontrol.DataBaseTable);
        devicecontrol.db = devicecontrol.sqlDataBaseHelper.getWritableDatabase();

        Cursor c = devicecontrol.db.rawQuery("SELECT * FROM " + DataBaseTable  + " ORDER BY time DESC",null);
        c.moveToFirst();
        int DataCount= c.getCount();

        List<Map<String, Object>> items = new ArrayList<Map<String,Object>>();
        InputStream is = null;
        for(int i = 0;i<DataCount;i++) {
            try {
                Log.d("s",c.getString(0) + ".jpg");
                is = openFileInput(c.getString(0) + ".jpg");
            }catch (IOException e) {
                e.printStackTrace();
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);


            Map<String, Object> item = new HashMap<String, Object>();
            item.put("photo", bitmap);
            item.put("date", c.getString(1));
            item.put("TemperatureValue", c.getString(2));
            item.put("status", c.getString(3));
            item.put("stud_id", c.getString(4));

            items.add(item);
            c.moveToNext();
        }
        if(DataCount > 0){
            try{
                is.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        SimpleAdapter adapter = new SimpleAdapter(
                this,
                items,
                R.layout.list_text,
                new String[]{"photo","date", "TemperatureValue","status","stud_id"},
                new int[]{R.id.imageView3,R.id.DateAndTime, R.id.TemperatureValue,R.id.Status,R.id.stud_id}
        );
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {

            @Override
            public boolean setViewValue(View view, Object data,
                                        String textRepresentation) {
                if(view instanceof ImageView && data instanceof Bitmap){
                    ImageView i = (ImageView)view;
                    i.setImageBitmap((Bitmap) data);
                    return true;
                }
                return false;
            }
        });
        listView.setAdapter(adapter);

        devicecontrol.sqlDataBaseHelper.close();

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//              startActivity(new Intent(ActivityH.this, MainActivity.class));
                finish();
            }
        });



    }
}
