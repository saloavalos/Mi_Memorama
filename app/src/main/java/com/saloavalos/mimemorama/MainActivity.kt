package com.saloavalos.mimemorama

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.saloavalos.mimemorama.models.BoardSize
import com.saloavalos.mimemorama.models.MemoramaGame
import com.saloavalos.mimemorama.models.UserImageList
import com.saloavalos.mimemorama.utils.EXTRA_BOARD_SIZE
import com.saloavalos.mimemorama.utils.EXTRA_GAME_NAME
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 77
    }

    // lateinit means it will be initialized later (inside onCreate Method)
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rv_Board: RecyclerView
    private lateinit var tv_NumMoves: TextView
    private lateinit var tv_NumPairs: TextView

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var memoramaGame: MemoramaGame
    private lateinit var adapter: MemoramaBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hooks
        clRoot = findViewById(R.id.clRoot)
        rv_Board = findViewById(R.id.rv_Board)
        tv_NumMoves = findViewById(R.id.tv_NumMoves)
        tv_NumPairs = findViewById(R.id.tv_NumPairs)


        setupBoard()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_Refresh -> {
                // if the user has some progress in the game
                if (memoramaGame.getNumMoves() > 0 && !memoramaGame.haveWonGame()) {
                    showAlertDialog("Quit you current game", null, View.OnClickListener {
                        // set up the game again
                        setupBoard()
                    })
                } else {
                    // set up the game again
                    setupBoard()
                }
                return true
            }
            // Handle mi_NewSize of menu_main
            R.id.mi_NewSize -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_CustomGame -> {
                showCreationDialog()
                return true
            }
            R.id.mi_Download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Retrieve the custom name
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        // Receive the name of the game the user wants to download
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            // Grab the text of the game name
            val et_DownloadGame = boardDownloadView.findViewById<EditText>(R.id.et_DownloadGame)
            val gameToDownload = et_DownloadGame.text.toString()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry, we couldn't find any such game, $customGameName", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            // we have found a game successfully
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            // Prefetch all the images with picasso, this is to optimize loading of the images downloaded that goes in the cards
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're playing '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retriving game", exception)
        }
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.rg_BoardSize)
        showAlertDialog("Create your own memory board", boardSizeView, View.OnClickListener {
            // Set a new value for the board size (change the difficulty of the game), depending on the selected button
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rb_Easy -> BoardSize.EASY
                R.id.rb_Medium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // Navigate to a new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            // startActivityForResult, is used if you want to get some data back from the activity we are going to launch
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.rg_BoardSize)
        // Show/Check with the radioButton what is the current difficulty selected
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rb_Easy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rb_Medium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rb_Hard)
        }
        showAlertDialog("Choose new size", boardSizeView, View.OnClickListener {
            // Set a new value for the board size (change the difficulty of the game), depending on the selected button
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rb_Easy -> BoardSize.EASY
                R.id.rb_Medium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // Reset these values every time the user goes back to a default game
            gameName = null
            customGameImages = null
            // once we selected the difficulty we restart the game
            setupBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null) // null is to dismiss dialog if click cancel
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        // set custom game name, and if the gameName is null set it equal to the name of the app
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tv_NumMoves.text = "Easy: 4 x 2"
                tv_NumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tv_NumMoves.text = "Medium: 6 x 3"
                tv_NumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tv_NumMoves.text = "Hard: 6 x 4"
                tv_NumPairs.text = "Pairs: 0 / 12"
            }
        }

        // this one, sets starting text color
        tv_NumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoramaGame = MemoramaGame(boardSize, customGameImages)

        // Adapter, provide a binding for the data set to the views of the RecyclerView (Adapts each piece of data into a view)
        adapter = MemoramaBoardAdapter(this, boardSize, memoramaGame.cards, object : MemoramaBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })

        rv_Board.adapter = adapter

        // This optimizes RecyclerView (used when adapter changes cannot affect the size of the RecyclerView)
        rv_Board.setHasFixedSize(true)

        // LayoutManager , measures and positions item views
        rv_Board.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        // Error handling
        // if we won the memory game
        if (memoramaGame.haveWonGame()) {
            // Alert the user of an invalid move
            // clRoot is the root view, in this case is my ConstraintLayout which contains everything
            Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        // if the card is already face up
        if (memoramaGame.isCardFaceUp(position)) {
            // Alert the user of an invalid move
            // clRoot is the root view, in this case is my ConstraintLayout which contains everything
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()
            return
        }

        // flip over the card
        if (memoramaGame.flipCard(position)) {
            // Show number of pairs found
            Log.i(TAG, "Found a match!, Num pairs found: ${memoramaGame.numPairsFound}")

            //this one, increases eventually the text color starting from one color in specific (in this case red and eventually transitions to green)
            val color = ArgbEvaluator().evaluate(
                    memoramaGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                    ContextCompat.getColor(this, R.color.color_progress_none),
                    ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tv_NumPairs.setTextColor(color)
            tv_NumPairs.text = "Pairs: ${memoramaGame.numPairsFound} / ${boardSize.getNumPairs()}"
            // check if maybe the user won the game flipping over this card
            if (memoramaGame.haveWonGame()) {
                Snackbar.make(clRoot, "You won! Congratulations", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }

        // Show how many moves the user has made
        tv_NumMoves.text = "Moves: ${memoramaGame.getNumMoves()}"

        // tell the recyclerview adapter that the contents of what it's showing has changed, so it should update
        adapter.notifyDataSetChanged()

    }
}