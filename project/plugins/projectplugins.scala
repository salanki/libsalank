import sbt._

class ProjectPlugins(info: ProjectInfo) extends PluginDefinition(info) {
  lazy val eclipse = "de.element34" % "sbt-eclipsify" % "0.7.0"
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.2"
  val akkaRepo = "Akka Repo" at "http://repo.akka.io/releases"

}
