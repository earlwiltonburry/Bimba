package ml.adamsprogs.bimba.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.EditText
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.adapters.FavouriteEditRowAdapter
import ml.adamsprogs.bimba.collections.FavouriteStorage
import kotlinx.android.synthetic.main.activity_edit_favourite.*
import android.app.Activity
import android.content.Intent



class EditFavouriteActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FAVOURITE = "favourite"
        const val EXTRA_POSITION_BEFORE = "position_before"
        const val EXTRA_NEW_NAME = "new_name"
    }

    private lateinit var favourites: FavouriteStorage
    private lateinit var nameEdit: EditText
    private var favourite: Favourite? = null
    private var positionBefore: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_favourite)

        favourite = intent.getParcelableExtra(EXTRA_FAVOURITE)
        positionBefore = intent.getIntExtra(EXTRA_POSITION_BEFORE, -1)
        if (favourite == null)
            finish()
        favourites = FavouriteStorage.getFavouriteStorage(this)

        val recyclerView = favourite_edit_list
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
        val dividerItemDecoration = androidx.recyclerview.widget.DividerItemDecoration(this, layoutManager.orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = FavouriteEditRowAdapter(favourite!!, favourite_edit_loading, favourite_edit_list)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.edit_favourite_title, favourite!!.name)
        nameEdit = favourite_name_edit
        nameEdit.setText(favourite!!.name)
    }

    override fun onBackPressed() {
        favourites.rename(favourite?.name!!, nameEdit.text.toString())
        val returnIntent = Intent()
        returnIntent.putExtra(EXTRA_POSITION_BEFORE, positionBefore)
        returnIntent.putExtra(EXTRA_NEW_NAME, nameEdit.text.toString())
        setResult(Activity.RESULT_OK, returnIntent)
        super.onBackPressed()
    }
}
