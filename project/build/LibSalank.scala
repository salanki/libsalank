import sbt._
import de.element34.sbteclipsify._

class LibSalankParentProject(info: ProjectInfo) extends ParentProject(info) {

  /****** Configuration  *******/
//  override def mainClass = Some("com.proceranetworks.psm.cmtspoller.LeasePollerMain")
//  override def compileOptions = ExplainTypes :: CompileOption("-unchecked") :: super.compileOptions.toList //  :: CompileOption("-optimise")
//  override def testOptions = super.testOptions ++  Seq(TestArgument(TestFrameworks.ScalaTest, "-oDF"))

  /****** Dependencies  *******/
  object Dependencies {
  // Logging
  private val logback_version = "0.9.29"
  val slf4j = "org.slf4j" % "slf4j-api" % "1.6.2"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % logback_version
  val logbackCore = "ch.qos.logback" % "logback-core" % logback_version
  // I/O
  val dispatchHttp = "net.databinder" % "dispatch-http_2.9.1" % "0.8.5"
  // val akka_persistence = "se.scalablesolutions.akka" % "akka-persistence_2.8.0"  % "0.10"
  // DNS
//	val dnsjava = "org.dnsjava" % "dnsjava" % "2.0.6"
 // CSV
   val opencsv = "net.sf.opencsv" % "opencsv" % "2.1"
  // SNMP
  val snmp4j = "org.snmp4j" % "snmp4j" % "1.11.3"
  // Lib
 // val scalaz = "com.googlecode.scalaz" % "scalaz-core_2.8.0.Beta1" % "5.0.1-SNAPSHOT"
  // Search
 // val solrj = "org.apache.solr" % "solr-solrj" % "1.4.0"
  // Persistence
 // val thrift = "org.apache.thrift" % "libthrift" % "r808609" from "http://cassandra-java-client.googlecode.com/svn-history/r6/trunk/lib/libthrift-r808609.jar"
  //val cassandra = "org.apache.cassandra" % "cassandra" % "0.5.1"
 // val postgresql = "postgresql" % "postgresql" % "8.4-701.jdbc4"
    // Testing
    val scalaTest = "org.scalatest" % "scalatest_2.9.1" % "1.6.1"
  }
  
  /******** Repositories *******/
  val guiceyfruit = "GuiceyFruit" at "http://guiceyfruit.googlecode.com/svn/repo/releases"
  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  val jBoss = "jBoss" at "http://repository.jboss.org/nexus/content/groups/public"
  //val multiverse = "Multiverse" at "http://multiverse.googlecode.com/svn/maven-repository/releases"
   val java_net = "java.net repository" at "http://download.java.net/maven/2"
   val oosnmp = "snmp4j repository" at "http://maven.opennms.org/content/groups/opennms.org-release"
   val akkaRepo = "Akka Repo" at "http://repo.akka.io/releases/"
   val scalaToolsSnapshots = ScalaToolsSnapshots
  
  // -------------------------------------------------------------------------------------------------------------------
  // Subprojects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val libsalank_core       = project("libsalank-core",   "libsalank-core",     new LibSalankCoreProject(_))
  lazy val libsalank_akka   	= project("libsalank-akka",   "libsalank-akka",     new LibSalankAkkaProject(_),    libsalank_core)
  
  // -------------------------------------------------------------------------------------------------------------------
  // libsalank-core subproject
  // -------------------------------------------------------------------------------------------------------------------

  class LibSalankCoreProject(info: ProjectInfo) extends LibSalankDefaultProject(info) {
	val slf4j = Dependencies.slf4j
	val logbackClassic = Dependencies.logbackClassic
	val logbackCore = Dependencies.logbackCore
	val dispatchHttp = Dependencies.dispatchHttp /* Separate into I/O module */
	val snmp = Dependencies.snmp4j /* Separate into I/O module */
	val csv = Dependencies.opencsv
	
    	// testing
	val scalatest = Dependencies.scalaTest
  }
  
  
  // -------------------------------------------------------------------------------------------------------------------
  // libsalank-akka subproject
  // -------------------------------------------------------------------------------------------------------------------

  class LibSalankAkkaProject(info: ProjectInfo) extends LibSalankDefaultProject(info) with AkkaProject {
	//  val akkaCamel = akkaModule("camel")
	val dispatcherExtras = akkaModule("dispatcher-extras")
	val akkaSlf4j = akkaModule("slf4j")
	//val remote = akkaModule("remote")
	// testing
	val scalatest = Dependencies.scalaTest
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------------------------------------------------
  
  class LibSalankDefaultProject(info: ProjectInfo /*, val deployPath: Path */) extends DefaultProject(info) with Eclipsify  {
    //override def disableCrossPaths = true

    override def compileOptions = ExplainTypes :: CompileOption("-unchecked") :: super.compileOptions.toList //  :: CompileOption("-optimise")
    override def testOptions = super.testOptions ++  Seq(TestArgument(TestFrameworks.ScalaTest, "-oDF"))
    
    /******** Manifest *******/
    override def packageOptions = super.packageOptions ++ Seq(ManifestAttributes("Implementation-Version" -> projectVersion.value.toString))
  
    /******** Publishing *******/
    override def managedStyle = ManagedStyle.Maven
    val publishTo = Resolver.file("maven-local", Path.userHome / ".m2" / "repository" asFile)
  }
}
