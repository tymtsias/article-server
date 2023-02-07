ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "article-server"
  )

val http4sVersion = "1.0.0-M29"
resolvers += Resolver.sonatypeRepo("snapshots")
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-core" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion
)
val doobieVersion = "1.0.0-M1"

libraryDependencies ++= Seq(
  // Start with this one
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  // And add any of these as needed
  "org.tpolecat" %% "doobie-h2" % doobieVersion, // H2 driver 1.4.200 + type mappings.
  "org.tpolecat" %% "doobie-hikari" % doobieVersion, // HikariCP transactor.
  "org.tpolecat" %% "doobie-postgres" % doobieVersion, // Postgres driver 42.3.1 + type mappings.
  "org.tpolecat" %% "doobie-specs2" % doobieVersion % "test", // Specs2 support for typechecking statements.
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion % "test" // ScalaTest support for typechecking statements.
)

resolvers += Resolver.sonatypeRepo("snapshots")
libraryDependencies ++= Seq(
  // Optional for auto-derivation of JSON codecs
  "io.circe" %% "circe-generic" % "0.15.0-M1",
  // Optional for string interpolation to JSON model
  "io.circe" %% "circe-literal" % "0.15.0-M1"
)


libraryDependencies += "io.jsonwebtoken" % "jjwt" % "0.9.1"
libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.1"
libraryDependencies += "com.typesafe" % "config" % "1.4.2"
libraryDependencies += "org.slf4j" % "slf4j-jdk14" % "2.0.6"