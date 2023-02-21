package com.models
import java.time.LocalDateTime

case class CreatingArticleAdditionalInfo(slug: String,
                                         date: LocalDateTime,
                                         favorited: Boolean,
                                         favoritesCount: Int,
                                         following: Boolean)
