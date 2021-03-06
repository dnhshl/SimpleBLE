package com.example.simpleble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.io.IOException
import java.util.*

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
    private val tvArray: TextView by lazy{ findViewById(R.id.tvArray) }
    private val btnLED: Button by lazy{ findViewById(R.id.buttonLED) }
    private val tvLED: TextView by lazy{ findViewById(R.id.tvLED) }
    private val btnLEDFlash: Button by lazy{ findViewById(R.id.buttonLEDFlash) }
    private val tvFlash : TextView by lazy { findViewById(R.id.tvFlash) }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var selectedDevice: String
    private lateinit var deviceAddress: String
    private var isScanning = false
    private var deviceIsSelected = false
    private var isConnected = false
    private var isOnLED = false
    private var ledFlashing = false
    private var isReceivingData = false

    private var discoveredDevices = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect.isEnabled = false
        btnData.isEnabled = false
        btnLED.isEnabled = false
        btnLEDFlash.isEnabled = false

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

        // BluetoothLe Service starten
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        // Service anbinden
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)

        btnDiscoverDevices.setOnClickListener {
            checkBTPermission()

            if (!isScanning) { // Suche ist nicht gestartet
                scanner.startScan(scanCallback)
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
            // Button Logik und connect bzw disconnect
            if (isConnected) {
                bluetoothLeService!!.disconnect()
                isConnected = false
                tvConnected.text = getString(R.string.bt_connect_off)
            } else {
                bluetoothLeService!!.connect(deviceAddress)
            }
        }

        btnLED.setOnClickListener {
            val obj = JSONObject()
            isOnLED = !isOnLED
            // Werte setzen
            if (isOnLED) {
                btnLED.text = getString(R.string.bt_led_off)
                tvLED.text = getString(R.string.led_on)
                obj.put("LED", "H")
            } else {
                btnLED.text = getString(R.string.bt_led_on)
                tvLED.text = getString(R.string.led_off)
                obj.put("LED", "L")
            }
            obj.put("LEDBlinken", if (ledFlashing) true else false)

            // Senden
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }
        }

        btnLEDFlash.setOnClickListener {
            val obj = JSONObject()
            ledFlashing = !ledFlashing
            if(ledFlashing) {
                obj.put("LEDBlinken", true)
                btnLEDFlash.text = getString(R.string.led_flash_off)
                tvFlash.text = getString(R.string.flash_on)
            }else{
                obj.put("LEDBlinken", false)
                btnLEDFlash.text = getString(R.string.led_flash_on)
                tvFlash.text = getString(R.string.flash_off)
            }
            obj.put("LED", if (isOnLED) "H" else "L")

            // Senden
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }
        }

        btnData.setOnClickListener {
            if (isReceivingData) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                isReceivingData = false;
                btnData.text = getString(R.string.bt_data_on)
                tvData.setText(R.string.no_data)
                tvArray.setText(R.string.no_data)
            } else {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                btnData.text = getString(R.string.bt_data_off)
            }
        }

        listview.onItemClickListener = lvClickListener
    }

    private val lvClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id ->
            // Ger??t aus dem Listview ausw??hlen
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
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothLeService != null && isConnected) {
            val result = bluetoothLeService!!.connect(deviceAddress)
            Log.d(TAG, "Connect request result=" + result)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Wenn Devicename nicht ESP32 enth??lt, mache nichts
            if (result.device.name == null) return
            if (!result.device.name.contains("ESP32")) return

            val deviceInfo = """${result.device.name} ${result.device.address}""".trimIndent()
            Log.i(TAG, "DeviceFound: $deviceInfo")

            // gefundenes Ger??t der Liste hinzuf??gen, wenn es noch nicht aufgef??hrt ist
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
        // Aufr??umen
        scanner.stopScan(scanCallback)
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        unbindService(serviceConnection)
        bluetoothLeService = null
    }

    // BluetoothLE Service Anbindung
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            // Variable zum Zugriff auf die Service-Methoden
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothLeService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                finish()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
        }
    }


    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            when (action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> onConnect()
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> onDisconnect()
                BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED
                    -> onGattCharacteristicDiscovered()
                BluetoothLeService.ACTION_DATA_AVAILABLE -> onDataAvailable()
            }
        }
    }

    private fun onConnect() {
        isConnected = true
        tvConnected.setText(R.string.connected)
        btnData.isEnabled = true
        btnLED.isEnabled = true
        btnLEDFlash.isEnabled = true
        btnDiscoverDevices.isEnabled = false
        Log.i(TAG, "connected")
    }

    private fun onDisconnect() {
        isConnected = false
        tvConnected.setText(R.string.disconnected)
        btnData.isEnabled = false
        btnLED.isEnabled = false
        btnLEDFlash.isEnabled = false
        btnDiscoverDevices.isEnabled = true
        Log.i(TAG, "disconnected")
    }

    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    private fun onDataAvailable() {
        // neue Daten verf??gbar
        Log.i(TAG, "Data available")
        val bytes: ByteArray = gattCharacteristic!!.value
        // byte[] to string
        val s = String(bytes)
        Log.i(TAG, "Data received $s")
        parseJSONData(s)
    }

    private fun parseJSONData(jsonString : String) {
        try {
            val obj = JSONObject(jsonString)
            //extrahieren des Objektes data
            tvData.text = obj.getString("ledstatus").toString()
            //Array Ausgabe
            tvArray.text = obj.getJSONArray("potiArray").toString()

        } catch (e : JSONException) {
            e.printStackTrace()
        }
    }
}


