package com.viking.xfsr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private UsbManager mUsbManager;
    private List<UsbSerialPort> mSerialPorts = new ArrayList<UsbSerialPort>();
    private UsbSerialPort mSerialPort = null;
    private EditText mEditTextRxData;
    private TextView mTextViewRxCount;
    private TextView mTextViewDevice;
    private FloatingActionButton mFab;
    private RecordTask mRecordTask = null;
    private StringBuffer mRxDataBuf = new StringBuffer(100000);

    private static final int MESSAGE_REFRESH = 101;
    private static final int MESSAGE_START_RECORD = 102;
    private static final int MESSAGE_STOP_RECORD = 103;
    private static final int MESSAGE_REFRESH_RX_DATA = 104;

    private static final long REFRESH_TIMEOUT_MILLIS = 5000;
    private static final long RECORD_TIMEOUT_MILLIS = 250;

    private static final String MSG_RX_DATA_KEY = "rx_data";
    private static final String MSG_RX_FILE_KEY = "rx_file";

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    if (mSerialPort == null) {
                        getSerialPorts();
                        mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    }
                    break;
                case MESSAGE_START_RECORD:
                    if (mRecordTask == null) {
                        mRecordTask = new RecordTask();
                        mRecordTask.execute((Void) null);
                    }
                    break;
                case MESSAGE_STOP_RECORD:
                    if (mRecordTask != null) {
                        mRecordTask.stop();
                        mRecordTask = null;
                    }
                    break;
                case MESSAGE_REFRESH_RX_DATA:
                    mTextViewRxCount.setText(String.format(getString(R.string.rx_template), msg.arg1));
                    byte[] rx_data = msg.getData().getByteArray(MSG_RX_DATA_KEY);
                    String rx_str;
                    try {
                        rx_str = HexDump.toString(rx_data);
                    } catch (Exception e) {
                        rx_str = e.getMessage();
                    }

                    int free_size = mRxDataBuf.capacity() - mRxDataBuf.length();
                    if (free_size < rx_str.length()) {
                        mRxDataBuf.delete(0, rx_str.length());
                    }
                    mRxDataBuf.append(rx_str);

                    mEditTextRxData.setText(mRxDataBuf);
                    mEditTextRxData.setSelection(mEditTextRxData.getText().length(), mEditTextRxData.getText().length());
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private int mRxCount = 0;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager = null;
    private final SerialInputOutputManager.Listener mListener =  new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Toast.makeText(getApplicationContext(), R.string.runner_stopped, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onNewData(final byte[] data) {
            /*
            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SerialConsoleActivity.this.updateReceivedData(data);
                }
            });
            */
            mRxCount += data.length;
            //TODO; Save and show data

            mTextViewRxCount.setText(String.format(getString(R.string.rx_template), mRxCount));
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mEditTextRxData = (EditText) findViewById(R.id.editTextRxData);
        mEditTextRxData.setMovementMethod(ScrollingMovementMethod.getInstance());

        mTextViewRxCount = (TextView) findViewById(R.id.textViewRxCount);
        mTextViewDevice = (TextView) findViewById(R.id.textViewDevice);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                //TODO: Check USB is connected
                if (mRecordTask == null) {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_START_RECORD, RECORD_TIMEOUT_MILLIS);
                } else {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_STOP_RECORD, RECORD_TIMEOUT_MILLIS);
                }

