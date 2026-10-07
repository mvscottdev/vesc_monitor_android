package dev.vescmonitor.core.link

/** Plain-language reasons shown with the connection state. */
enum class Reason(
    val message: String,
) {
    MODULE_NOT_FOUND("Module not found. Is it powered? Close VESC Tool or any other app connected to it."),
    TAKEN_BY_OTHER("Connection taken by another device."),
    VESC_NOT_ANSWERING(
        "Module connected but the VESC is not answering: check the UART app, 115200 baud, RX/TX wiring, " +
            "and that the VESC has not disabled the module.",
    ),
    LINK_ERRORS("Link errors"),
    NOT_A_BRIDGE("Not a VESC bridge?"),
    FIRMWARE_TOO_OLD("Firmware too old: update in VESC Tool"),
    BLUETOOTH_OFF("Bluetooth is off."),
    PERMISSION_CONNECT("Bluetooth permission denied: cannot connect."),
    PERMISSION_SCAN("Bluetooth permission denied: cannot search."),
    PAIRING_NEEDED("Pairing needed"),
    LINK_LOST("Link lost. Reconnect when the module is in range."),
    NO_CONTROLLERS("No motor controller found on this link."),
    REPLAY_ENDED("Recording finished."),
    USER_DISCONNECT("Disconnected."),
}
