package com.db

import scala.concurrent.{ExecutionContext, Future}

trait TagsRepo {
  def getAll()(implicit ec: ExecutionContext): Future[Set[String]]
}
