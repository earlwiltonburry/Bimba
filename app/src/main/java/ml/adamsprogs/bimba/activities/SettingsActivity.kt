package ml.adamsprogs.bimba.activities

import android.preference.*
import android.os.Bundle

import ml.adamsprogs.bimba.*

// todo create layout with toolbar and fragment; and put fragment here
class SettingsActivity: AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(android.R.id.content, MainPreferenceFragment()).commit()
    }


    class MainPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_main)

            bindPreferenceSummaryToValue(findPreference(getString(R.string.key_timetable_source_url)))
        }

        private fun bindPreferenceSummaryToValue(preference: Preference) {
            preference.onPreferenceChangeListener = bindPreferenceSummaryToValueListener

            bindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.context)
                            .getString(preference.key, ""))
        }

        private val bindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val stringValue = newValue.toString()
            if (preference is EditTextPreference) {
                if (preference.getKey() == getString(R.string.key_timetable_source_url)) {
                    preference.summary = stringValue
                }
            } else {
                preference.summary = stringValue
            }
            true
        }
    }
}