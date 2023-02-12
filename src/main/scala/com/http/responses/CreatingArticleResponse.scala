package com.http.responses

import com.models.auth.{Author, UserData}
import com.models.{CreateArticleModel, CreatingArticleAdditionalInfo}

import java.time.LocalDateTime

case class CreatingArticleBody(slug: String,
                                   title: String,
                                   description: String,
                                   body: String,
                                   tagList: List[String],
                                   createdAt: LocalDateTime,
                                   updatedAt: LocalDateTime,
                                   favorited: Boolean,
                                   favoritesCount: Int,
                                   author: Author)

case class CreatingArticleResponse(article: CreatingArticleBody)

object CreatingArticleResponse {
  def build(userResponse: UserData,
            articleModel: CreateArticleModel,
            creatingArticleAdditionalInfo: CreatingArticleAdditionalInfo): CreatingArticleResponse =
    CreatingArticleResponse(CreatingArticleBody(
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
    ))
}
