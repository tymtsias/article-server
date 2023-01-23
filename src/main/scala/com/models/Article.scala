package com.models

import com.models.auth.{Author}

import java.time.LocalDateTime

case class Article(
    slug: String,
    title: String,
    description: String,
    body: String,
    tagList: List[String],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    favorited: Boolean,
    favoritesCount: Int,
    author: Author
)
