package com.cle.ambientnotifications;

import java.util.HashMap;

/**
 * Created by christian on 12/31/14.
 */
public class BLEGattAttributes {
    private static HashMap<String, String> attributes = new HashMap<String, String>();

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String BLE_SHIELD_TX = "713d0003-503e-4c75-ba94-3148f18d941e";
    public static String BLE_SHIELD_RX = "713d0002-503e-4c75-ba94-3148f18d941e";
    public static String BLE_SHIELD_SERVICE = "713d0000-503e-4c75-ba94-3148f18d941e";

    static {
        // Services.
        attributes.put("713d0000-503e-4c75-ba94-3148f18d941e",
                "BLE Shield Service");
        // Characteristics.
        attributes.put(BLE_SHIELD_TX, "BLE Shield TX");
        attributes.put(BLE_SHIELD_RX, "BLE Shield RX");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
