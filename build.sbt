name := "bunny"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies += "com.rabbitmq" % "amqp-client" % "4.0.0"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "com.typesafe" % "config" % "1.3.1" % "test"
