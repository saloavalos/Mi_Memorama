package com.saloavalos.mimemorama

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.saloavalos.mimemorama.models.BoardSize
import kotlin.math.min

class ImagePickerAdapter(
        private val context: Context,
        private val imageUris: List<Uri>,
        private val boardSize: BoardSize,
        private val imageClickListener: ImageClickListener
) : RecyclerView.Adapter<ImagePickerAdapter.ViewHolder>() {

    interface ImageClickListener {
        fun onPlaceholderClicked()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_image, parent, false)
        val cardWidth = parent.width / boardSize.getWidth()
        val cardHeight = parent.height / boardSize.getHeight()
        val cardSideLength = min(cardWidth, cardHeight)
        val layoutParams = view.findViewById<ImageView>(R.id.iv_CustomImage).layoutParams
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        return ViewHolder(view)
    }

    override fun getItemCount() = boardSize.getNumPairs()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < imageUris.size) {
            // the user has already pick the image for the card
            holder.bind(imageUris[position])
        } else {
            // the user hasn't yet pick the image for the card
            holder.bind()
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iv_CustomImage = itemView.findViewById<ImageView>(R.id.iv_CustomImage)

        fun bind(uri: Uri) {
            iv_CustomImage.setImageURI(uri)
            // We don't want to respond to clicks
            iv_CustomImage.setOnClickListener(null)
        }

        fun bind() {
            iv_CustomImage.setOnClickListener {
                // Launch an intent for the users to select photos
                imageClickListener.onPlaceholderClicked()
            }
        }
    }
}
