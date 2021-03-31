package com.example.simpleble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import splitties.toast.toast

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private val btnDiscoverDevices : Button by lazy{ findViewById(R.id.buttonDiscoverDevices) }
    private val listview: ListView by lazy { findViewById(R.id.listview) }
    private val tvSelectedDeviceTitle: TextView by lazy{ findViewById(R.id.tvSelectedDevice1) }
    private val tvSelectedDevice: TextView by lazy{ findViewById(R.id.tvSelectedDevice2) }
    private val btnConnect: Button by lazy{ findViewById(R.id.buttonConnect) }
    private val tvConnected: TextView by lazy{ findViewById(R.id.tvConnect) }
    private val btnData: Button by lazy{ findViewById(R.id.buttonData) }
    private val tvData: TextView by lazy{ findViewById(R.id.tvData) }
    private val btnLED: Button by lazy{ findViewById(R.id.buttonLED) }
    private val tvLED: TextView by lazy{ findViewById(R.id.tvLED) }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner

    private lateinit var selectedDevice: String
    private lateinit var deviceAddress: String
    private var isScanning = false
    private var deviceIsSelected = false

    private var discoveredDevices = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(bluetoothAdapter == null)
        {
            toast(getString(R.string.bt_not_available))
            finish()
        }

        if (!packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            ))
        {
            toast(getString(R.string.ble_not_supported))
            finish()
        }

        scanner = bluetoothAdapter.bluetoothLeScanner

        btnDiscoverDevices.setOnClickListener {

            checkBTPermission()

            if (!isScanning) { // Suche ist nicht gestartet
                scanner.startScan(scanCallback)
                //scanner?.startScan(scanCallback)
                Log.i(TAG, "Starte Scan")
                isScanning = true
                btnDiscoverDevices.text = getString(R.string.stop_search_device)
            } else {                        // Suche ist gestartet
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Stoppe Scan")
                isScanning = false
                btnDiscoverDevices.text = getString(R.string.start_search_device)
            }
        }

        btnConnect.setOnClickListener {

        }

        btnLED.setOnClickListener {

        }

        btnData.setOnClickListener {

        }

        listview.onItemClickListener = lvClickListener
    }

    private val lvClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id -> // Gerät aus dem Listview auswählen
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                btnDiscoverDevices.text = getText(R.string.start_search_device)
            }
            selectedDevice = (view as TextView).text.toString()
            deviceAddress = selectedDevice.substring(selectedDevice.length - 17)
            tvSelectedDeviceTitle.visibility = View.VISIBLE
            tvSelectedDevice.visibility = View.VISIBLE
            tvSelectedDevice.text = selectedDevice
            deviceIsSelected = true
            btnConnect.isEnabled = true
        }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceInfo = """${result.device.name}${result.device.address}""".trimIndent()
            Log.i(TAG, "DeviceFound: $deviceInfo")

            // gefundenes Gerät der Liste hinzufügen, wenn es noch nicht aufgeführt ist
            if (!discoveredDevices.contains(deviceInfo)) {
                discoveredDevices.add(deviceInfo)
            }

            // aktualisierte Liste im Listview anzeigen
            val adapt = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1,
                discoveredDevices)
            listview.adapter = adapt
        }
    }

    private fun checkBTPermission() {
        var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
        if (permissionCheck != 0) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Aufräumen
        scanner.stopScan(scanCallback)
    }
}