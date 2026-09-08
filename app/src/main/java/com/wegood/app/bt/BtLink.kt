package com.wegood.app.bt

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

/** 蓝牙直连消息（换行分隔 JSON，双方对等） */
@Serializable
data class BtMsg(
    val t: String,             // hello | heart
    val kind: String? = null,  // heart 种类（heart/kiss/hug/rose/miss）
    val from: String? = null,  // 发送者昵称
    val name: String? = null,  // hello：昵称
    val ts: Long = 0,
)

/**
 * 蓝牙直连链路（RFCOMM/SPP）。
 * 约定：只使用系统设置里已配对的设备 → 不需要定位权限，也不需要扫描。
 * 角色对等：任意一方可监听等待，任意一方可主动连接，连上即建立同一条链路。
 */
object BtLink {
    sealed class State {
        data object Off : State()
        data object Waiting : State()
        data class Connected(val peerName: String) : State()
    }

    /** 双端约定的服务 UUID（Android 蓝牙聊天示例通用的 SPP UUID） */
    const val SPP_UUID: String = "8ce255c0-200a-11e0-ac64-0800200c9a66"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<BtMsg>(extraBufferCapacity = 32)
    val incoming: SharedFlow<BtMsg> = _incoming.asSharedFlow()

    private lateinit var appContext: Context
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    private val writeLock = Any()
    private var writer: PrintWriter? = null
    private var acceptJob: Job? = null
    private var readJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun hasBluetooth(): Boolean = runCatching { adapter() != null }.getOrDefault(false)

    /** API 31+ 需要运行时申请 BLUETOOTH_CONNECT */
    fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.BLUETOOTH_CONNECT,
        ) != PackageManager.PERMISSION_GRANTED

    /** 系统已配对设备（address to 显示名），无权限时返回空列表 */
    fun bondedDevices(): List<Pair<String, String>> = runCatching {
        if (needsPermission()) return emptyList()
        adapter()?.bondedDevices.orEmpty()
            .sortedBy { it.name ?: it.address }
            .map { (it.address ?: "") to (it.name ?: it.address ?: "未知设备") }
    }.getOrDefault(emptyList())

    private fun adapter() =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** 开始等待 TA 连接（监听 RFCOMM）。失败通过 onError 提示；已在链路中会先重开 */
    fun startWaiting(onError: (String) -> Unit = {}) {
        stop()
        val a = adapter() ?: run { onError("此手机不支持蓝牙"); return }
        if (needsPermission()) { onError("缺少蓝牙权限"); return }
        val enabled = runCatching { a.isEnabled }.getOrDefault(false)
        if (!enabled) { onError("请先打开手机蓝牙"); return }
        acceptJob = scope.launch {
            try {
                val server = a.listenUsingInsecureRfcommWithServiceRecord("WeGood", UUID.fromString(SPP_UUID))
                serverSocket = server
                _state.value = State.Waiting
                while (isActive) {
                    // accept() 阻塞；stop() 关闭 serverSocket 时抛 IOException 退出
                    val s = try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                    manage(s)
                }
            } catch (_: SecurityException) {
                _state.value = State.Off
                onError("缺少蓝牙权限")
            } catch (_: IOException) {
                _state.value = State.Off
            } catch (_: Exception) {
                _state.value = State.Off
            }
        }
    }

    /** 主动连接 TA 的手机（对方需已在 WeGood 里处于等待/打开状态） */
    fun connect(address: String, onError: (String) -> Unit = {}) {
        val a = adapter() ?: run { onError("此手机不支持蓝牙"); return }
        if (needsPermission()) { onError("缺少蓝牙权限"); return }
        val dev = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: run { onError("设备无效"); return }
        scope.launch {
            try {
                val s = dev.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                s.connect()
                manage(s)
            } catch (_: SecurityException) {
                _state.value = State.Off
                onError("缺少蓝牙权限")
            } catch (_: IOException) {
                _state.value = State.Off
                onError("连接失败：请确认 TA 也打开了 WeGood（蓝牙等待中）")
            } catch (_: Exception) {
                _state.value = State.Off
                onError("连接失败")
            }
        }
    }

    /** 建立数据通道：握手 + 起读循环 */
    private fun manage(s: BluetoothSocket) {
        synchronized(writeLock) {
            runCatching { socket?.close() }
            socket = s
            writer = PrintWriter(s.outputStream, true)
        }
        val peerName = runCatching { s.remoteDevice.name }.getOrNull() ?: s.remoteDevice.address
        _state.value = State.Connected(peerName)
        send(BtMsg(t = "hello", name = com.wegood.app.data.Prefs.myName))
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = runCatching { json.decodeFromString<BtMsg>(line) }.getOrNull() ?: continue
                    _incoming.tryEmit(msg)
                }
            } catch (_: Exception) {
            }
            onLinkDown(s)
        }
    }

    private fun onLinkDown(s: BluetoothSocket) {
        synchronized(writeLock) {
            if (socket === s) {
                socket = null
                writer = null
            }
        }
        runCatching { s.close() }
        // 仍在监听就回到 Waiting，否则 Off
        _state.value = if (serverSocket != null) State.Waiting else State.Off
    }

    fun send(msg: BtMsg): Boolean {
        val w = writer ?: return false
        return synchronized(writeLock) {
            runCatching {
                w.println(json.encodeToString(msg))
                w.flush()
                true
            }.getOrDefault(false)
        }
    }

    fun stop() {
        readJob?.cancel()
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        runCatching { socket?.close() }
        serverSocket = null
        synchronized(writeLock) {
            socket = null
            writer = null
        }
        _state.value = State.Off
    }
}
