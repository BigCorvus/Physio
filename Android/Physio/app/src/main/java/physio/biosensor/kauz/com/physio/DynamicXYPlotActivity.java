/*
 * Copyright 2012 AndroidPlot.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package physio.biosensor.kauz.com.physio;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.util.PlotStatistics;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYAxisType;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import at.abraxas.amarino.Amarino;
import at.abraxas.amarino.AmarinoIntent;

// Monitor the phone's orientation sensor and plot the resulting azimuth pitch and roll values.
// See: http://developer.android.com/reference/android/hardware/SensorEvent.html
public class DynamicXYPlotActivity extends Activity
{
    //sampling rates
    static private final int FsP=60;
    static private final int FsE=125;
    static private final int FsR=20;
    static private final int XaxisSecP=5;
    static private final int XaxisSecE=5;
    static private final int XaxisSecR=20;
    boolean recordEnable = false;
    PowerManager.WakeLock wL;

    boolean measuringRR = false;
    // private static final String TAG = "SensorGraph";

    // change this to your Bluetooth device address -1th: BILEX HC-05 "EEG2",
    // 0th: BILEX HC05 "EEG", 1st: HC05 on board (black box), 2nd:
    // RN42 3rd: no idea
    // get a variable from UserInput class
    public static String DEVICE_ADDRESS = null;// "20:13:04:23:03:56";//"EEG2"//"00:06:66:45:0F:3E";//RN42//"00:12:09:20:02:98";//EEG
    private ArduinoReceiver data = new ArduinoReceiver();

    File outputFile; //for recording

    private static final int REQUEST_WRITE_STORAGE = 112;

    private static final int HISTORY_SIZE_E = FsE * XaxisSecE;            // number of points to plot in history for ECG
    private static final int HISTORY_SIZE_R = FsR * XaxisSecR;            // respiration et al
    private static final int HISTORY_SIZE_P = FsP * XaxisSecP;              //pulse oximeter
    private SensorManager sensorMgr = null;
    private Sensor orSensor = null;

    //private XYPlot aprLevelsPlot = null;
    private XYPlot aprHistoryPlot = null;
    private XYPlot aprHistoryPlot2 = null;
    private XYPlot aprHistoryPlot3 = null;

    private CheckBox hwAcceleratedCb;
    private CheckBox showFpsCb;
    private CheckBox recordingCb;
    //private SimpleXYSeries aprLevelsSeries = null;

    String Channel = "GSR";
    //private SimpleXYSeries CH1Series;
    // private SimpleXYSeries CH2Series;
    private TextView mValueTV;
    private TextView HR_TV;
    private TextView SPO2_TV;
    private TextView Resp_TV;
    private TextView RR_TV;
    private TextView Temp_TV;
    List<String> chanList;
    private Spinner G4spinner;

    private SimpleXYSeries CH1HistorySeries = null;
    private SimpleXYSeries CH2HistorySeries = null;
    private SimpleXYSeries CH3HistorySeries = null;

    private Redrawer redrawer;
    private Redrawer redrawer2;
    private Redrawer redrawer3;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //reload my activity with permission granted or use the features what required the permission
                } else
                {
                    Toast.makeText(DynamicXYPlotActivity.this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }

    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
//ask for permission to write to storage
        boolean hasPermission = (ContextCompat.checkSelfPermission(DynamicXYPlotActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(DynamicXYPlotActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }

        // #78 wake-lock to keep that open!
        PowerManager pM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wL = pM.newWakeLock(PowerManager.FULL_WAKE_LOCK, "tag whatever");
        super.onCreate(savedInstanceState);
        wL.acquire(); // can also be put into onResume() method
        // receive Intent with BT-device extra from DiscoveryActivity and decode
        // device name and address of the found module
        Intent fromDiscovery = getIntent();
        final BluetoothDevice device = fromDiscovery
                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); // get the
        // struct
        // type
        // thingy.
        String deviceName = device.getName();
        String deviceAddress = device.getAddress();



        DEVICE_ADDRESS = deviceAddress;


        setContentView(R.layout.dynamicxyplot_example);

/*

        // setup the APR Levels plot:
        aprLevelsPlot = (XYPlot) findViewById(R.id.aprLevelsPlot);
        aprLevelsPlot.setDomainBoundaries(0, 1024, BoundaryMode.FIXED);
        aprLevelsPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.TRANSPARENT);


*/
        //CH1Series = new SimpleXYSeries("CH1");
        // CH2Series = new SimpleXYSeries("CH2");



