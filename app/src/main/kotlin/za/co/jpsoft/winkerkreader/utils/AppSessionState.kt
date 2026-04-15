package za.co.jpsoft.winkerkreader.utils


/**
 * Holds all transient session/runtime state for the app.
 * Replaces global mutable variables previously held in WinkerkContract.
 */
object AppSessionState {
    var sortOrder: String = "VAN"
    var soek: String = "A"
    var detail: Boolean = false
    var lidmaatId: Int = 0
    var lidmaatGuid: String = ""
    var gesinGuid: String = ""
    var soekList: Boolean = false
    var loader: String = ""
    
    // Formerly WinkerkContract.AppSessionState.deviceId = ...
    var deviceId: String = ""
    
    var recordStatus: String = "0"
    var groepSmsNaam: String = ""
    var keuse: String = "Verjaar"
    
    var listView: Int = 2
    var groepView: Int = 500 // WkrContract.winkerkEntry.GROEPLIST_LOADER
    var winkerkDbDatum: String = ""
}
