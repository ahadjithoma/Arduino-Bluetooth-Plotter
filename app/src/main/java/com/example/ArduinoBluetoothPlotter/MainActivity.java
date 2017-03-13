package com.example.ArduinoBluetoothPlotter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries.GraphViewStyle;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphView.LegendAlign;

public class MainActivity extends Activity implements View.OnClickListener{

    int flag = 0; // used to avoid the first input msg
  TextView  txtStringLength, sensorView;
  Handler bluetoothIn;
  final int handlerState = 0;        				 //used to identify handler message
  private BluetoothAdapter btAdapter = null;
  private BluetoothSocket btSocket = null;
  private StringBuilder recDataString = new StringBuilder();
  private ConnectedThread mConnectedThread;
  // SPP UUID service - this should work for most devices
  private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  // String for MAC address
  private static String address;
    /********/
    //toggle Button
    static boolean Lock;//whether lock the x-axis to 0-5
    static boolean AutoScrollX;//auto scroll to the last x value
    static boolean Stream;//Start or stop streaming
    //Button init
    Button bXminus;
    Button bXplus;
    Button bYminus;
    Button bYplus;
    ToggleButton tbLock;
    ToggleButton tbScroll;
    ToggleButton tbStream;
    //GraphView init
    static LinearLayout GraphViewLinearLayout;
    static GraphView graphView;
    static GraphViewSeries Series;
    //graph value
    private static double graph2LastXValue = 0;
    private static int Xview=10;
    Button bConnect, bDisconnect;
    /********/
@Override

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    requestWindowFeature(Window.FEATURE_NO_TITLE);//Hide title
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);//Hide Status bar
    setContentView(R.layout.activity_main);
    LinearLayout background = (LinearLayout)findViewById(R.id.bg);
    background.setBackgroundColor(Color.BLACK);
    graphInit();
    buttonInit();


    //Link the buttons and textViews to respective views
    sensorView = (TextView) findViewById(R.id.sensorView0);

    bluetoothIn = new Handler() {
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);

            if (msg.what == handlerState && tbStream.isChecked()) {				  // if message is what we want

                String readMessage = (String) msg.obj;    // msg.arg1 = bytes from connect thread
                recDataString.append(readMessage);        // keep appending to string until ~

                int endOfLineIndex = recDataString.indexOf("~");    // determine the end-of-line INDEX

                if (endOfLineIndex > 0 && flag==1) {                        // make sure there data before ~
                    String dataInPrint = recDataString.substring(0, endOfLineIndex);            // extract string

                    if (recDataString.charAt(0) == 's')							//if it starts with # we know it is what we are looking for
                    {
                        String sensor = recDataString.substring(1, endOfLineIndex);         //get sensor value from string between indices 1-5
                        double sensorValue = Double.valueOf(sensor);
                    	sensorView.setText("Signal Value = " + sensorValue);//update the textviews with sensor values

                        Series.appendData(new GraphViewData(graph2LastXValue, sensorValue),false);

                        //X-axis control
                        if (graph2LastXValue >= Xview && Lock == true){
                            Series.resetData(new GraphViewData[] {});
                            graph2LastXValue = 0;
                        }else graph2LastXValue += 0.1;

                        if(Lock == true) {
                            graphView.setViewPort(0, Xview);
                        }else {
                            graphView.setViewPort(graph2LastXValue-Xview, Xview);
                        }
                        //refresh
                        GraphViewLinearLayout.removeView(graphView);
                        GraphViewLinearLayout.addView(graphView);
                    }
                    recDataString.delete(0, recDataString.length()); 			//clear all string data
                }
                flag = 1;

            }
        }
    };

    btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
    checkBTState();

  }

    void graphInit(){
        //init graphview
        GraphViewLinearLayout = (LinearLayout) findViewById(R.id.Graph);
        // init example series data-------------------
        Series = new GraphViewSeries("Signal",
                new GraphViewStyle(Color.YELLOW, 6), //color and thickness of the line
                new GraphViewData[] {new GraphViewData(0, 0)});
        graphView = new LineGraphView(this, "Graph");
        graphView.setViewPort(0, Xview);
        graphView.setScrollable(true);
        graphView.setScalable(true);
        graphView.setShowLegend(true);
        graphView.setLegendAlign(LegendAlign.BOTTOM);
        graphView.setManualYAxis(true);
        graphView.setManualYAxisBounds(6, -0.5);
        graphView.addSeries(Series); // data
        GraphViewLinearLayout.addView(graphView);
    }
    void buttonInit(){
        bConnect = (Button)findViewById(R.id.bConnect);
        bConnect.setOnClickListener(this);
        bDisconnect = (Button)findViewById(R.id.bDisconnect);
        bDisconnect.setOnClickListener(this);
        //X-axis control button
        bXminus = (Button)findViewById(R.id.bXminus);
        bXminus.setOnClickListener(this);
        bXplus = (Button)findViewById(R.id.bXplus);
        bXplus.setOnClickListener(this);
        bYminus = (Button)findViewById(R.id.bXminus);
        bXminus.setOnClickListener(this);
        bXplus = (Button)findViewById(R.id.bXplus);
        bXplus.setOnClickListener(this);
        //
        tbLock = (ToggleButton)findViewById(R.id.tbLock);
        tbLock.setOnClickListener(this);
        tbStream = (ToggleButton)findViewById(R.id.tbStream);
        tbStream.setOnClickListener(this);
        //init toggleButton
        Lock=false;
        AutoScrollX=true;
        Stream=false;
    }


    boolean doubleBackToExitPressedOnce = false;
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onBackPressed() {

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            disconnect();
            finishAffinity();
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Double click BACK to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.bConnect:
                Intent i = new Intent(this, DeviceListActivity.class);
                startActivity(i);
                break;
            case R.id.bDisconnect:
                disconnect();
                Toast.makeText(this, "Disconnected..", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bXminus:
                if (Xview>1) Xview--;
                break;
            case R.id.bXplus:
                if (Xview<30) Xview++;
                break;
            case R.id.bYminus:
                //if (Xview>1) Xview--;
                break;
            case R.id.bYplus:
                //if (Xview<30) Xview++;
                break;
            case R.id.tbLock:
                if (tbLock.isChecked()){
                    Lock = true;
                }else{
                    Lock = false;
                }
                break;
            case R.id.tbStream:
                if (tbStream.isChecked()){
                    if (mConnectedThread != null)
                        mConnectedThread.write("E");
                }else{
                    if (mConnectedThread != null)
                        mConnectedThread.write("Q");
                }
                break;
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
      
      return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
      //creates secure outgoing connecetion with BT device using UUID
  }

  @Override
  public void onResume() {
      super.onResume();

      //Get MAC address from DeviceListActivity via intent
      Intent intent = getIntent();
    
    //Get the MAC address from the DeviceListActivty via EXTRA
    address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

    //create device and set the MAC address
    BluetoothDevice device = btAdapter.getRemoteDevice(address);
     
    try {
        btSocket = createBluetoothSocket(device);
    } catch (IOException e) {
    	Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
    }  
    // Establish the Bluetooth socket connection.
    try 
    {
      btSocket.connect();
    } catch (IOException e) {
      try 
      {
        btSocket.close();
      } catch (IOException e2) 
      {
    	//insert code to deal with this 
      }
    } 
    mConnectedThread = new ConnectedThread(btSocket);
    mConnectedThread.start();
    
    //I send a character when resuming.beginning transmission to check device is connected
    //If it is not an exception will be thrown in the write method and finish() will be called
    mConnectedThread.write("x");
  }
  
  @Override
  public void onPause() 
  {
    super.onPause();
    try
    {
    //Don't leave Bluetooth sockets open when leaving activity
      btSocket.close();
    } catch (IOException e2) {
    	//insert code to deal with this 
    }
  }

 //Checks that the Android device Bluetooth is available and prompts to be turned on if off 
  private void checkBTState() {
 
    if(btAdapter==null) { 
    	Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
    } else {
      if (btAdapter.isEnabled()) {
      } else {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, 1);
      }
    }
  }
    public  void disconnect(){
        if (mConnectedThread!= null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }
  //create new class for connect thread
  private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
      
        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
            	//Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
      
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
      
        public void run() {
            byte[] buffer;
            int bytes; 
 
            // Keep looping to listen for received messages
            while (true) {
                try {
                    Thread.sleep(30);
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);        	//read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget(); 
                } catch (IOException e) {
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {  
            	//if you cannot write, close the application
            	Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
            	finish();
            }
        }

      /** Will cancel an in-progress connection, and close the socket */
      public void cancel() {
          try {
              btSocket.close();
          } catch (IOException e) { }
      }
  }
}
    
