package com.db

import com.models.{Article, CreateArticleModel, CreatingArticleAdditionalInfo}

import scala.concurrent.{ExecutionContext, Future}

trait ArticlesRepo {
  def save (userEmail: String, entity: CreateArticleModel)(implicit ec: ExecutionContext): Future[CreatingArticleAdditionalInfo]

  def yourFeed(offset: Int, limit: Int, userEmail: String): Future[List[Article]]
  def get(tag: Option[String],
          author: Option[String],
          favorited: Option[Boolean],
          offset: Int,
          limit: Int): Future[List[Article]]

}
