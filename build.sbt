name := """geostreams"""
organization := "edu.illinois.ncsa"

version := "3.4.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

pipelineStages := Seq(rjs, digest, gzip)

RjsKeys.mainModule := "main"

libraryDependencies += filters
libraryDependencies += jdbc
libraryDependencies += "org.postgresql" %	"postgresql"	% "42.0.0"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test
libraryDependencies += "org.webjars" % "requirejs" % "2.3.1"
libraryDependencies += "com.adrianhurt" %% "play-bootstrap" % "1.1-P25-B3"
libraryDependencies += "com.mohiva" %% "play-silhouette" % "4.0.0"
libraryDependencies += "com.mohiva" %% "play-silhouette-password-bcrypt" % "4.0.0"
libraryDependencies += "com.mohiva" %% "play-silhouette-persistence" % "4.0.0"
libraryDependencies += "com.mohiva" %% "play-silhouette-crypto-jca" % "4.0.0"
libraryDependencies += "com.mohiva" %% "play-silhouette-testkit" % "4.0.0" % "test"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.0.1"
libraryDependencies += "com.iheart" %% "ficus" % "1.2.6"
libraryDependencies += "com.typesafe.play" %% "play-mailer" % "5.0.0"

resolvers += Resolver.jcenterRepo

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "edu.illinois.ncsa.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "edu.illinois.ncsa.binders._"
