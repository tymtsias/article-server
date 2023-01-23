package com.db

import scala.concurrent.Future

trait TagsRepo {

  def getAll(): Future[Set[String]]

}
