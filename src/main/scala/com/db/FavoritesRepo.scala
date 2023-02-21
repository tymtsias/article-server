package com.db

import scala.concurrent.{ ExecutionContext, Future }

trait FavoritesRepo {
  def favorite(slug: String, email: String)(implicit ec: ExecutionContext): Future[Unit]

  def unfavorite(slug: String, email: String)(implicit ec: ExecutionContext): Future[Unit]

}
