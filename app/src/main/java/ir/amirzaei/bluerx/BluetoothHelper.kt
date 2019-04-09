package ir.amirzaei.bluerx

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Message
import android.os.ParcelUuid
import android.util.Log
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit


class BluetoothHelper(var application: Application) {


    enum class BLTStatus {
        Connected, Disconnected, Failed
    }


    private var lastConnectedDevice: BluetoothDevice? = null

    private var bltConnectionsListenerSubject =
        PublishSubject.create<BLTStatus>()

    var bltConnectionListener =
        bltConnectionsListenerSubject as Observable<BLTStatus>

    private var bltOnConnectedSubject =
        PublishSubject.create<String>()

    var bltOnConnectedListener =
        bltOnConnectedSubject as Observable<String>

    var connectedDevices =
        hashMapOf<String, IConnectedDevice>()


    companion object {
        private var instance: BluetoothHelper? = null
        fun getInstance(application: Application): BluetoothHelper =
            if (instance == null) {
                instance = BluetoothHelper(application)
                instance!!
            } else instance!!
    }

//Todo : There aws exception here | A01
    //Can't create handler inside thread that has not called Looper.prepare()
//    private val blt = BluetoothSPP(application)

    val reciver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {

                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {

                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    bltConnectionsListenerSubject.onNext(BLTStatus.Connected)

                    bltOnConnectedSubject.onNext(device.address)

                    lastConnectedDevice = device


                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                }
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> {

                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {

                    bltConnectionsListenerSubject.onNext(BLTStatus.Disconnected)

                    lastConnectedDevice = null

                }
            }
        }
    }


    init {

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)

        application.registerReceiver(reciver, filter)


    }


    fun getDeviceUUIDDescription(uuid: ParcelUuid): String {

        return BluetoothUtils
            .uuidsDescriptions[uuid.toString().substringBefore("-").takeLast(4).toUpperCase()] ?: "No Found"

    }

    fun scan() =
        Observable.create<BluetoothDevice> { emitter ->


            val mReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action

                    // When discovery finds a device
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        // Get the BluetoothDevice object from the Intent
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                        emitter.onNext(device)

                        // When discovery is finished, change the Activity title
                    } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {

                        emitter.onComplete()
                    }
                }
            }

            var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            application.registerReceiver(mReceiver, filter)

            // Register for broadcasts when discovery has finished
            filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            application.registerReceiver(mReceiver, filter)


            // Get the local Bluetooth adapter
            val mBtAdapter = BluetoothAdapter.getDefaultAdapter()

            val pairedDevices = mBtAdapter.bondedDevices

            pairedDevices.forEach { emitter.onNext(it) }

            mBtAdapter.startDiscovery()

            emitter.setCancellable {
                application.unregisterReceiver(mReceiver)
            }

        }
//TODO : A01
//    fun connect(address: String, timeOutInSec: Long): Completable {
//
//        return Completable.create { emitter ->
//
//
//            val mDisposable = bltOnConnectedListener
//                .filter { it == address }
//                .firstOrError()
//                .timeout(timeOutInSec, TimeUnit.SECONDS)
//                .subscribeBy(onSuccess = {
//                    emitter.onComplete()
//                },
//                    onError = {
//                        emitter.onError(it)
//                    }
//                )
//
////            blt.connect(address)
//
//
//            emitter.setCancellable {
//                mDisposable.dispose()
//            }
//
//        }
//            .subscribeOn(Schedulers.single())
//            .observeOn(AndroidSchedulers.mainThread())
//    }


    fun connectedDeviceAddress(): String? {
        return lastConnectedDevice?.address
    }


    fun openBluetoothSetting(): Intent {
        val intentOpenBluetoothSettings = Intent()
        intentOpenBluetoothSettings.action = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
        return intentOpenBluetoothSettings
    }

    /**
     * Use BluetoothProfile.ProfileType for profile
     */
    fun isConnectedTo(profile: Int) =
        isBltOnOrError()
            .andThen(getConnectedDevicesTo(profile))
            .isEmpty
            .map { it == false }

    fun getConnectedDevicesTo(profile: Int) =
        isBltOnOrError()
            .andThen(findBluetoothDevice(profile))
            .map {
                var device = connectedDevices[it.address]

                if (device == null) {
                    device = ConnectedDevice(it)
                    connectedDevices[it.address] = device
                }
                return@map device
            }


    fun findBluetoothDevice(profile: Int): Observable<BluetoothDevice>? {
        return Observable
            .create<BluetoothDevice> { emitter ->
                BluetoothAdapter
                    .getDefaultAdapter()
                    .getProfileProxy(application, object : BluetoothProfile.ServiceListener {

                        override fun onServiceDisconnected(profile: Int) {
                            emitter.onComplete()
                        }

                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {

                            proxy.connectedDevices
                                .forEach {
                                    emitter.onNext(it)
                                }
                            emitter.onComplete()

                        }
                    }, profile)
            }
    }

    fun isBltOn(): Single<Boolean> {
        return Single.fromCallable { BluetoothAdapter.getDefaultAdapter().isEnabled }
    }

    fun isBltOnOrError(): Completable {
        return Completable.create {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled)
                it.onComplete()
            else it.onError(BluetoothIsNotEnable())
        }
    }

    class BluetoothIsNotEnable : Throwable("Bluetooth is not enable")


}


