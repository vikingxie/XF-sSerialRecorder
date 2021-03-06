package com.viking.xfsr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.viking.xfsr.SettingsActivity.DEFAULT_PATH_NAME;
import static com.viking.xfsr.SettingsActivity.INVALID_PATH_NAME;

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

    private static final int MESSAGE_REFRESH = 101;
    private static final int MESSAGE_FORCE_REFRESH = 102;
    private static final int MESSAGE_START_RECORD = 103;
    private static final int MESSAGE_STOP_RECORD = 104;
    private static final int MESSAGE_REFRESH_RX_DATA = 105;
    private static final int MESSAGE_EXIT = 106;

    private static final long REFRESH_TIMEOUT_MILLIS = 5000;
    private static final long RECORD_TIMEOUT_MILLIS = 250;

    private static final String MSG_RX_DATA_KEY = "rx_data";

    private final Handler mHandler = new Handler() {
        private StringBuffer mRxDataBuf = new StringBuffer(4000);

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    if (mSerialPort != null) {
                        break;
                    }
                case MESSAGE_FORCE_REFRESH:
                    getSerialPorts();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                case MESSAGE_START_RECORD:
                    if (mRecordTask == null) {
                        mTextViewRxCount.setText(R.string.rx);

                        mRxDataBuf.delete(0, mRxDataBuf.length());
                        mEditTextRxData.setText(mRxDataBuf);

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
                case MESSAGE_EXIT:
                    finish();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String record_dir_name = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString(getString(R.string.pref_key_file_dir), INVALID_PATH_NAME);
        if (record_dir_name == INVALID_PATH_NAME) {
            File dir = new File(Environment.getExternalStorageDirectory().getPath(), DEFAULT_PATH_NAME);
            PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putString(getString(R.string.pref_key_file_dir), dir.getAbsolutePath()).apply();
        }

        setContentView(R.layout.activity_main);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerUSBReceiver();

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
                if (mRecordTask == null) {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_START_RECORD, RECORD_TIMEOUT_MILLIS);
                } else {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_STOP_RECORD, RECORD_TIMEOUT_MILLIS);
                }
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
    protected void onDestroy() {
        mHandler.sendEmptyMessage(MESSAGE_STOP_RECORD);
        super.onDestroy();
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
        } else {
            mSerialPort = null;
            mTextViewDevice.setText(getString(R.string.device_not_connect));
        }
    }

    private int openPort() {
        if (mSerialPort == null) {
            Toast.makeText(getApplicationContext(), R.string.device_not_connect, Toast.LENGTH_LONG).show();
            return -1;
        }

        UsbDeviceConnection connection = mUsbManager.openDevice(mSerialPort.getDriver().getDevice());
        if (connection == null) {
            Toast.makeText(getApplicationContext(), R.string.open_device_failed, Toast.LENGTH_LONG).show();
            return -2;
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
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.error_opening_device), e.getMessage()), Toast.LENGTH_LONG).show();
            try {
                mSerialPort.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return -3;
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

    private class RecordTask extends AsyncTask<Void, Void, Void> {
        private boolean running = false;
        private boolean record_to_file = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean(getString(R.string.pref_key_record_to_file), true);
        private int count = 0;
        private final ByteBuffer mReadBuffer = ByteBuffer.allocate(100000);
        private BufferedOutputStream record_file = null;

        @Override
        protected void onPreExecute() {
            if (openPort() != 0
                    || (record_to_file && (record_file = recordFile()) == null)) {
                mHandler.sendEmptyMessageDelayed(MESSAGE_STOP_RECORD, RECORD_TIMEOUT_MILLIS);
                return;
            }

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

                        // Save data
                        if (record_to_file) {
                            record_file.write(rx_data);
                        }

                        Message msg = new Message();
                        Bundle bundle = new Bundle();
                        bundle.putByteArray(MSG_RX_DATA_KEY, rx_data);
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
                if (record_to_file) {
                    record_file.close();
                }
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
            String path = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString(getString(R.string.pref_key_file_dir), getString(R.string.pref_default_file_dir));
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Toast.makeText(getApplicationContext(), R.string.create_record_dir_fail, Toast.LENGTH_LONG).show();
                }
            }
            return dir;
        }

        private BufferedOutputStream recordFile() {
            //String date = SimpleDateFormat.getDateTimeInstance().format(new Date());
            String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            try {
                return new BufferedOutputStream(new FileOutputStream(new File(recordDir(), date + ".txt")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    //public void onDevicePluged() {
    //    //mHandler.sendEmptyMessage(MESSAGE_FORCE_REFRESH);
    //    Intent intent = getIntent();
    //    overridePendingTransition(0, 0);
    //    //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    //    finish();
    //    overridePendingTransition(0, 0);
    //    startActivity(intent);
    //}

    public void onDeviceUnplugged() {
        mHandler.sendEmptyMessage(MESSAGE_STOP_RECORD);

        //mHandler.sendEmptyMessageDelayed(MESSAGE_FORCE_REFRESH, 300);
        mHandler.sendEmptyMessageDelayed(MESSAGE_EXIT, 300);
    }

    private final BroadcastReceiver mUSBReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            //    Toast.makeText(context, "插入", Toast.LENGTH_LONG).show();
            //}
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                Toast.makeText(context, R.string.device_unplugged, Toast.LENGTH_LONG).show();
                onDeviceUnplugged();
            }
        }
    };

    private void registerUSBReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        //intentFilter.addDataScheme("file");
        registerReceiver(mUSBReceiver, intentFilter);
    }

}
