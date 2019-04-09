package ir.amirzaei.bluerx;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;

import java.util.ArrayList;


public class DeviceData {
    private final int deviceClass;
    private final int majorDeviceClass;
    private String name = "";
    private String address = "";
    private int bondState = BluetoothDevice.BOND_NONE;
    private ArrayList<ParcelUuid> uuids = null;

    public DeviceData(BluetoothDevice device, String emptyName) {
        name = device.getName();
        address = device.getAddress();
        bondState = device.getBondState();

        if (name == null || name.isEmpty()) name = emptyName;
        deviceClass = device.getBluetoothClass().getDeviceClass();
        majorDeviceClass = device.getBluetoothClass().getMajorDeviceClass();
        uuids = BluetoothUtils.getDeviceUuids(device);
    }

    public String getName() {
        return name;
    }

    public void setName(String deviceName) {
        name = deviceName;
    }

    public String getAddress() {
        return address;
    }

    public int getDeviceClass() {
        return deviceClass;
    }

    public int getMajorDeviceClass() {
        return majorDeviceClass;
    }

    public ArrayList<ParcelUuid> getUuids() {
        return uuids;
    }

    public int getBondState() {
        return bondState;
    }

    public void setBondState(int state) {
        bondState = state;
    }
}
