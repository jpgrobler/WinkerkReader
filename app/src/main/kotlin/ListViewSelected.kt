package za.co.jpsoft.winkerkreader.data

/**
 * Created by Pieter Grobler on 01/09/2017.
 */
internal class ListViewSelected(id: Int, smsto: Boolean) {
    var id: Int = id
        set(value) {
            field = value
            // Note: setter name will be setId() in Java
        }

    var smsto: Boolean = smsto
    // Java getter: getSmsto(), setter: setSmsto()
}