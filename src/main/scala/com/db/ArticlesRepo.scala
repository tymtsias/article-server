package com.db

import com.models.{Article, CreateArticleModel, CreatingArticleAdditionalInfo}

import scala.concurrent.Future

trait ArticlesRepo {
  def save (userEmail: String, req: CreateArticleModel): Future[CreatingArticleAdditionalInfo]

  def get(tag: Option[String],
          author: Option[String],
          favorited: Option[Boolean],
          offset: Int,
          limit: Int): Future[List[Article]]

}
