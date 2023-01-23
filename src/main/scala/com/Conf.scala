package com

import com.typesafe.config.ConfigFactory

object Conf {
  val configuration = ConfigFactory.load("local.conf")
  val httpHost      = configuration.getString("http.host")
  val httpPort      = configuration.getInt("http.port")
  val jwtSecret     = configuration.getString("jwtSecret")

  object DB {
    val url      = configuration.getString("db.url")
    val userName = configuration.getString("db.userName")
    val password = configuration.getString("db.password")
  }

}