/*
        aprLevelsPlot.addSeries(CH1Series,
                new BarFormatter(Color.rgb(0, 100, 200), Color.rgb(10, 80, 0)));
        aprLevelsPlot.addSeries(CH2Series,
                new BarFormatter(Color.rgb(100, 0, 200), Color.rgb(10, 80, 0)));

        aprLevelsPlot.setDomainStepValue(3);
        aprLevelsPlot.setTicksPerRangeLabel(3);

        // per the android documentation, the minimum and maximum readings we can get from
        // any of the orientation sensors is -180 and 359 respectively so we will fix our plot's
        // boundaries to those values.  If we did not do this, the plot would auto-range which
        // can be visually confusing in the case of dynamic plots.
        aprLevelsPlot.setRangeBoundaries(0, 1024, BoundaryMode.FIXED);

        // update our domain and range axis labels:
        aprLevelsPlot.setDomainLabel("");
        aprLevelsPlot.getDomainLabelWidget().pack();
        aprLevelsPlot.setRangeLabel("Amplitude");
        aprLevelsPlot.getRangeLabelWidget().pack();
        aprLevelsPlot.setGridPadding(15, 0, 15, 0);
        aprLevelsPlot.setRangeValueFormat(new DecimalFormat("#"));
*/
        // setup the APR History plot:
        aprHistoryPlot = (XYPlot) findViewById(R.id.aprHistoryPlot);
       aprHistoryPlot2 = (XYPlot) findViewById(R.id.aprHistoryPlot2);
        aprHistoryPlot3 = (XYPlot) findViewById(R.id.aprHistoryPlot3);


        HR_TV = (TextView) findViewById(R.id.tvHR);
        SPO2_TV = (TextView) findViewById(R.id.tvSPO2);
        Resp_TV = (TextView) findViewById(R.id.tvRespRate);
        Temp_TV = (TextView) findViewById(R.id.tvTemp);
        RR_TV = (TextView) findViewById(R.id.tvRR);

        //set up channel selection spinner
        G4spinner = (Spinner) findViewById(R.id.spinnerG4);
        chanList = new ArrayList<String>();
        chanList.add("RES");
        chanList.add("GSR");
        chanList.add("T");
        chanList.add("AX");
        chanList.add("AY");
        chanList.add("AZ");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, chanList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        G4spinner.setAdapter(dataAdapter);
        //G4spinner.setRotation(-90);

        G4spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Channel=parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Channel="GSR";
            }
        });

        //set text colors
        HR_TV.setTextColor(0xFFFF0000);
        SPO2_TV.setTextColor(0xFF00FF00);
        Resp_TV.setTextColor(0xFF000000);
        Temp_TV.setTextColor(0xFFFF00FF);
        RR_TV.setTextColor(0xFF0000FF);


        CH1HistorySeries = new SimpleXYSeries("Resp");
        CH1HistorySeries.useImplicitXVals();
        CH2HistorySeries = new SimpleXYSeries("ECG");
        CH2HistorySeries.useImplicitXVals();
        CH3HistorySeries = new SimpleXYSeries("Photopleth.");
        CH3HistorySeries.useImplicitXVals();

        aprHistoryPlot.setRangeBoundaries(0, 16777216, BoundaryMode.AUTO);
        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE_R, BoundaryMode.FIXED);
        aprHistoryPlot2.setRangeBoundaries(0, 16777216, BoundaryMode.AUTO);
        aprHistoryPlot2.setDomainBoundaries(0, HISTORY_SIZE_E, BoundaryMode.FIXED);
        aprHistoryPlot3.setRangeBoundaries(0, 255, BoundaryMode.AUTO);
        aprHistoryPlot3.setDomainBoundaries(0, HISTORY_SIZE_P, BoundaryMode.FIXED);

        aprHistoryPlot.addSeries(CH1HistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(200, 200, 200), null, null, null));
       aprHistoryPlot2.addSeries(CH2HistorySeries,
                new LineAndPointFormatter(
                       Color.rgb(200, 200, 0), null, null, null));
        aprHistoryPlot3.addSeries(CH3HistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(200, 0, 0), null, null, null));


        aprHistoryPlot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        aprHistoryPlot.setDomainStepValue(HISTORY_SIZE_R / XaxisSecR);
       // aprHistoryPlot.setTicksPerRangeLabel(3);
        //aprHistoryPlot.setDomainLabel("Sample Index");
        //aprHistoryPlot.getDomainLabelWidget().pack();
        //aprHistoryPlot.setRangeLabel("Amplitude");
       // aprHistoryPlot.getRangeLabelWidget().pack();
        aprHistoryPlot.getGraphWidget().getDomainTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot.getGraphWidget().getDomainOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot.getGraphWidget().getRangeOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot.getGraphWidget().getRangeTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot.getGraphWidget().getRangeGridLinePaint().setColor(Color.TRANSPARENT);

        aprHistoryPlot.setRangeValueFormat(new DecimalFormat("#"));
        aprHistoryPlot.setDomainValueFormat(new DecimalFormat("#"));

        aprHistoryPlot2.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        aprHistoryPlot2.setDomainStepValue(HISTORY_SIZE_E/XaxisSecE);
       // aprHistoryPlot2.setTicksPerRangeLabel(3);
        //aprHistoryPlot2.setDomainLabel("Sample Index");
        //aprHistoryPlot2.getDomainLabelWidget().pack();
        //aprHistoryPlot2.setRangeLabel("Amplitude");
        //aprHistoryPlot2.getRangeLabelWidget().pack();
        aprHistoryPlot2.getGraphWidget().getDomainTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot2.getGraphWidget().getDomainOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot2.getGraphWidget().getRangeOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot2.getGraphWidget().getRangeTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot2.getGraphWidget().getRangeGridLinePaint().setColor(Color.TRANSPARENT);

        aprHistoryPlot2.setRangeValueFormat(new DecimalFormat("#"));
        aprHistoryPlot2.setDomainValueFormat(new DecimalFormat("#"));

        aprHistoryPlot3.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        aprHistoryPlot3.setDomainStepValue(HISTORY_SIZE_P/XaxisSecP);
        // aprHistoryPlot2.setTicksPerRangeLabel(3);
        //aprHistoryPlot2.setDomainLabel("Sample Index");
        //aprHistoryPlot2.getDomainLabelWidget().pack();
        //aprHistoryPlot2.setRangeLabel("Amplitude");
        //aprHistoryPlot2.getRangeLabelWidget().pack();
        aprHistoryPlot3.getGraphWidget().getDomainTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot3.getGraphWidget().getDomainOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot3.getGraphWidget().getRangeOriginTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot3.getGraphWidget().getRangeTickLabelPaint().setColor(Color.TRANSPARENT);
        aprHistoryPlot3.getGraphWidget().getRangeGridLinePaint().setColor(Color.TRANSPARENT);

        aprHistoryPlot3.setRangeValueFormat(new DecimalFormat("#"));
        aprHistoryPlot3.setDomainValueFormat(new DecimalFormat("#"));
       // aprHistoryPlot2.removeMarkers();
        // setup checkboxes:
        recordingCb = (CheckBox) findViewById(R.id.recordCb);
        hwAcceleratedCb = (CheckBox) findViewById(R.id.hwAccelerationCb);
        //final PlotStatistics levelStats = new PlotStatistics(1000, false);
        final PlotStatistics histStats = new PlotStatistics(1000, false);
        final PlotStatistics histStats2 = new PlotStatistics(1000, false);
        final PlotStatistics histStats3 = new PlotStatistics(1000, false);

        //aprLevelsPlot.addListener(levelStats);
        aprHistoryPlot.addListener(histStats);
       aprHistoryPlot2.addListener(histStats2);
        aprHistoryPlot3.addListener(histStats3);
        hwAcceleratedCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    //aprLevelsPlot.setLayerType(View.LAYER_TYPE_NONE, null);
                    aprHistoryPlot.setLayerType(View.LAYER_TYPE_NONE, null);
                    aprHistoryPlot2.setLayerType(View.LAYER_TYPE_NONE, null);
                    aprHistoryPlot3.setLayerType(View.LAYER_TYPE_NONE, null);

                } else {
                    //aprLevelsPlot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    aprHistoryPlot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    aprHistoryPlot2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    aprHistoryPlot3.setLayerType(View.LAYER_TYPE_SOFTWARE, null);


                }
            }
        });

        recordingCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    recordEnable = true;

                    //give each new record an adequate name
                    Calendar c = Calendar.getInstance();
                    int seconds = c.get(Calendar.SECOND);
                    int minutes  = c.get(Calendar.MINUTE);
                    int hours = c.get(Calendar.HOUR_OF_DAY);
                    int month = c.get(Calendar.MONTH);
                    int day = c.get(Calendar.DAY_OF_MONTH);
                    int year = c.get(Calendar.YEAR);
                    String filename = "record_" + Integer.toString(day) + Integer.toString(month+1) +  Integer.toString(year) +  "_" + Integer.toString(hours)+  Integer.toString(minutes) +  Integer.toString(seconds)+ ".txt";
                    //String filename = "record_" + Integer.toString(c.get(Calendar.DATE)) +  "_" + Integer.toString(hours)+  Integer.toString(minutes) +  Integer.toString(seconds)+ ".txt";
                    // create a File object for the parent directory
                    File RecordDirectory = new File("/sdcard/Physio/");
