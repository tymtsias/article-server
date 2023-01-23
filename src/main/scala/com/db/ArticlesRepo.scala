package com.db

import com.models.Article

import scala.concurrent.Future

trait ArticlesRepo {

  def get(tag: Option[String],
          author: Option[String],
          favorited: Option[Boolean],
          offset: Int,
          limit: Int): Future[List[Article]]

}
