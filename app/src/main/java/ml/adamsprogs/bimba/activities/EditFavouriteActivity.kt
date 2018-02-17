package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.widget.EditText
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.FavouriteEditRowAdapter
import ml.adamsprogs.bimba.models.FavouriteStorage
import kotlinx.android.synthetic.main.activity_edit_favourite.*

class EditFavouriteActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FAVOURITE = "favourite"
    }

    private lateinit var favourites: FavouriteStorage
    private lateinit var nameEdit: EditText
    private var favourite: Favourite? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_favourite)

        favourite = intent.getParcelableExtra(EXTRA_FAVOURITE)
        if (favourite == null)
            finish()
        favourites = FavouriteStorage.getFavouriteStorage(this)

        val recyclerView = favourite_edit_list
        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
        val dividerItemDecoration = DividerItemDecoration(this, layoutManager.orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = FavouriteEditRowAdapter(favourite!!)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.edit_favourite_title, favourite!!.name)
        nameEdit = favourite_name_edit
        nameEdit.setText(favourite!!.name)
    }

    override fun onBackPressed() {
        favourites.rename(favourite?.name!!, nameEdit.text.toString())
        super.onBackPressed()
    }
}
