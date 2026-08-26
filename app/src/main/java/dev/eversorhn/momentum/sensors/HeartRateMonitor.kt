package dev.eversorhn.momentum.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * A Bluetooth heart-rate strap or watch, read straight off the standard service every such
 * device implements. No account, no vendor SDK, no cloud: MOMENTUM connects to the sensor the same
 * way a bike computer does.
 *
 * Pace alone says how fast you went; heart rate says what it cost. Kept deliberately small —
 * scan, connect, subscribe, publish a number — because everything above it only needs the number.
 */
@SuppressLint("MissingPermission")
class HeartRateMonitor(private val context: Context) {

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val samples = ArrayList<Int>()

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Every heart-rate device on the planet exposes these; nothing vendor-specific is needed. */
    private companion object {
        val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val SCAN_TIMEOUT_MS = 12_000L
    }

    data class Found(val name: String, val address: String)

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** Scans for heart-rate devices for a few seconds. [onFound] may be called more than once per device. */
    fun scan(onFound: (Found) -> Unit, onDone: () -> Unit) {
        val scanner = adapter?.bluetoothLeScanner ?: run { onDone(); return }
        if (scanning) return
        scanning = true
        val seen = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device?.address ?: return
                if (!seen.add(address)) return
                onFound(Found(result.device?.name ?: "Heart-rate sensor", address))
            }
        }
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                cb,
            )
        }.onFailure { scanning = false; onDone(); return }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { scanner.stopScan(cb) }
            scanning = false
            onDone()
        }, SCAN_TIMEOUT_MS)
    }

    /** Connects to a remembered device and starts publishing [bpm]. Safe to call twice. */
    fun connect(address: String) {
        if (gatt != null) return
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        samples.clear()
        gatt = runCatching {
            device.connectGatt(context, true, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    _connected.value = newState == BluetoothGatt.STATE_CONNECTED
                    if (newState == BluetoothGatt.STATE_CONNECTED) runCatching { g.discoverServices() }
                    if (newState == BluetoothGatt.STATE_DISCONNECTED) _bpm.value = null
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    val ch = g.getService(SERVICE)?.getCharacteristic(MEASUREMENT) ?: return
                    g.setCharacteristicNotification(ch, true)
                    val cccd = ch.getDescriptor(CCCD) ?: return
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(cccd)
                        }
                    }
                }

                override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                    publish(parse(value))
                }

                @Deprecated("Pre-Tiramisu callback")
                override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                    @Suppress("DEPRECATION")
                    publish(parse(ch.value ?: return))
                }
            })
        }.getOrNull()
    }

    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        _connected.value = false
        _bpm.value = null
    }

    /** Mean and peak over the session so far, or null when nothing was ever received. */
    fun summary(): Pair<Int, Int>? {
        if (samples.isEmpty()) return null
        return samples.average().toInt() to (samples.maxOrNull() ?: 0)
    }

    fun resetSummary() = samples.clear()

    private fun publish(value: Int?) {
        val v = value ?: return
        if (v !in 25..250) return
        _bpm.value = v
        samples += v
    }

    /**
     * The measurement characteristic: flags byte first, then the rate as one or two bytes
     * depending on bit 0 (Bluetooth SIG's Heart Rate Measurement format).
     */
    private fun parse(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val wide = (data[0].toInt() and 0x01) != 0
        return if (wide) {
            if (data.size < 3) null else (data[1].toInt() and 0xff) or ((data[2].toInt() and 0xff) shl 8)
        } else {
            if (data.size < 2) null else data[1].toInt() and 0xff
        }
    }
}

/** The remembered strap. One device: this is not a fleet manager. */
object HeartRatePrefs {
    private const val PREFS = "momentum_heart_rate"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"

    fun address(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)

    fun name(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun remember(context: Context, address: String, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
