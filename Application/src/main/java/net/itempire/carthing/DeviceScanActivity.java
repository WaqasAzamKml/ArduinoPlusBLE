package net.itempire.carthing;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(net.itempire.carthing.R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, net.itempire.carthing.R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, net.itempire.carthing.R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(net.itempire.carthing.R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(net.itempire.carthing.R.id.menu_stop).setVisible(false);
            menu.findItem(net.itempire.carthing.R.id.menu_scan).setVisible(true);
            menu.findItem(net.itempire.carthing.R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(net.itempire.carthing.R.id.menu_stop).setVisible(true);
            menu.findItem(net.itempire.carthing.R.id.menu_scan).setVisible(false);
            menu.findItem(net.itempire.carthing.R.id.menu_refresh).setActionView(
                    net.itempire.carthing.R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case net.itempire.carthing.R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case net.itempire.carthing.R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<DeviceRecord> mDevices;
        private LayoutInflater mInflator;
        private long mLastUpdate;

        class DeviceRecord {
            public BluetoothDevice device;
            public Long lastScanned;
            public int rssi;

            public DeviceRecord(BluetoothDevice device, int rssi) {
                this.device = device;
                this.rssi = rssi;
                this.lastScanned = Long.valueOf(System.currentTimeMillis() / 1000);
            }
        }

        public LeDeviceListAdapter() {
            this.mLastUpdate = 0;
            this.mInflator = DeviceScanActivity.this.getLayoutInflater();
            this.mDevices = new ArrayList();
        }

        public void addDevice(BluetoothDevice device, int rssi) {
            synchronized (this.mDevices) {
                Iterator i$ = this.mDevices.iterator();
                while (i$.hasNext()) {
                    DeviceRecord rec = (DeviceRecord) i$.next();
                    if (rec.device.equals(device)) {
                        rec.rssi = rssi;
                        rec.lastScanned = Long.valueOf(System.currentTimeMillis() / 1000);
                        updateUi(false);
                        return;
                    }
                }
                this.mDevices.add(new DeviceRecord(device, rssi));
                updateUi(true);
            }
        }

        public void removeDevice(BluetoothDevice device) {
            synchronized (this.mDevices) {
                Iterator<DeviceRecord> it = this.mDevices.iterator();
                while (it.hasNext()) {
                    if (((DeviceRecord) it.next()).device.equals(device)) {
                        it.remove();
                        updateUi(true);
                        return;
                    }
                }
            }
        }

        public BluetoothDevice getDevice(int position) {
            if (position < this.mDevices.size()) {
                return ((DeviceRecord) this.mDevices.get(position)).device;
            }
            return null;
        }

        public void clear() {
            this.mDevices.clear();
        }

        public int getCount() {
            return this.mDevices.size();
        }

        public Object getItem(int position) {
            return this.mDevices.get(position);
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = this.mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceRssi = (ProgressBar) view.findViewById(R.id.device_rssi);
                viewHolder.rssiValue = (TextView) view.findViewById(R.id.rssi_value);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            DeviceRecord rec = (DeviceRecord) this.mDevices.get(i);
            String deviceName = rec.device.getName();
            String deviceAddr = rec.device.getAddress();
            if (deviceName == null) {
                deviceName = deviceAddr;
            }
            viewHolder.deviceName.setText(deviceName);
            viewHolder.deviceAddress.setText(deviceAddr);
            viewHolder.deviceRssi.setProgress(normaliseRssi(rec.rssi));
            viewHolder.rssiValue.setText(String.valueOf(rec.rssi));
            return view;
        }

        private void updateUi(boolean force) {
            Long ts = Long.valueOf(System.currentTimeMillis() / 1000);
            if (force || ts.longValue() - this.mLastUpdate > 1) {
                synchronized (this.mDevices) {
                    Iterator<DeviceRecord> it = this.mDevices.iterator();
                    while (it.hasNext()) {
                        if (ts.longValue() - ((DeviceRecord) it.next()).lastScanned.longValue() > 2) {
                            it.remove();
                        }
                    }
                }
                DeviceScanActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
                this.mLastUpdate = ts.longValue();
            }
        }

        private int normaliseRssi(int rssi) {
            return (((rssi - 20) + 147) * 100) / 147;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device, rssi);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        ProgressBar deviceRssi;
        TextView rssiValue;
    }
}