package com.example.hayden.assignment5;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    // Unique UUID for this application
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    /** Name of the connected device */
    private String mConnectedDeviceName = null;

    /** Local Bluetooth adapter */
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member fields
//    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ThreadConnected mThreadConnected;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    private File file;
    private Uri tmpPath;
    public BufferedWriter bufferedWriter;
    ArrayAdapter<String> mSensorDataArrayAdapter;

    // Layout Views
    private ListView mSensorDataView;
    private Button mStartButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //csv stuff
        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File myFile = new File(dcim, "sensorData.csv");

        if(!myFile.exists()) {
            file = new File(dcim, "sensorData.csv");

            try {
                bufferedWriter = new BufferedWriter(new FileWriter(file));
                String headings ="TimeStamp,Sensor1,Sensor2,Sensor3\n";
                bufferedWriter.write(headings);
                bufferedWriter.flush();

            } catch (IOException e){
                Log.d("BUILDING", "CANT CREATE BUFFERED OR FILE READER");
            }
        } else {
            //already exists
            try {
                bufferedWriter = new BufferedWriter(new FileWriter(myFile, true));
            } catch (IOException e){
                Log.d("BUILDING", "CANT CREATE BUFFERED OR FILE READER");
            }
        }

        //Layout
        mSensorDataView = (ListView) findViewById(R.id.in);
        mStartButton = (Button) findViewById(R.id.button_start);

        setup();

        mStartButton.setVisibility(View.INVISIBLE);

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startEdison();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                Log.d("BUILDING", "CLICKED secure");
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                Log.d("BUILDING", "CLICKED insecure");
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                Log.d("BUILDING", "CLICKED DISCOVERABLE");
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        Log.d("BUILDING", "inside ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    public synchronized void setState(int state) {
        mState = state;

        //make a call to mHandler eventually TODO
    }
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d("BUILDING", "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mThreadConnected != null) {
            mThreadConnected.cancel();
            mThreadConnected = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d("BUILDING", "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mThreadConnected != null) {
            mThreadConnected.cancel();
            mThreadConnected = null;
        }


        // Start the thread to manage the connection and perform transmissions
        mThreadConnected = new ThreadConnected(socket);
        mThreadConnected.start();

//        // Send the name of the connected device back to the UI Activity
//        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
//        Bundle bundle = new Bundle();
//        bundle.putString(Constants.DEVICE_NAME, device.getName());
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tempSocket = null;
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("BUILDING", "Socket listen() failed", e);
            }

            mmSocket = tempSocket;
        }

        @Override
        public void run() {
            Log.d("BUILDING", "BEGIN mConnectThread");
            setName("ConnectThread");

            mBluetoothAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                mmSocket.connect();
            } catch (IOException e) {
                Log.d("BUILDING", "FAILED TO CONNECT TO SOCKET");
                return;
            }

            synchronized (MainActivity.this) {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice);
        }

        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("BUILDING", "close() failed", e);
            }
        }
    }

    private class ThreadConnected extends Thread {
        private BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;

        public ThreadConnected(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tempIn = socket.getInputStream();
                tempOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("BUILDING", "temp sockets not created", e);
            }
            connectedInputStream = tempIn;
            connectedOutputStream = tempOut;
        }

        @Override
        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            mStartButton.setVisibility(View.VISIBLE);
            // Keep listening to the InputSteam while connected
            while(true) {
                try {
                    //Read from the InputStream
                    bytes = connectedInputStream.read(buffer);

                    writeToCSV(bytes, buffer);
                } catch (IOException e) {
                    Log.e("BUILDING", "Disconnected", e);
                    //try and restart maybe eventually
                    break;
                }
            }
        }

        public void write(byte[] buffer){

            try{
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e("BUILDING", "Exception during write" , e);
            }
        }

        public void cancel() {
            try {
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                Log.e("BUILDING", "close() failed", e);
            }
        }
    }

    public void writeToCSV(int bytes, byte[] buffer) {
        String sensorInfo = new String(buffer, 0, bytes);
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();

        sensorInfo = ts + ',' + sensorInfo+'\n';

        mSensorDataArrayAdapter.add(sensorInfo);
        try {
            bufferedWriter.write(sensorInfo);
            bufferedWriter.flush();
        } catch (IOException e){
            Log.e("BUILDING", "error writing to csv", e);
        }

    }

    public void startEdison(){
        Log.d("BUILDING", "sending START cmd to connected device");
        String startCommand = "START";
        byte[] cmdBuffer = startCommand.getBytes();

        mThreadConnected.write(cmdBuffer);
    }

    public void setup(){
        Log.d("BUILDING", "entering setup");
        //initialize the array adapter that will display recieved sensor data
        mSensorDataArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mSensorDataView.setAdapter(mSensorDataArrayAdapter);

        connectToEdison();

    }

    public void connectToEdison(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device: pairedDevices){
            Log.d("BUILDING", "Device name: "+device.getName());
            if(device.getName().equals("hayden")){
                Log.d("BUILDING", "FOUND EDISON");
                connect(device);
                break;
            }
        }
    }

}
