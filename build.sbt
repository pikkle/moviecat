name := "moviecat"

version := "1.0"

lazy val `moviecat` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq( jdbc , cache , ws   , specs2 % Test )

libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "1.1.0"

libraryDependencies += "com.google.apis" % "google-api-services-youtube" % "v3-rev174-1.22.0"


unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"  