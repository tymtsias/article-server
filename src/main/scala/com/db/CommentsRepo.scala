package com.db

import com.models.Comment

import scala.concurrent.Future

trait CommentsRepo {

  def get(slug: String, maybeUserEmail: Option[String]): Future[List[Comment]]

  def create(slug: String, body: String, userEmail: String): Future[Comment]

  def delete(id: Int, userEmail: String): Future[Boolean]

}
