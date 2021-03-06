package st.salanki.libsalank

/**
 * Simple class for getting and setting different timers (stopwatches)
 * 
 * @author Peter Salanki
 */	 
class Timer {
  import scala.compat.Platform.currentTime
  //import com.twitter.ostrich.Stats
    
  private val TimeMap = collection.mutable.Map[Symbol, Long]()
  
  def getMs(name: Symbol) = currentTime - TimeMap(name)
  def getMsStat(name: Symbol) = {
	//  Stats.addTiming(name.name, currentTime - TimeMap(name))
	  currentTime - TimeMap(name)
  }
  def getS(name: Symbol) = getMs(name)/1000
  def getSStat(name: Symbol) = getMsStat(name)/1000
  def apply(name: Symbol) = getMs(name)
  def set(name: Symbol) = TimeMap(name) = currentTime
}

/**
 * Trait that simplifies adding timers to your class
 * 
 * @author Peter Salanki
 */	 
trait Timed {
  val timer = new Timer()
}

/**
 * Trait that simplifies adding timers to your class and also adds method for logging times
 * 
 * @author Peter Salanki
 */	
trait LogTimed extends Timed {
  protected def debug(message:String, values:Any*): Unit
  
  protected def debugTimer(name: Symbol) = debug(name + " took " + timer.getS(name) + "s and " + (timer.getMs(name) - timer.getS(name)*1000) +"ms")
}

/**
 * Wrapper for SLF4j with added support for added message prepend
 * 
 * @author Joakim Ohlrogge
 * @author Peter Salanki
 */	 
trait Logged {
  import org.slf4j.{Logger, LoggerFactory}
  
  protected var logName: String = getClass.toString
  protected var prependStr: String  = ""

  private lazy val logInstance: org.slf4j.Logger = LoggerFactory.getLogger(logName) 
    
  protected def trace(message:String, values:Any*) = logInstance.trace(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  protected def trace(message:String, error:Throwable) = logInstance.trace(prependStr + message, error)
  protected def trace(message:String, error:Throwable, values:Any*) = logInstance.trace(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  protected def debug(message:String, values:Any*) = logInstance.debug(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  protected def debug(message:String, error:Throwable) = logInstance.debug(prependStr + message, error)
  protected def debug(message:String, error:Throwable, values:Any*) = logInstance.debug(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  protected def info(message:String, values:Any*) = logInstance.info(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  protected def info(message:String, error:Throwable) = logInstance.info(prependStr + message, error)
  protected def info(message:String, error:Throwable, values:Any*) =  logInstance.info(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  protected def warn(message:String, values:Any*) = logInstance.warn(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  protected def warn(message:String, error:Throwable) = logInstance.warn(prependStr + message, error)
  protected def warn(message:String, error:Throwable, values:Any*) =  logInstance.warn(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  protected def error(message:String, values:Any*) =  logInstance.error(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  protected def error(message:String, error:Throwable) = logInstance.error(prependStr + message, error)
  protected def error(message:String, error:Throwable, values:Any*) = logInstance.error(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
}

/**
 * Wrapper for SLF4j with added message formatting for printing a name in front of the log message
 * 
 * @author Joakim Ohlrogge
 * @author Peter Salanki
 */	 
trait NameLogged extends Logged {
  val name: String
  
  logName = getClass.toString
  prependStr = "["+ name + "] "
}

/**
 * Simple interface to work with CSV Files, using the Opencsv Java CSV library
 *
 * @author Peter Salanki
 */
object CSV {
  import au.com.bytecode.opencsv.{CSVWriter, CSVReader}
  import java.io.{FileWriter, FileReader}
 // import scala.collection.JavaConversions
  
  trait PimpedWriter { this: CSVWriter =>
     def writeNext(in: Iterable[String]): Unit = writeNext(in.toArray)
     def writeNext(in: Any*): Unit = writeNext(in.map(_.toString))
	 def writeAll(in: Iterable[Iterable[String]]): Unit = in.foreach(a => writeNext(a.toArray))
  }
  
  trait PimpedReader { this: CSVReader =>
  	def toList = {
  		import scala.collection.JavaConversions._
  		this.readAll.toList
  	}
  }
  
  def withCSVWriter(fileName: String)(block: (CSVWriter with PimpedWriter) => Unit) {
    val writer = new CSVWriter(new FileWriter(fileName)) with PimpedWriter

    try {
      block(writer)
    } finally {
      writer.close()
    }
  }
  
 def withCSVReader[A](fileName: String)(block: (CSVReader with PimpedReader) => A): A = {
    val reader = new CSVReader(new FileReader(fileName)) with PimpedReader

    try {
      block(reader)
    } finally {
      reader.close()
    }
  }
}