import sbt._
import de.element34.sbteclipsify._

// with AkkaProject 
class LibSalankProject(info: ProjectInfo) extends DefaultProject(info) with Eclipsify {
  import java.io.File

  override def managedStyle = ManagedStyle.Maven
   val publishTo = Resolver.file("maven-local", Path.userHome / ".m2" / "repository" asFile)
  /****** Configuration  *******/
//  override def mainClass = Some("com.proceranetworks.psm.cmtspoller.LeasePollerMain")
  override def compileOptions = ExplainTypes :: CompileOption("-unchecked") :: super.compileOptions.toList //  :: CompileOption("-optimise")

  /****** Dependencies  *******/
  // Akka
//  val akkaCamel = akkaModule("camel")
  // Logging
  private val logback_version = "0.9.17"
  val slf4j = "org.slf4j" % "slf4j-api" % "1.5.8"
  val logback_classic = "ch.qos.logback" % "logback-classic" % logback_version
  val logback_core = "ch.qos.logback" % "logback-core" % logback_version
  // I/O
//  val dispatch_http = "net.databinder" % "dispatch-http_2.8.0.Beta1" % "0.6.6"
  // val akka_persistence = "se.scalablesolutions.akka" % "akka-persistence_2.8.0"  % "0.10"
  // DNS
//	val dnsjava = "org.dnsjava" % "dnsjava" % "2.0.6"
 // CSV
   val opencsv = "net.sf.opencsv" % "opencsv" % "2.1"
  // SNMP
  val snmp4j = "org.snmp4j" % "snmp4j" % "1.11.1"
  // Lib
 // val scalaz = "com.googlecode.scalaz" % "scalaz-core_2.8.0.Beta1" % "5.0.1-SNAPSHOT"
  // Search
 // val solrj = "org.apache.solr" % "solr-solrj" % "1.4.0"
  // Persistence
 // val thrift = "org.apache.thrift" % "libthrift" % "r808609" from "http://cassandra-java-client.googlecode.com/svn-history/r6/trunk/lib/libthrift-r808609.jar"
  //val cassandra = "org.apache.cassandra" % "cassandra" % "0.5.1"
 // val postgresql = "postgresql" % "postgresql" % "8.4-701.jdbc4"

  // Custom repositories
  val guiceyfruit = "GuiceyFruit" at "http://guiceyfruit.googlecode.com/svn/repo/releases"
  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  val jBoss = "jBoss" at "http://repository.jboss.org/nexus/content/groups/public"
//  val nexus = "Nexus Maven 2 repository" at "https://nexus.griddynamics.net/nexus/content/groups/public"
  val multiverse = "Multiverse" at "http://multiverse.googlecode.com/svn/maven-repository/releases"
//  val lag_net = "lag.net repository" at "http://www.lag.net/repo"
   val java_net = "java.net repository" at "http://download.java.net/maven/2"
   val oosnmp = "snmp4j repository" at "https://server.oosnmp.net/dist/release"

  /******** Manifest *******/
  override def packageOptions = super.packageOptions ++ Seq(ManifestAttributes("Implementation-Version" -> projectVersion.value.toString))

  /******** Proguard *******/
  lazy val outputJar = outputPath / (name + "-" + version + "-standalone.jar")

  val proguardJar = "net.sf.proguard" % "proguard" % "4.3" % "tools->default"
  val toolsConfig = config("tools")
  def rootProjectDirectory = rootProject.info.projectPath
  val proguardConfigurationPath: Path = outputPath / "proguard.pro"
  lazy val proguard = proguardTask dependsOn(`package`, writeProguardConfiguration)
  private lazy val writeProguardConfiguration = writeProguardConfigurationTask dependsOn `package`
  //lazy val pack = packTask dependsOn(proguard)

  private def proguardTask = task {
    FileUtilities.clean(outputJar :: Nil, log)
    val proguardClasspathString = Path.makeString(managedClasspath(toolsConfig).get)
    val configFile = proguardConfigurationPath.toString
    println(proguardClasspathString)
    val exitValue = Process("java", List("-Xmx1024M", "-cp", proguardClasspathString, "proguard.ProGuard", "@" + configFile)) ! log
    if(exitValue == 0) None else Some("Proguard failed with nonzero exit code (" + exitValue + ")")
  }

  private def writeProguardConfigurationTask = 
    task {
      /* the template for the proguard configuration file
       * You might try to remove "-keep class *" and "-keep class *", but this might break dynamic classloading.
       */
      val outTemplate = """
	|-dontskipnonpubliclibraryclasses
	|-dontskipnonpubliclibraryclassmembers
	|-dontoptimize
        |-dontobfuscate
	|-dontshrink
	|-dontpreverify
        |-dontnote
        |-dontwarn
        |-libraryjars %s
        |%s
        |-outjars %s
        |-ignorewarnings
	|-keep class *
	|-keep class %s { *** main(...); }
        |"""
      
      val defaultJar = (outputPath / defaultJarName).asFile.getAbsolutePath
      log.debug("proguard configuration using main jar " + defaultJar)

      val externalDependencies = Set() ++ (
        mainCompileConditional.analysis.allExternals ++ compileClasspath.get.map { _.asFile }
      ) map { _.getAbsoluteFile } filter {  _.getName.endsWith(".jar") }
      
      def quote(s: Any) = '"' + s.toString + '"'
      log.debug("proguard configuration external dependencies: \n\t" + externalDependencies.mkString("\n\t"))
      // partition jars from the external jar dependencies of this project by whether they are located in the project directory
      // if they are, they are specified with -injars, otherwise they are specified with -libraryjars
      val (externalJars, libraryJars) = externalDependencies.toList.partition(jar => Path.relativize(rootProjectDirectory, jar).isDefined)
      log.debug("proguard configuration library jars locations: " + libraryJars.mkString(", "))
      // exclude properties files and manifests from scala-library jar
      val inJars = (quote(defaultJar) :: externalJars.map(quote(_) + "(!META-INF/**,!*.txt)")).map("-injars " + _).mkString("\n")
            
      val proguardConfiguration = outTemplate.stripMargin.format(libraryJars.map(quote).mkString(File.pathSeparator), inJars, quote(outputJar.absolutePath), mainClass.get)
      log.debug("Proguard configuration written to " + proguardConfigurationPath)
      FileUtilities.write(proguardConfigurationPath.asFile, proguardConfiguration, log)
    }
}
