import com.github.play2war.plugin._

name := """ui"""

version := "2.1"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  javaWs,
  "commons-collections" % "commons-collections" % "3.2.1",
  "org.webjars" %% "webjars-play" % "2.3.0",
  "org.webjars" % "bootstrap" % "3.2.0",
  "org.webjars" % "jquery" % "1.11.1",
  "org.webjars" % "jquery-ui" % "1.11.1",
  "org.apache.chemistry.opencmis" % "chemistry-opencmis-client-impl" % "0.12.0",
  "org.apache.chemistry.opencmis" % "chemistry-opencmis-client-bindings" % "0.12.0",
  "org.apache.chemistry.opencmis" % "chemistry-opencmis-client-api" % "0.12.0",
  "org.apache.chemistry.opencmis" % "chemistry-opencmis-commons-impl" % "0.12.0",
  "org.apache.httpcomponents" % "httpclient" % "4.4-beta1"
)

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.1"

Play2WarKeys.targetName := Option("ui")
