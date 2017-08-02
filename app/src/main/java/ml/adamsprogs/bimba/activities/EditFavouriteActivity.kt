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

class EditFavouriteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_favourite)

        val favourite = intent.getParcelableExtra<Favourite>("favourite")

        val recyclerView = findViewById(R.id.favourite_edit_list) as RecyclerView?
        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
        val dividerItemDecoration = DividerItemDecoration(this, layoutManager.orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = FavouriteEditRowAdapter(this, favourite)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.edit_favourite_title, favourite.name)

        val nameEdit = findViewById(R.id.favourite_name_edit) as EditText
        nameEdit.setText(favourite.name)
    }
}
