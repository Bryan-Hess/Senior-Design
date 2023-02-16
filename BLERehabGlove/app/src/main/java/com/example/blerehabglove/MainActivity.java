package com.example.blerehabglove;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;

import static java.lang.Math.log;
import static java.lang.String.valueOf;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {
    private final static String TAG = MainActivity.class.getSimpleName();
    private static final String DEVICE_NAME = "Rehab Glove";
    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;
    private ProgressDialog mProgress;
    private BluetoothGatt mConnectedGatt;

    private static final int REQUEST_BLUETOOTH_ADMIN_ID = 1;
    private static final int REQUEST_LOCATION_ID = 2;
    private static final int REQUEST_BLUETOOTH_ID = 3;

    public static final UUID READ_SERVICE = UUID.fromString("00000001-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID THUMB_MEASUREMENT = UUID.fromString("00000002-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID INDEX_MEASUREMENT = UUID.fromString("00000003-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID MIDDLE_MEASUREMENT = UUID.fromString("00000004-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID RING_MEASUREMENT = UUID.fromString("00000005-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID PINKY_MEASUREMENT = UUID.fromString("00000006-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID DONE_FROM_PERIPHERAL = UUID.fromString("00000007-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID EXTRA_MEASUREMENT = UUID.fromString("00000008-627e-47e5-a3fc-ddabd97aa966");
    public static final UUID WRITE_SERVICE = UUID.fromString("00000001-627e-47e5-a3fc-ddabd97aa977");
    public static final UUID MOTOR_CONTROL = UUID.fromString("00000002-627e-47e5-a3fc-ddabd97aa977");
    public static final UUID SPEED_CONTROL = UUID.fromString("00000003-627e-47e5-a3fc-ddabd97aa977");
    public static final UUID POSITION_CONTROL = UUID.fromString("00000004-627e-47e5-a3fc-ddabd97aa977");
    public static final UUID DONE_FROM_APP = UUID.fromString("00000005-627e-47e5-a3fc-ddabd97aa977");

    //Read variables
    private double thumb_pos, index_pos, middle_pos, ring_pos, pinky_pos;
    private boolean done_perph;

    //Write variables
    private int motor_cont=32, speed_cont=50, pos_cont=50;
    private boolean done_app, write_to_app=false, updateFlexUI=false;

    //Internal variables for display
    private int thumb_bit=0, index_bit=0, middle_bit=0, ring_bit=0, pinky_bit=0, sumOfBits=0, temp_mot, temp_spd, temp_pos;
    //private int temT, temI, temM, temR, temP;
    ArrayList<String> exerciseList = new ArrayList<>();
    int[][] exerciseData;
    int arrayCount=10;
    boolean mode=true;


    //Objects for UI interaction
    private Button writeButton, saveButton, cancelButton;
    private SeekBar positionBar, speedBar;
    private ImageButton thumbButton, indexButton, middleButton, ringButton, pinkyButton;
    private ImageView posImg, spdImg;
    private TextView mThumb, mIndex, mMiddle, mRing, mPinky, posTxt, spdTxt, percThumb, percIndex, percMiddle, percRing, percPinky;
    private EditText customExerciseName;
    private Spinner exerciseSelect;
    private AlertDialog.Builder dialogBuilder;
    private AlertDialog dialog;
    private ProgressBar thumbBar, indexBar, middleBar, ringBar, pinkyBar;
    private Switch modeSwitch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setProgressBarIndeterminate(true);

        //Declarations of UI connectors
        mThumb = (TextView) findViewById(R.id.thumb_text);
        mIndex = (TextView) findViewById(R.id.index_text);
        mMiddle = (TextView) findViewById(R.id.middle_text);
        mRing = (TextView) findViewById(R.id.ring_text);
        mPinky = (TextView) findViewById(R.id.pinky_text);

        positionBar = (SeekBar) findViewById(R.id.position_seekBar);
        speedBar = (SeekBar) findViewById(R.id.speed_seekBar);
        posTxt= (TextView) findViewById(R.id.pos_percent_text);
        spdTxt= (TextView) findViewById(R.id.speed_percent_text);

        posImg = findViewById(R.id.positionImg);
        spdImg = findViewById(R.id.speedImg);

        modeSwitch = (Switch) findViewById(R.id.assistedSwitch);

        //Mode switch changes operating mode
        modeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mode = !mode;
            }
        });


        //Position and speed bars changes side image and sets current speed and position values
        positionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                posTxt.setText(valueOf(i)+"%");
                pos_cont = i;
                if(i<33)
                    posImg.setImageResource(R.drawable.zero_hand);
                else if(i<66)
                    posImg.setImageResource(R.drawable.fifty_hand);
                else
                    posImg.setImageResource(R.drawable.hundred_hand);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                spdTxt.setText(valueOf(i)+"%");
                speed_cont = i;
                if(i<33)
                    spdImg.setImageResource(R.drawable.turtle);
                else if(i<66)
                    spdImg.setImageResource(R.drawable.rabbit);
                else
                    spdImg.setImageResource(R.drawable.rocket);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        //Finger selection code
        thumbButton = (ImageButton) findViewById(R.id.thumb_button);
        thumbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(thumb_bit==0) {
                    thumbButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    thumb_bit = 1;
                }else{
                    thumbButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    thumb_bit = 0;
                }
            }
        });

        indexButton = (ImageButton) findViewById(R.id.index_button);
        indexButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(index_bit==0) {
                    indexButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    index_bit = 1;
                }else{
                    indexButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    index_bit = 0;
                }
            }
        });

        middleButton = (ImageButton) findViewById(R.id.middle_button);
        middleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(middle_bit==0) {
                    middleButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    middle_bit = 1;
                }else{
                    middleButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    middle_bit = 0;
                }
            }
        });

        ringButton = (ImageButton) findViewById(R.id.ring_button);
        ringButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ring_bit==0) {
                    ringButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    ring_bit = 1;
                }else{
                    ringButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    ring_bit = 0;
                }
            }
        });

        pinkyButton = (ImageButton) findViewById(R.id.pinky_button);
        pinkyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(pinky_bit==0) {
                    pinkyButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    pinky_bit = 1;
                }else{
                    pinkyButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    pinky_bit = 0;
                }
            }
        });

        //Button to write to microcontroller and complete an exercise
        writeButton = (Button) findViewById(R.id.hand_close_write);
        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fingerSelect();
                //If assistive mode, write to peripheral for assistive exercises, otherwise user completed exercise
                sumOfBits=thumb_bit+index_bit+middle_bit+ring_bit+pinky_bit;
                if(mode) {
                    done_app = false;
                    write_to_app = true;
                }
                progressPopup();
            }
        });

        //Preset exercise list
        exerciseList.add("Single Finger - Thumb");
        exerciseList.add("Single Finger - Index");
        exerciseList.add("Single Finger - Middle");
        exerciseList.add("Single Finger - Ring");
        exerciseList.add("Single Finger - Pinky");
        exerciseList.add("Two Fingers- Index/Middle");
        exerciseList.add("Two Fingers- Ring/Pinky");
        exerciseList.add("Closed Hand");
        exerciseList.add("Claw");
        exerciseList.add("Pinch");

        //Preset exercise data
        //Data stored as: position, speed, pinkybit, ringbit, middlebit, pointerbit, thumbbit
        exerciseData = new int[100][];
        exerciseData[0]= new int[]{100,50,0,0,0,0,1};
        exerciseData[1]= new int[]{100,50,0,0,0,1,0};
        exerciseData[2]= new int[]{100,50,0,0,1,0,0};
        exerciseData[3]= new int[]{100,50,0,1,0,0,0};
        exerciseData[4]= new int[]{100,50,1,0,0,0,0};
        exerciseData[5]= new int[]{100,50,0,0,1,1,0};
        exerciseData[6]= new int[]{100,50,1,1,0,0,0};
        exerciseData[7]= new int[]{100,50,1,1,1,1,1};
        exerciseData[8]= new int[]{50,50,1,1,1,1,1};
        exerciseData[9]= new int[]{75,50,0,0,0,1,1};

        //Creates select menu for exercises
        exerciseSelect = (Spinner) findViewById(R.id.saved_select);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,exerciseList);
        exerciseSelect.setAdapter(adapter);
        exerciseSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            //On a exercise selection, load saved data
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                //Finger bit select and setting UI elements
                if(exerciseData[position][6]==1) {
                    thumbButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    thumb_bit=1;
                }else {
                    thumbButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    thumb_bit=0;
                }

                if(exerciseData[position][5]==1) {
                    indexButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    index_bit=1;
                }else {
                    indexButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    index_bit=0;
                }

                if(exerciseData[position][4]==1) {
                    middleButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    middle_bit=1;
                }else {
                    middleButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    middle_bit=0;
                }

                if(exerciseData[position][3]==1) {
                    ringButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    ring_bit=1;
                }else {
                    ringButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    ring_bit=0;
                }

                if(exerciseData[position][2]==1) {
                    pinkyButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    pinky_bit=1;
                }else {
                    pinkyButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    pinky_bit=0;
                }

                //Position and speed values and UI set
                speedBar.setProgress(exerciseData[position][1]);
                spdTxt.setText(valueOf(exerciseData[position][1])+"%");
                speed_cont = exerciseData[position][1];

                positionBar.setProgress(exerciseData[position][0]);
                posTxt.setText(valueOf(exerciseData[position][0])+"%");
                pos_cont = exerciseData[position][0];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                //Do nothing when nothing
            }

        });


        //Button to save current exercise to list
        customExerciseName = (EditText) findViewById(R.id.custom_exercise_name);
        saveButton = (Button) findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(exerciseList.contains(String.valueOf(customExerciseName.getText()))){ //If user tries name an exercise that already exists, notify them
                    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                    alert.setTitle("Alert!");
                    alert.setMessage("An exercise already exists with this name. Please rename the exercise before saving it again.");
                    alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    });
                    alert.show();
                }else{ //If a new exercise, save its data
                    exerciseList.add(String.valueOf(customExerciseName.getText()));
                    exerciseData[arrayCount]= new int[]{pos_cont,speed_cont,pinky_bit,ring_bit,middle_bit,index_bit,thumb_bit};
                    arrayCount++;
                }
            }
        });

        //Initializes a Bluetooth adapter.
        bleCheck();
        locationCheck();
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();


        //A progress dialog will be needed while the connection process is taking place
        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setCancelable(false);

    }

    //Progress popup to prevent user input when in an exercise
    public void progressPopup(){
        dialogBuilder = new AlertDialog.Builder(this,android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        final View exerciseProgress = getLayoutInflater().inflate(R.layout.exercise_in_progress, null);

        dialogBuilder.setView(exerciseProgress);
        dialog = dialogBuilder.create();
        dialog.show();
        thumbBar = (ProgressBar) exerciseProgress.findViewById(R.id.thumb_progress_bar);
        indexBar = (ProgressBar) exerciseProgress.findViewById(R.id.index_progress_bar);
        middleBar = (ProgressBar) exerciseProgress.findViewById(R.id.middle_progress_bar);
        ringBar = (ProgressBar) exerciseProgress.findViewById(R.id.ring_progress_bar);
        pinkyBar = (ProgressBar) exerciseProgress.findViewById(R.id.pinky_progress_bar);
        percThumb = (TextView) exerciseProgress.findViewById(R.id.thumb_percent_txt);
        percIndex = (TextView) exerciseProgress.findViewById(R.id.index_percent_txt);
        percMiddle = (TextView) exerciseProgress.findViewById(R.id.middle_percent_txt);
        percRing = (TextView) exerciseProgress.findViewById(R.id.ring_percent_txt);
        percPinky = (TextView) exerciseProgress.findViewById(R.id.pinky_percent_txt);
        updateFlexUI=true;

        /* IF I WANT TO IMPLEMENT AUTO COMPLETE, MAKE THIS ITS OWN FUNCTION AND CHECK IN BLE HANDLER. ALSO ADD A DONE CHECK FOR ASSISTED AUTOCOMPLETE FROM PERPH
        //Code for mode completion
        if(!mode){
            double target = pos_cont*(thumb_bit+index_bit+middle_bit+ring_bit+pinky_bit); //target total for exercise completion
            double sum = 0;
            if(thumb_bit==1)
                sum+=thumb_pos;
            if(index_bit==1)
                sum+=index_pos;
            if(middle_bit==1)
                sum+=middle_pos;
            if(ring_bit==1)
                sum+=ring_pos;
            if(pinky_bit==1)
                sum+=pinky_pos;

            if(sum>=target) {
                dialog.dismiss();
            }else {
                sum = 0;
            }
        }
         */

        //Button to write to microcontroller and complete an exercise
        cancelButton = (Button) exerciseProgress.findViewById(R.id.cancel_exercise);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
/*                temp_mot=motor_cont;
                temp_spd=speed_cont;
                temp_pos=pos_cont;*/
                motor_cont = 32;
                speed_cont = 0;
                pos_cont = 0;
                done_app = true;
                write_to_app = true;
                //Sleep for 1 seconds to write 0s to motor code
                try{
                    Thread.sleep(1000);
                }
                catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                }
                write_to_app = false;
                updateFlexUI=false;
                motor_cont = temp_mot;
                speed_cont = temp_spd;
                pos_cont = temp_pos;


                thumbButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                indexButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                middleButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                ringButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                pinkyButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);


                thumb_bit=1;
                //Position and speed values and UI set
                speedBar.setProgress(50);
                spdTxt.setText(valueOf(50)+"%");
                speed_cont = 50;

                positionBar.setProgress(100);
                posTxt.setText(valueOf(100)+"%");
                pos_cont = 100;
                dialog.dismiss();
            }
        });
    }


    public void fingerSelect(){
        String binaryFingers = String.valueOf(pinky_bit)+String.valueOf(ring_bit)+String.valueOf(middle_bit)+String.valueOf(index_bit)+String.valueOf(thumb_bit);
        motor_cont=Integer.parseInt(binaryFingers,2); ;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Use this check to determine whether BLE is supported on the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        //Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

    }

    //IF SUPPRESSION IS ADDED HERE THE APP CRASHES
    @SuppressLint("MissingPermission")
    @Override
    protected void onPause() {
        super.onPause();
        //Make sure dialog is hidden
        mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onStop() {
        super.onStop();
        //Disconnect from any active tag connection
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    private void bleCheck() {
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // Bluetooth permission has not been granted.
            ActivityCompat.requestPermissions(this,new String[]{BLUETOOTH},REQUEST_BLUETOOTH_ID);
        }
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            // Bluetooth admin permission has not been granted.
            ActivityCompat.requestPermissions(this, new String[]{BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_ADMIN_ID);
        }
    }

    private void locationCheck() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Location permission has not been granted.
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, REQUEST_LOCATION_ID);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);
        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan: //Clears device list and rescans if scan is clicked again
                mDevices.clear();
                startScan();
                return true;
            default: //Any other button will connect to that service
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to "+device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                //Display progress UI
                //mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to "+device.getName()+"..."));
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDisplayValues() {
        mThumb.setText("---");
        mIndex.setText("---");
        mMiddle.setText("---");
        mRing.setText("---");
        mPinky.setText("---");
    }


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    @SuppressLint("MissingPermission")
    private void startScan() {
        mBluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500); //Scam lasts 2.5 seconds
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    /* BluetoothAdapter.LeScanCallback */
    @SuppressLint("MissingPermission")
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        /*
         * We are looking for RehabGlove devices only, so validate the name
         * that each device reports before adding it to our collection
         */
        if (DEVICE_NAME.equals(device.getName())) {
            mDevices.put(device.hashCode(), device);
            //Update the overflow menu
            invalidateOptionsMenu();
        }
    }

    /*
     * In this callback, we create a state machine to enforce that only
     * one characteristic be read or written at a time
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;
        private void reset() { mState = 0; }
        private void advance() { mState++; }

        /* NOT NEEDED AS SENSORS ARE READ ONLY
         * Send an enable command to each sensor by writing a configuration
         * characteristic.  This is specific to the SensorTag to keep power
         * low by disabling sensors you aren't using.

        private void enableNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Enabling pressure cal");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x02});
                    break;
                case 1:
                    Log.d(TAG, "Enabling pressure");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x01});
                    break;
                case 2:
                    Log.d(TAG, "Enabling humidity");
                    characteristic = gatt.getService(HUMIDITY_SERVICE)
                            .getCharacteristic(HUMIDITY_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x01});
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.writeCharacteristic(characteristic);
        }
        */

        //Read the data characteristic's value for each sensor explicitly
        @SuppressLint("MissingPermission")
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Reading Thumb Measurement");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(THUMB_MEASUREMENT);
                    break;
                case 1:
                    Log.d(TAG, "Reading Index Measurement");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(INDEX_MEASUREMENT);
                    break;
                case 2:
                    Log.d(TAG, "Reading Middle Measurement");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(MIDDLE_MEASUREMENT);
                    break;
                case 3:
                    Log.d(TAG, "Reading Ring Measurement");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(RING_MEASUREMENT);
                    break;
                case 4:
                    Log.d(TAG, "Reading Pinky Measurement");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(PINKY_MEASUREMENT);
                    break;
                case 5:
                    Log.d(TAG, "Reading Done From Perf");
                    characteristic = gatt.getService(READ_SERVICE)
                            .getCharacteristic(DONE_FROM_PERIPHERAL);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "Read Sweep Complete");
                    return;
            }

            gatt.readCharacteristic(characteristic);
        }
        //private int motor_cont, speed_cont, pos_cont;
        //private boolean done_app, write_to_app=false
        //Occurs after full read sweep once write_to_app flag is set true
        @SuppressLint("MissingPermission")
        private void writeNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            byte[] value = new byte[1];

            switch (mState) {
                case 6:
                    characteristic = gatt.getService(WRITE_SERVICE)
                            .getCharacteristic(MOTOR_CONTROL);
                    value[0] = (byte) (motor_cont);
                    Log.i(TAG, "Write Motor:");
                    break;
                case 7:
                    characteristic = gatt.getService(WRITE_SERVICE)
                            .getCharacteristic(SPEED_CONTROL);
                    value[0] = (byte) (speed_cont & 0xFF);
                    Log.i(TAG, "Write Speed:");
                    break;
                case 8:
                    characteristic = gatt.getService(WRITE_SERVICE)
                            .getCharacteristic(POSITION_CONTROL);
                    value[0] = (byte) (pos_cont & 0xFF);
                    Log.i(TAG, "Write Pos:");
                    break;
                case 9:
                    characteristic = gatt.getService(WRITE_SERVICE)
                            .getCharacteristic(DONE_FROM_APP);
                    value[0] = (byte) ((done_app ? 1 : 0 ) & 0xFF);
                    Log.i(TAG, "Write Done:");
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "Write Sweep Complete");
                    return;
            }


            boolean status1 = false;

            //value[0] = (byte) (64 & 0xFF);
            characteristic.setValue(value);
            status1 = gatt.writeCharacteristic(characteristic);

            Log.i(TAG, "Write Val: "+Integer.toString(value[0])+" sucess?= "+status1);

        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                //mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the values
                 * out of the UI
                 */
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered: "+status);
            //mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            reset();
            readNextSensor(gatt);
        }

        public void startSensorRead(){

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            //For each read, pass the data up to the UI thread to update the display
            if (THUMB_MEASUREMENT.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_THUMB, characteristic));
            }
            if (INDEX_MEASUREMENT.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_INDEX, characteristic));
            }
            if (MIDDLE_MEASUREMENT.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_MIDDLE, characteristic));
            }
            if (RING_MEASUREMENT.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_RING, characteristic));
            }
            if (PINKY_MEASUREMENT.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PINKY, characteristic));
            }
            if (DONE_FROM_PERIPHERAL.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_DONE, characteristic));
            }



            if(DONE_FROM_PERIPHERAL.equals(characteristic.getUuid()) && !write_to_app) { //If at the end of a read, and write flag not set
                //Log.w(TAG, "STATE IF:"+ String.valueOf(mState));
                reset();
                readNextSensor(gatt);
            }else if(DONE_FROM_PERIPHERAL.equals(characteristic.getUuid()) && write_to_app){ //If at the end of a read, and write flag set
                advance();
                writeNextSensor(gatt);
            }else{ //Still reading
                //Log.w(TAG, "STATE ELSE:"+String.valueOf(mState));
                advance();
                readNextSensor(gatt);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if(DONE_FROM_APP.equals(characteristic.getUuid())) { //If at the end of a write, return to read
                reset();
                readNextSensor(gatt);
                write_to_app = !write_to_app;
            }else{ //Still reading
                //Log.w(TAG, "UUID: "+String.valueOf(characteristic.getUuid()));
                advance();
                writeNextSensor(gatt);
            }
        }

    /*
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.

            if (HUMIDITY_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_HUMIDITY, characteristic));
            }
            if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
            }
            if (PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE_CAL, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
            advance();
            enableNextSensor(gatt);
        }
        */

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return valueOf(status);
            }
        }
    };


    // We have a Handler to process event results on the main thread
    public static final int MSG_THUMB = 101;
    public static final int MSG_INDEX = 102;
    public static final int MSG_MIDDLE = 103;
    public static final int MSG_RING= 104;
    public static final int MSG_PINKY = 105;
    private static final int MSG_DONE = 106;

    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_THUMB:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining thumb value");
                        return;
                    }
                    Log.w(TAG, "Thumb Reading: ");
                    updateFlexValue(characteristic, msg);
                    break;
                case MSG_INDEX:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining index value");
                        return;
                    }
                    Log.w(TAG, "Index Reading: ");
                    updateFlexValue(characteristic, msg);
                    break;
                case MSG_MIDDLE:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining middle value");
                        return;
                    }
                    Log.w(TAG, "Middle Reading: ");
                    updateFlexValue(characteristic, msg);
                    break;
                case MSG_RING:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining ring value");
                        return;
                    }
                    Log.w(TAG, "Ring Reading: ");
                    updateFlexValue(characteristic, msg);
                    break;
                case MSG_PINKY:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining pinky value");
                        return;
                    }
                    Log.w(TAG, "Pinky Reading: ");
                    updateFlexValue(characteristic, msg);
                    break;
                case MSG_DONE:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining done value");
                        return;
                    }
                    Log.w(TAG, "Done Reading: ");
                    //Need to import to get this to work
                    //done_perph = Boolean.parseBoolean(String(characteristic.getValue(), StandardCharsets.UTF_8));
                    break;


                case MSG_PROGRESS:
                    mProgress.setMessage((String) msg.obj);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };


    /* Methods to extract sensor data and update the UI */
    private void updateFlexValue(BluetoothGattCharacteristic characteristic, Message msg) {
        double reading = FlexSensorCalc.calculatePosition(characteristic);
        String progress = FlexSensorCalc.calculateprogress(msg, reading);
        int progInt = 0;
        if(progress.length() != 0)
            progInt = Integer.parseInt((progress.substring(0, progress.length() - 1)));


        String s = String.format("%f",reading);

        switch (msg.what) {
            case MSG_THUMB:
                thumb_pos = reading;
                mThumb.setText(s);

                if((updateFlexUI)&&(thumb_bit==1 ? true : false)) {
                    if(progInt<pos_cont) {
                        thumbBar.setProgress(progInt);
                        percThumb.setText(progress);
                    }else {
                        percThumb.setText("Done");
                        thumbBar.setProgress(100);
                        thumb_bit=0;
                        //temT=1;
                        fingerSelect();
                        write_to_app=true;
                        //checkToEnd();
                    }

                    //percThumb.setText(progress);
                }

                break;
            case MSG_INDEX:
                index_pos = reading;
                mIndex.setText(s);
                if((updateFlexUI) && (index_bit==1 ? true : false)) {
                    if(progInt<pos_cont) {
                        indexBar.setProgress(progInt);
                        percIndex.setText(progress);
                    }else {
                        percIndex.setText("Done");
                        indexBar.setProgress(100);
                        index_bit=0;
                        //temI=1;
                        fingerSelect();
                        write_to_app=true;
                        //checkToEnd();
                    }

                    //percIndex.setText(progress);
                    //indexBar.setProgress((int) reading);
                    //percIndex.setText(String.format("%f.2",reading)+"%");
                    //if((int)reading/10>=100)
                        //percIndex.setText(String.format("Done"));
                }
                break;
            case MSG_MIDDLE:
                middle_pos = reading;
                mMiddle.setText(s);
                if((updateFlexUI) && (middle_bit==1 ? true : false)) {
                    if(progInt<pos_cont) {
                        percMiddle.setText(progress);
                        middleBar.setProgress(progInt);
                    }else {
                        percMiddle.setText("Done");
                        middleBar.setProgress(100);
                        middle_bit=0;
                        //temM=1;
                        fingerSelect();
                        write_to_app=true;
                        //checkToEnd();
                    }
                    //percMiddle.setText(progress);
                    //middleBar.setProgress((int) reading);
                    //percMiddle.setText(String.format("%f.2",reading)+"%");
                    //if((int)reading/10>=100)
                        //percMiddle.setText(String.format("Done"));
                }
                break;
            case MSG_RING:
                ring_pos = reading;
                mRing.setText(s);
                if((updateFlexUI) && (ring_bit==1 ? true : false)) {
                    if(progInt<pos_cont) {
                        ringBar.setProgress(progInt);
                        percRing.setText(progress);
                    }else {
                        percRing.setText("Done");
                        ringBar.setProgress(100);
                        ring_bit=0;
                        //temR=1;
                        fingerSelect();
                        write_to_app=true;
                        //checkToEnd();
                    }

                    //percRing.setText(progress);
                    //ringBar.setProgress((int) reading );
                    //percRing.setText(String.format("%f.2",reading)+"%");
                    //if((int)reading/10>=100)
                        //percRing.setText(String.format("Done"));
                }
                break;
            case MSG_PINKY:
                pinky_pos = reading;
                mPinky.setText(s);
                if((updateFlexUI) && (pinky_bit==1 ? true : false)) {
                    if(progInt<pos_cont) {
                        pinkyBar.setProgress(progInt);
                        percPinky.setText(progress);
                    }else {
                        percPinky.setText("Done");
                        pinkyBar.setProgress(100);
                        pinky_bit=0;
                        //temP=1;
                        fingerSelect();
                        write_to_app=true;
                        //checkToEnd();
                    }

                    //percPinky.setText(progress);
                    //pinkyBar.setProgress((int) reading);
                    //percPinky.setText(String.format("%f.2", reading) + "%");
                    //if((int)reading/10>=100)
                    //percRing.setText(String.format("Done"));
                }
                break;
        }
    }

