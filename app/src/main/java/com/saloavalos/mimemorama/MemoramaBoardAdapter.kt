package com.saloavalos.mimemorama

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.saloavalos.mimemorama.models.BoardSize
import com.saloavalos.mimemorama.models.MemoramaCard
import com.squareup.picasso.Picasso
import kotlin.math.min

class MemoramaBoardAdapter(
        private val context: Context,
        private val boardSize: BoardSize,
        private val cards: List<MemoramaCard>,
        private val cardClickListener: CardClickListener
) :
    RecyclerView.Adapter<MemoramaBoardAdapter.ViewHolder>() {

    companion object {
        private const val MARGIN_SIZE = 10
        private const val TAG = "MemoramaBoardAdapter"
    }

    interface CardClickListener {
        fun onCardClicked(position: Int)
    }

    // This is responsible for figuring out how to create a view
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Calculate percentage of size that each one has, minus the margin
        val cardWidth = parent.width / boardSize.getWidth() - (2 * MARGIN_SIZE)
        val cardHeight = parent.height / boardSize.getHeight() - (2 * MARGIN_SIZE)
        val cardSideLength = min(cardWidth, cardHeight)

        val view =LayoutInflater.from(context).inflate(R.layout.memorama_card, parent, false)
        val layoutParams = view.findViewById<CardView>(R.id.cv_MemoramaCard).layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        // Apply margin
        layoutParams.setMargins(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE)
        return ViewHolder(view)
    }

    // How many elements are in our RecyclerView
    override fun getItemCount() = boardSize.numCards

    // This is responsible for setting the data that goes at this current position
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ib_MemoramaCard = itemView.findViewById<ImageButton>(R.id.ib_MemoramaCard)

        fun bind(position: Int) {
            val memoramaCard = cards[position]
            if (memoramaCard.isFaceUp) {
                if (memoramaCard.imageUrl != null) {
                    // set the custom image to the card, (load a placeholder image while the image url is being fetched)
                    Picasso.get().load(memoramaCard.imageUrl).placeholder(R.drawable.ic_image).into(ib_MemoramaCard)
                } else {
                    // use default images
                    ib_MemoramaCard.setImageResource(memoramaCard.identifier)
                }
            } else {
                // if the image is up; show image, else show back of card
                ib_MemoramaCard.setImageResource(if (memoramaCard.isFaceUp) memoramaCard.identifier else R.drawable.ic_cover)
            }

            // if is matched change opacity, to know they were already found
            ib_MemoramaCard.alpha = if (memoramaCard.isMatched) .4f else 1.0f
            // if is matched change just a little the background color, to know they were already found
            val colorStateList = if (memoramaCard.isMatched) ContextCompat.getColorStateList(context, R.color.color_gray) else null
            ViewCompat.setBackgroundTintList(ib_MemoramaCard, colorStateList)

            ib_MemoramaCard.setOnClickListener {
                Log.i(TAG, "Click on position - $position")
                cardClickListener.onCardClicked(position)
            }
        }
    }
}
