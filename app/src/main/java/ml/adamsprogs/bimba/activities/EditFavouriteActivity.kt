package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.widget.EditText
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.FavouriteEditRowAdapter
import ml.adamsprogs.bimba.models.FavouriteStorage

class EditFavouriteActivity : AppCompatActivity() {

    lateinit var favourites: FavouriteStorage
    lateinit var nameEdit: EditText
    var favourite: Favourite? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_favourite)

        favourite = intent.getParcelableExtra<Favourite>("favourite")
        if (favourite == null)
            finish()
        favourites = FavouriteStorage.getFavouriteStorage(this)

        val recyclerView = findViewById(R.id.favourite_edit_list) as RecyclerView?
        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
        val dividerItemDecoration = DividerItemDecoration(this, layoutManager.orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = FavouriteEditRowAdapter(favourite!!)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.edit_favourite_title, favourite!!.name)
        nameEdit = findViewById(R.id.favourite_name_edit) as EditText
        nameEdit.setText(favourite!!.name)
    }

    override fun onBackPressed() {
        val oldName = favourite?.name
        val newName = nameEdit.text.toString()
        favourites.delete(oldName!!)
        favourite?.name = newName
        favourites.add(newName, favourite!!)
        super.onBackPressed()
    }
}
