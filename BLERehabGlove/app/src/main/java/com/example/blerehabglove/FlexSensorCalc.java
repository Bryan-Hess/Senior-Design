package com.example.blerehabglove;

import static com.example.blerehabglove.MainActivity.MSG_INDEX;
import static com.example.blerehabglove.MainActivity.MSG_MIDDLE;
import static com.example.blerehabglove.MainActivity.MSG_PINKY;
import static com.example.blerehabglove.MainActivity.MSG_RING;
import static com.example.blerehabglove.MainActivity.MSG_THUMB;
import static java.lang.Math.log;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Message;
import android.util.Log;
import java.nio.charset.StandardCharsets;

public class FlexSensorCalc {

    public static double calculatePosition(BluetoothGattCharacteristic c) {
        String s = new String(c.getValue(), StandardCharsets.UTF_8);
        double myDouble = Double.parseDouble(s);

        Log.w("FlexSensorCalc", "Value Received: "+ String.valueOf(myDouble));
        return myDouble;
    }

    public static String calculateprogress(Message msg, double reading){
        double progress = 0;
        String pecent = "";
        double[] tollerence = {0,0,0,0,0};
        double y3=Math.pow(reading,3), y2=Math.pow(reading,2), y=reading;


        //Regression values converted to %
        switch (msg.what) {
            case MSG_THUMB:
                progress = (-11.8725*y3)+(83.643*y2)+(-236.0979*y)+380.2074;
                break;
            case MSG_INDEX:
                progress = (-175.3966*reading+712.1525)*.6;
                //progress = (338.014*y3)+(-3.5729E+3*y2)+(1.236E4*y)-1.3887E4;
                break;
            case MSG_MIDDLE:
                progress = (-261.2576*reading+1.1419E+3)*.6;
                //progress = (1.091E+3*y3)+(-1.3238E+4*y2)+(5.3203E+4*y)-7.0725E+4;
                break;
            case MSG_RING:
                //progress = (329.2518*y3)+(-3.5663E+3*y2)+(1.2662E+4*y)-1.4627E+4;
                progress = (-158.120134905516*reading+665.617500915603)*.6;
                break;
            case MSG_PINKY:
                progress = (-9.7278*y3)+(116.477*y2)+(-479.5077*y)+685.3639;
                break;
        }

        //Sets progress bar
        if((progress)<0) {
            pecent="0%";
        }else if((progress)>=100) {
            pecent="100%";
        }else {
            int conversion;
            conversion = (int) (progress) ;
            pecent = (String.valueOf(conversion)+"%");
        }

        return pecent;
    }


}
