package com.polyglotprogramminginc.andevcon2016sanfrancisco;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;

import java.util.UUID;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        BleScannerFragment.ScannerCommunicationBus, ServiceConnection {

    // scan for devices
    private MetaWearBleService.LocalBinder mwBinder = null;
    private MWScannerFragment mwScannerFragment;
    private BluetoothAdapter btAdapter;
    private final static int REQUEST_ENABLE_BT = 0;
    //
    // connect to device
    private boolean btDeviceSelected;
    private MetaWearBoard mwBoard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Scan for devices setup
        /** code to set up the bluetooth adapter or give messages if it's not enabled or available */
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            btAdapter = bluetoothManager.getAdapter();
        }
        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(R.string.error_no_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        // Bind the MetaWear BLE service
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            // bring up the scanner fragment
            if (mwScannerFragment != null) {
                Fragment metawearBlescannerPopup = getFragmentManager().findFragmentById(R.id.metawear_blescanner_popup_fragment);
                if (metawearBlescannerPopup != null) {
                    FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.remove(metawearBlescannerPopup);
                    fragmentTransaction.commit();
                }
                mwScannerFragment.dismiss();
            }
            mwScannerFragment = new MWScannerFragment();
            mwScannerFragment.show(getFragmentManager(), "metawear_scanner_fragment");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_main) {
            // Handle the camera action
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * service bindings
     **/
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        ///< Get a reference to the MetaWear service from the binder
        mwBinder = (MetaWearBleService.LocalBinder) iBinder;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    /**
     * methods for scanning callbacks
     **/
    @Override
    public UUID[] getFilterServiceUuids() {
        ///< Only return MetaWear boards in the scan
        return new UUID[]{UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")};
    }

    @Override
    public long getScanDuration() {
        ///< Scan for 10000ms (10 seconds)
        return 10000;
    }

    @Override
    public void onDeviceSelected(BluetoothDevice bluetoothDevice) {
        // connect to device
        btDeviceSelected = true;
        connectDevice(bluetoothDevice);
        Fragment metawearBlescannerPopup = getFragmentManager().findFragmentById(R.id.metawear_blescanner_popup_fragment);
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.remove(metawearBlescannerPopup);
        fragmentTransaction.commit();
        mwScannerFragment.dismiss();
    }

    /**
     * connection handlers
     */
    private MetaWearBoard.ConnectionStateHandler connectionStateHandler = new MetaWearBoard.ConnectionStateHandler() {
        @Override
        public void connected() {
            Log.i("Metawear Controller", "Device Connected");
            runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  Toast.makeText(getApplicationContext(), "MetaWear Connected",
                                          Toast.LENGTH_SHORT).show();
                              }
                          }
            );
            if (btDeviceSelected) {
                MWDeviceConfirmationFragment mwDeviceConfirmFragment = new MWDeviceConfirmationFragment();
                btDeviceSelected = false;
            }

        }

        @Override
        public void disconnected() {
            Log.i("Metawear Controler", "Device Disconnected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "MetaWear Disconnected", Toast.LENGTH_SHORT).show();
                }
            });

        }
    };

    // MetaWear Connection Helper methods
    private void connectDevice(BluetoothDevice device) {
        mwBoard = mwBinder.getMetaWearBoard(device);

        mwBoard.setConnectionStateHandler(connectionStateHandler);

        mwBoard.connect();
    }

}
