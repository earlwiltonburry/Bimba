package ml.adamsprogs.bimba.activities

import android.preference.*
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_settings.*

import ml.adamsprogs.bimba.*

class SettingsActivity: AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        supportActionBar.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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