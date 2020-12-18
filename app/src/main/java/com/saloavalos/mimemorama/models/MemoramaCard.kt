package com.saloavalos.mimemorama.models

data class MemoramaCard(
    // val, once its value is set it cannot be changed
    val identifier: Int,
    val imageUrl: String? = null,
    // var, its value can be change
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false
)