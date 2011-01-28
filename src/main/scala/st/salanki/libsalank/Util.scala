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
  def debug(message:String, values:Any*): Unit
  
  def debugTimer(name: Symbol) = debug(name + " took " + timer.getS(name) + "s and " + (timer.getMs(name) - timer.getS(name)*1000) +"ms")
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
    
  def trace(message:String, values:Any*) = logInstance.trace(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  def trace(message:String, error:Throwable) = logInstance.trace(prependStr + message, error)
  def trace(message:String, error:Throwable, values:Any*) = logInstance.trace(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  def debug(message:String, values:Any*) = logInstance.debug(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  def debug(message:String, error:Throwable) = logInstance.debug(prependStr + message, error)
  def debug(message:String, error:Throwable, values:Any*) = logInstance.debug(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  def info(message:String, values:Any*) = logInstance.info(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  def info(message:String, error:Throwable) = logInstance.info(prependStr + message, error)
  def info(message:String, error:Throwable, values:Any*) =  logInstance.info(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  def warn(message:String, values:Any*) = logInstance.warn(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  def warn(message:String, error:Throwable) = logInstance.warn(prependStr + message, error)
  def warn(message:String, error:Throwable, values:Any*) =  logInstance.warn(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
	 
  def error(message:String, values:Any*) =  logInstance.error(prependStr + message, values.map(_.asInstanceOf[Object]).toArray)
  def error(message:String, error:Throwable) = logInstance.error(prependStr + message, error)
  def error(message:String, error:Throwable, values:Any*) = logInstance.error(prependStr + message, error, values.map(_.asInstanceOf[Object]).toArray)
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