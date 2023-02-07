package com.models

import com.models.auth.{Author, UserResponse}

import java.time.LocalDateTime

case class CreatingArticleResponse(slug: String,
                                   title: String,
                                   description: String,
                                   body: String,
                                   tagList: List[String],
                                   createdAt: LocalDateTime,
                                   updatedAt: LocalDateTime,
                                   favorited: Boolean,
                                   favoritesCount: Int,
                                   author: Author)

object CreatingArticleResponse {
  def build(userResponse: UserResponse,
            articleModel: CreateArticleModel,
            creatingArticleAdditionalInfo: CreatingArticleAdditionalInfo): CreatingArticleResponse =
    CreatingArticleResponse(
      slug = creatingArticleAdditionalInfo.slug,
      title = articleModel.title,
      description = articleModel.description,
      body = articleModel.body,
      tagList = articleModel.tagList,
      createdAt = creatingArticleAdditionalInfo.date,
      updatedAt = creatingArticleAdditionalInfo.date,
      favorited = creatingArticleAdditionalInfo.favorited,
      favoritesCount = creatingArticleAdditionalInfo.favoritesCount,
      author = Author.fromUserResponse(userResponse, creatingArticleAdditionalInfo.following)
    )
}
