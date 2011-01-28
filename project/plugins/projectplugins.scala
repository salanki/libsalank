import sbt._

class ProjectPlugins(info: ProjectInfo) extends PluginDefinition(info) {
  lazy val eclipse = "de.element34" % "sbt-eclipsify" % "0.6.0"
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "0.10"
}