interface IConnectedDevice {
    val messageHandlerSubject:
            PublishSubject<Pair<DeviceConnector.MessageState, String>>

    fun isConnected(): Boolean
    fun listenToMessage(): Observable<String>
}

class ConnectedDevice(device: BluetoothDevice) : IConnectedDevice {

    class DeviceDisconnectedException : Exception("Device  disconnected")


    override val messageHandlerSubject =
        PublishSubject.create<Pair<DeviceConnector.MessageState, String>>()

    private val messageHandler = messageHandlerSubject
        .doOnDispose {
            Log.e("AMIR", "OnDispose")
            Observable
                .timer(1, TimeUnit.SECONDS)
                .subscribe {
                    requestStop()
                }
        }
        .doOnSubscribe {
            numberOfSubscription++
        }


    private val connector = DeviceConnector(device, BluetoothResponseHandler {
        messageHandlerSubject.onNext(it)
    })


    override fun isConnected() = connector.state == DeviceConnector.ConnectionState.CONNECTED

    override fun listenToMessage() =
        start()
            .timeout(15, TimeUnit.SECONDS)
            .flatMapObservable { messageHandler }
            .filter { it.first == DeviceConnector.MessageState.READ }
            .map { it.second }

    //indication of the number of the active subscribers
    var numberOfSubscription = 0

    private fun start(): Single<IConnectedDevice> {


        return when (connector.state) {
            DeviceConnector.ConnectionState.CONNECTED,
            DeviceConnector.ConnectionState.CONNECTING ->

                Single
                    .just(this)
            else ->
                messageHandler
                    .filter { it.first == DeviceConnector.MessageState.STATE_CHANGE }
                    .map {
                        DeviceConnector.ConnectionState.findById(it.second.toInt())
                    }
                    .filter {
                        it == DeviceConnector.ConnectionState.CONNECTED
                    }
                    .doOnSubscribe {
                        when (connector.state) {
                            DeviceConnector.ConnectionState.DISCONNECTED,
                            DeviceConnector.ConnectionState.CONNECTION_FAILED,
                            DeviceConnector.ConnectionState.CONNECTION_LOST -> {
                                connector.connect()
                            }
                        }
                    }
                    .map {
                        this as IConnectedDevice
                    }
                    .firstOrError()
        }
    }

    private fun requestStop() {
        numberOfSubscription--
        if (numberOfSubscription <= 0) {
            connector.stop()
        }
    }


    fun stop() {
        connector.stop()
    }

    class BluetoothResponseHandler(
        val callback: ((Pair<DeviceConnector.MessageState, String>) -> Unit)?
    ) : Handler() {

        override fun handleMessage(msg: Message) {

            val state = DeviceConnector.MessageState.findById(msg.what)

            val readMessage = msg.obj as String?

            if (readMessage != null)
                callback?.invoke(state to readMessage)
            else
                callback?.invoke(state to "")


        }
    }
}


