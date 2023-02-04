package com.models

import com.models.auth.Author

import java.sql.Timestamp
import java.time.LocalDateTime

case class Article(
                    slug: String,
                    title: String,
                    description: String,
                    body: String,
                    tagList: List[String],
                    createdAt: Timestamp,
                    updatedAt: Timestamp,
                    favorited: Boolean,
                    favoritesCount: Int,
                    author: Author
)