/*
    public void checkToEnd(){
        if((thumbBar.getProgress()+indexBar.getProgress()+middleBar.getProgress()+ringBar.getProgress()+pinkyBar.getProgress())/100>=sumOfBits){
            String binaryFingers = String.valueOf(temP)+String.valueOf(temR)+String.valueOf(temM)+String.valueOf(temI)+String.valueOf(temT);
            temp_mot=Integer.parseInt(binaryFingers,2);
            temp_pos = pos_cont;
            temp_spd = speed_cont;
            motor_cont = 0;
            speed_cont = 0;
            pos_cont = 0;
            done_app = true;
            write_to_app = true;
            //Sleep for 1 seconds to write 0s to motor code
            try{
                Thread.sleep(1000);
            }
            catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }
            temT=temI=temR=temM=temP=0;
            motor_cont = temp_mot;
            speed_cont = temp_spd;
            pos_cont = temp_pos;
            write_to_app = false;
            updateFlexUI=false;
            //dialog.dismiss();
        }
    }
    */

/*
    private void updateHumidityValues(BluetoothGattCharacteristic characteristic) {
        double humidity = SensorTagData.extractHumidity(characteristic);

        mHumidity.setText(String.format("%.0f%%", humidity));
    }

    private int[] mPressureCals;
    private void updatePressureCals(BluetoothGattCharacteristic characteristic) {
        mPressureCals = SensorTagData.extractCalibrationCoefficients(characteristic);
    }

    private void updatePressureValue(BluetoothGattCharacteristic characteristic) {
        if (mPressureCals == null) return;
        double pressure = SensorTagData.extractBarometer(characteristic, mPressureCals);
        double temp = SensorTagData.extractBarTemperature(characteristic, mPressureCals);

        mTemperature.setText(String.format("%.1f\u00B0C", temp));
        mPressure.setText(String.format("%.2f", pressure));
    }
 */
}



