package za.co.jpsoft.winkerkreader.utils.widget

import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs

object PastoralWidgetDependencies {
    lateinit var congregationPrefs: CongregationPrefs
        private set

    fun init(prefs: CongregationPrefs) {
        congregationPrefs = prefs
    }
}