//                if (mSerialIoManager == null && startRecord()) {
//                    mFab.setImageDrawable(getDrawable(R.drawable.ic_stop));
//                } else {
//                    stopRecord();
//                    mFab.setImageDrawable(getDrawable(R.drawable.ic_play));
//                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessage(MESSAGE_REFRESH);
    }

    @Override
    protected void onPause() {
        mHandler.removeMessages(MESSAGE_REFRESH);
        super.onPause();
    }

    @Override
    protected void onStop() {
        //stopRecord();
        super.onStop();
    }

    private void getSerialPorts()
    {
        mSerialPorts.clear();
        final List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            //Log.d(TAG, String.format("+ %s: %s port%s", driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            mSerialPorts.addAll(ports);
        }
        //Toast.makeText(this, String.format("Found %d", mSerialPorts.size()), Toast.LENGTH_SHORT).show();
        if (mSerialPorts.size() > 0) {
            mSerialPort = mSerialPorts.get(0);
            //Toast.makeText(getApplicationContext(), mSerialPort.toString(), Toast.LENGTH_LONG).show();
            mTextViewDevice.setText(String.format("%s %s", mSerialPort.getClass().getSimpleName(), mSerialPort.getDriver().getDevice().getDeviceId()));
        }
    }

    private int openPort() {
        UsbDeviceConnection connection = null;

        if (mSerialPort == null
                || (connection = mUsbManager.openDevice(mSerialPort.getDriver().getDevice())) == null) {
            return -1;
        }

        try {
            mSerialPort.open(connection);
            SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int baudrate = Integer.parseInt(preference.getString(getString(R.string.pref_key_baudrate), "115200"));
            int databit = Integer.parseInt(preference.getString(getString(R.string.pref_key_databit), "8"));
            int stopbit = Integer.parseInt(preference.getString(getString(R.string.pref_key_stopbit), "1"));
            int parity = Integer.parseInt(preference.getString(getString(R.string.pref_key_parity), "0"));
            mSerialPort.setParameters(baudrate, databit, stopbit, parity);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.error_opening_device), e.getMessage()), Toast.LENGTH_SHORT).show();
            try {
                mSerialPort.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return -2;
        }

        return 0;
    }

    void closePort() {
        if (mSerialPort != null) {
            try {
                mSerialPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    private boolean startRecord() {
//        if (mSerialPort == null) {
//            Toast.makeText(getApplicationContext(), R.string.device_not_connect, Toast.LENGTH_LONG).show();
//            return false;
//        }
//
//        UsbDeviceConnection connection = mUsbManager.openDevice(mSerialPort.getDriver().getDevice());
//        if (connection == null) {
//            Toast.makeText(getApplicationContext(), R.string.open_device_failed, Toast.LENGTH_LONG).show();
//            return false;
//        }
//
//        mEditTextRxData.setText("");
//
//        try {
//            mSerialPort.open(connection);
//            SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//
//            mEditTextRxData.append(String.format("%s %s %s",
//                    preference.getString(getString(R.string.pref_key_baudrate), "115200xxx"),
//                    preference.getString(getString(R.string.pref_key_stopbit), "1xxx"),
//                    preference.getString(getString(R.string.pref_key_parity), "0xxx")));
//
//            int baudrate = Integer.parseInt(preference.getString(getString(R.string.pref_key_baudrate), "115200"));
//            int stopbit = Integer.parseInt(preference.getString(getString(R.string.pref_key_stopbit), "1"));
//            int parity = Integer.parseInt(preference.getString(getString(R.string.pref_key_parity), "0"));
//            mSerialPort.setParameters(baudrate, 8, stopbit, parity);
//
//        } catch (IOException e) {
//            Toast.makeText(getApplicationContext(), String.format(getString(R.string.error_opening_device), e.getMessage()), Toast.LENGTH_SHORT).show();
//            try {
//                mSerialPort.close();
//            } catch (IOException e1) {
//                e1.printStackTrace();
//            }
//            return false;
//        } catch (Exception e1) {
//            mEditTextRxData.append(e1.getMessage());
//            return false;
//        }
//
//        //TODO: Open file
//        mRxCount = 0;
//        mSerialIoManager = new SerialInputOutputManager(mSerialPort, mListener);
//        mExecutor.submit(mSerialIoManager);
//
//        return true;
//    }
//
//    private void stopRecord() {
//        if (mSerialIoManager == null) {
//            return;
//        }
//
//        mSerialIoManager.stop();
//        mSerialIoManager = null;
//
//        if (mSerialPort != null) {
//            try {
//                mSerialPort.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    private class RecordTask extends AsyncTask<Void, Void, Void> {
        private boolean running = false;
        private int count = 0;
        private final ByteBuffer mReadBuffer = ByteBuffer.allocate(100000);
        private BufferedOutputStream record_file = null;

        @Override
        protected void onPreExecute() {
            if (openPort() != 0 || (record_file = recordFile()) == null) {
                mHandler.sendEmptyMessageDelayed(MESSAGE_STOP_RECORD, RECORD_TIMEOUT_MILLIS);
            }

            mTextViewRxCount.setText(R.string.rx);
            mFab.setImageDrawable(getDrawable(R.drawable.ic_stop));
            running = true;
        }

        @Override
        protected Void doInBackground(Void... params) {
            while (running) {
                // Read USB
                try {
                    int len = mSerialPort.read(mReadBuffer.array(), 200);
                    if (len > 0) {
                        count += len;
                        final byte[] rx_data = new byte[len];
                        mReadBuffer.get(rx_data, 0, len);
                        mReadBuffer.clear();

                        //TODO: Save data
                        record_file.write(rx_data);

                        Message msg = new Message();
                        Bundle bundle = new Bundle();
                        //bundle.putCharArray(MSG_RX_DATA_KEY, getChars(mReadBuffer, len));
                        bundle.putByteArray(MSG_RX_DATA_KEY, rx_data);
                        //bundle.putString(MSG_RX_FILE_KEY, record_file.toString());
                        msg.what = MESSAGE_REFRESH_RX_DATA;
                        msg.arg1 = count;
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                record_file.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            closePort();
            mFab.setImageDrawable(getDrawable(R.drawable.ic_play));
        }

        void stop() {
            running = false;
        }

        private File recordDir() {
            File dir = new File(Environment.getExternalStorageDirectory().getPath(), "SerialRecord");
            if (!dir.exists()) {
                dir.mkdir();
            }
            return dir;
        }

        private BufferedOutputStream recordFile() {
            //String date = SimpleDateFormat.getDateTimeInstance().format(new Date());
            String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            try {
                return new BufferedOutputStream(new FileOutputStream(new File(recordDir(), date + ".txt")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

//        private char[] getChars(ByteBuffer bytes, int length) {
//            Charset cs = Charset.forName("UTF-8");
//            ByteBuffer bb = ByteBuffer.allocate(length);
//            bb.put(bytes);
//            bb.flip();
//            CharBuffer cb = cs.decode(bb);
//            return cb.array();
//        }


    }
}