// have the object build the directory structure, if needed.
                    RecordDirectory.mkdirs();
                    // create a File object for the output file
                    outputFile = new File(RecordDirectory, filename);

                } else {
                    recordEnable=false;

                }
            }
        });

        showFpsCb = (CheckBox) findViewById(R.id.showFpsCb);
        showFpsCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
               // levelStats.setAnnotatePlotEnabled(b);
                histStats.setAnnotatePlotEnabled(b);
                histStats2.setAnnotatePlotEnabled(b);
                histStats3.setAnnotatePlotEnabled(b);
            }
        });
/*
        // get a ref to the BarRenderer so we can make some changes to it:
        BarRenderer barRenderer = (BarRenderer) aprLevelsPlot.getRenderer(BarRenderer.class);
        if(barRenderer != null) {
            // make our bars a little thicker than the default so they can be seen better:
            barRenderer.setBarWidth(25);
        }
        */
/*
        // register for orientation sensor events:
        sensorMgr = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        for (Sensor sensor : sensorMgr.getSensorList(Sensor.TYPE_ORIENTATION)) {
            if (sensor.getType() == Sensor.TYPE_ORIENTATION) {
                orSensor = sensor;
            }
        }*/
/*
        // if we can't access the orientation sensor then exit:
        if (orSensor == null) {
            System.out.println("Failed to attach to orSensor.");
            cleanup();
        }
*/
        //sensorMgr.registerListener(this, orSensor, SensorManager.SENSOR_DELAY_UI);

            redrawer = new Redrawer(
                Arrays.asList(new Plot[]{aprHistoryPlot}),
                FsR, false);
        redrawer2 = new Redrawer(
                Arrays.asList(new Plot[]{aprHistoryPlot2}),
                FsE, false);
        redrawer3 = new Redrawer(
                Arrays.asList(new Plot[]{aprHistoryPlot3}),
                FsP, false);

    }

    @Override
    public void onResume() {
        wL.acquire(); // can also be put into onResume() method
        super.onResume();
        redrawer.start();
        redrawer2.start();
        redrawer3.start();
    }

    @Override
    public void onPause() {
        wL.release();
        redrawer.pause();
        redrawer2.pause();
        redrawer3.pause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        redrawer.finish();
        redrawer2.finish();
        redrawer3.finish();
        // if you connect in onStart() you must not forget to disconnect when
        // your app is closed
        Amarino.disconnect(this, DEVICE_ADDRESS);

        // do never forget to unregister a registered receiver
        unregisterReceiver(data);
        super.onDestroy();
    }

    private void cleanup() {
        // aunregister with the orientation sensor before exiting:
        //sensorMgr.unregisterListener(this);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // in order to receive broadcasted intents we need to register our
        // receiver
        registerReceiver(data, new IntentFilter(
                AmarinoIntent.ACTION_RECEIVED));

        // this is how you tell Amarino to connect to a specific BT device from
        // within your own code
        Amarino.connect(this, DEVICE_ADDRESS);

    }

    @Override
    protected void onStop() {
        wL.release();
        super.onStop();



    }

    /*
        // Called whenever a new orSensor reading is taken.
        @Override
        public synchronized void onSensorChanged(SensorEvent sensorEvent) {

            // update level data:
            aLvlSeries.setModel(Arrays.asList(
                            new Number[]{sensorEvent.values[0]}),
                    SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

            pLvlSeries.setModel(Arrays.asList(
                            new Number[]{sensorEvent.values[1]}),
                    SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

            rLvlSeries.setModel(Arrays.asList(
                            new Number[]{sensorEvent.values[2]}),
                    SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

            // get rid the oldest sample in history:
            if (rollHistorySeries.size() > HISTORY_SIZE) {
                rollHistorySeries.removeFirst();
                pitchHistorySeries.removeFirst();
                azimuthHistorySeries.removeFirst();
            }

            // add the latest history sample:
            azimuthHistorySeries.addLast(null, sensorEvent.values[0]);
            pitchHistorySeries.addLast(null, sensorEvent.values[1]);
            rollHistorySeries.addLast(null, sensorEvent.values[2]);
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            // Not interested in this event
        }
        */
    //******************************************************************************************
    class ArduinoReceiver extends BroadcastReceiver {
        private int sensorReading;
        private int sensorReading2;


        @Override
        public void onReceive(Context context, Intent intent) {
             String data = null;

            // the device address from which the data was sent, we don't need it
            // here but to demonstrate how you retrieve it
            final String address = intent
                    .getStringExtra(AmarinoIntent.EXTRA_DEVICE_ADDRESS);

            // the type of data which is added to the intent
            final int dataType = intent.getIntExtra(
                    AmarinoIntent.EXTRA_DATA_TYPE, -1);


            // we only expect String data though, but it is better to check if
            // really string was sent
            // later Amarino will support differnt data types, so far data comes
            // always as string and
            // you have to parse the data to the type you have sent from
            // Arduino, like it is shown below
            if (dataType == AmarinoIntent.STRING_EXTRA) {
                data = intent.getStringExtra(AmarinoIntent.EXTRA_DATA);

                if (data != null) {
                    // mValueTV.setText(data);
                    try {
                        // we get data with a delimiter
                        String patternStr = ";"; // delimiter value
                        String[] fields = data.split(patternStr);// array where
                        // values
                        // will be
                        // stored.
                        // since we know that our string value is an int number
                        // we can parse it to an integer


                        if(fields[0].substring(0, 1).equals("R"))
                        {
                            //final RR values
                            RR_TV.setText(fields[0].substring(1) + " / " + fields[1]+" mmHg");
                            at.abraxas.amarino.Amarino.sendDataToArduino(DynamicXYPlotActivity.this,DEVICE_ADDRESS,'b',"RR"); //turn the RR monitor off so that no junk data comes
                            measuringRR=false;
                        } else if(fields[0].substring(0, 1).equals("r")){
                            //RR pressure curves
                            measuringRR=true;
                            final int RRWave = Integer.parseInt(fields[1]);
                            CH3HistorySeries.addLast(null,  RRWave);
                            // final int RRcuffPress = Integer.parseInt(fields[1]);
                            //mGraph4.addDataPoint(RRcuffPress);
                            RR_TV.setText(fields[0].substring(1));
                        } else if(fields[0].substring(0, 1).equals("P")){
                            //pulse Oximeter stuff
                            final int pulseOxWave = Integer.parseInt(fields[0].substring(1));
                            if(!measuringRR)
                            CH3HistorySeries.addLast(null,  pulseOxWave);

                            SPO2_TV.setText(fields[2] + " %");
                            HR_TV.setText(fields[1] + " bpm");


                        }else if(fields[0].substring(0, 1).equals("G")){
                            if(Channel.equals("GSR")) {
                                final int GSR = Integer.parseInt(fields[0].substring(1));
                                CH1HistorySeries.addLast(null,  GSR);
                            }else if(Channel.equals("RES")) {
                                final int Resp = Integer.parseInt(fields[1]);
                                CH1HistorySeries.addLast(null,  Resp);
                            }else if(Channel.equals("AX")){
                                final int accX = Integer.parseInt(fields[2]);
                                CH1HistorySeries.addLast(null,  accX);
                            }else  if(Channel.equals("AY")){
                                final int accY = Integer.parseInt(fields[3]);
                                CH1HistorySeries.addLast(null,  accY);
                            }else  if(Channel.equals("AZ")){
                                final int accZ = Integer.parseInt(fields[4]);
                                CH1HistorySeries.addLast(null,  accZ);
                            }
                        }else if(fields[0].substring(0, 1).equals("E")){
                            final int ECG = Integer.parseInt(fields[0].substring(1));
                            CH2HistorySeries.addLast(null,  ECG);
                        }else if(fields[0].substring(0, 1).equals("T")){
                            final float Temp = Float.parseFloat(fields[0].substring(1));
                            if(Channel.equals("T"))
                                CH1HistorySeries.addLast(null,  Temp);
                            Temp_TV.setText(fields[0].substring(1)+" °C");

                        }

                       // sensorReading = Integer.parseInt(fields[0]);
                        // pass an int to the GraphView object
                        // mGraph.addDataPoint(sensorReading);

                       // sensorReading2 = Integer.parseInt(fields[1]);
                        // pass an int to the GraphView object
                        // mGraph2.addDataPoint(sensorReading2);
                        // update level data:
                        /*
                        CH1Series.setModel(Arrays.asList(
                                        new Number[]{ sensorReading}),
                                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

                        CH2Series.setModel(Arrays.asList(
                                        new Number[]{ sensorReading2}),
                                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

*/

                        // get rid the oldest sample in history:
                        if (CH1HistorySeries.size() > HISTORY_SIZE_R) {
                            CH1HistorySeries.removeFirst();


                        }

                        if (CH2HistorySeries.size() > HISTORY_SIZE_E) {
                            CH2HistorySeries.removeFirst();
                        }

                        if (CH3HistorySeries.size() > HISTORY_SIZE_P) {
                            CH3HistorySeries.removeFirst();
                        }
                        // add the latest history sample:
                        //CH1HistorySeries.addLast(null,  ECG);
                        //CH2HistorySeries.addLast(null,  sensorReading2+1024);

                        if (recordEnable == true) {
// writing to a textfile
                           record(data);
                        }

                    } catch (NumberFormatException e) { /*
														 * oh data was not an
														 * integer
														 */
                    }

                }
            }
        }

    }

    public void record(String str) {
        class OneShotTask implements Runnable {
            String str;
            OneShotTask(String s) { str = s; }
            public void run() {
                try {
                    FileWriter testwriter = new FileWriter(
                            outputFile,
                            true);
                    BufferedWriter out = new BufferedWriter(testwriter);
                    out.write(str);
                    out.newLine();

                    out.close();
                    testwriter.close();

                } catch (IOException e) {
                    // ...do nothing right now (catch clause for FileWriter)
                }
            }
        }
        Thread t = new Thread(new OneShotTask(str));
        t.start();
    }
    /* switch to BT search activity when button pressed */
    public void onRRclicked(View view) {
        at.abraxas.amarino.Amarino.sendDataToArduino(DynamicXYPlotActivity.this,DEVICE_ADDRESS,'b',"RR");

    }

}




