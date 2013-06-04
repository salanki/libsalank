package st.salanki.libsalank
package snmp

import org.snmp4j.Snmp
import org.snmp4j.transport._
import org.snmp4j.smi._
import org.snmp4j.event._
import org.snmp4j._
import org.snmp4j.mp.SnmpConstants

/**
 * Factory for SimpleSnmp class
 *  
 * @author Peter Salanki
 */
object SimpleSnmp {
  def apply(bindAddress: String) = new SimpleSnmp(Some(bindAddress))
  def apply() = new SimpleSnmp(None)
  def apply(bindAddress: Option[String]) = new SimpleSnmp(bindAddress)
}

/**
 * Wrapper for SNMP4J to simplify sending SNMP messages
 * <p>
 * User should import org.snmp4j.smi._ and org.snmp4j.PDU
 * SimpleSnmp is also inherently thread safe as SNMP4J is so
 * 
 * @author Peter Salanki
 */
class SimpleSnmp(bindAddress: Option[String]) extends Logged {
  
  val transport = bindAddress match {
    case None => new DefaultUdpTransportMapping()
    case Some(address) => new DefaultUdpTransportMapping(new UdpAddress(address))
  }
  
  val snmp = new Snmp(transport)

  transport.listen()

  /**
   * Send an asynchronous SNMP request
   * 
   * @param targetIp		Target IP address
   * @param community	SNMP Community
   * @param pdu			Request PDU
   * @param retries		Number of retries
   * @param timeout		Timeout in milliseconds
   * @param handler		A handler method that is executed on every received response, takes a ResponseEvent as argument. It is super important that this never throws an exception, as this will crash the SNMP stack listen thread.
   */
  def send(targetIp: String, community: String, pdu: PDU, retries: Int, timeout: Long)(handler: (ResponseEvent) => Unit): Unit = {
    val target = new CommunityTarget()
    target.setCommunity(new OctetString(community))

    target.setAddress(GenericAddress.parse("udp:" + targetIp + "/161"))
    target.setRetries(retries)
    target.setTimeout(timeout)
    target.setVersion(SnmpConstants.version2c)

    val listener = new ResponseListener() {
      def onResponse(event: ResponseEvent) = {
        event.getSource().asInstanceOf[Snmp].cancel(event.getRequest(), this) /* Need to cancel async request not to cause memory leak */
        try {
          handler(event) /* Run supplied closure to handle the reply */
        } catch {
          case e: Exception => error("!!!!!!!! Do not let your handler throw an Exception, this will break the SNMP stack (but now we cought it, so you are fine): " + e, e)
        }
      }
    }

    /* Send  the request */
    snmp.send(pdu, target, null, listener)
  }

  /**
   * Send an asynchronous SNMP request
   * 
   * @param targetIp		Target IP address
   * @param community	SNMP Community, defaults to "public"
   * @param oids		An iterable of OIDs that will go into the request
   * @param pduType		Type of the request PDU, defaults to GET
   * @param maxRepetitions	Maximum number of repetitions for a GETBULK, defaults to 500
   * @param nonRepeaters	Number of non repeater variable bindings for a GETBULK, defaults to 0
   * @param retries		Number of retries, defaults to 2
   * @param timeout		Timeout in milliseconds, defaults to 1000
   * @param handler		A handler method that is executed on  received response and timeout, takes a ResponseEvent as argument. It is super important that this never throws an exception, as this will crash the SNMP stack listen thread.
   */
  def send(targetIp: String, community: String = "public", oids: Iterable[OID], pduType: Int = PDU.GET, maxRepetitions: Int = 500, nonRepeaters: Int = 0, retries: Int = 2, timeout: Long = 1000)(handler: (ResponseEvent) => Unit): Unit = {
    val pdu = new PDU()

    for (oid <- oids) pdu.add(new VariableBinding(oid))
    pdu.setType(pduType)
    pdu.setMaxRepetitions(maxRepetitions)
    pdu.setNonRepeaters(nonRepeaters)

    send(targetIp, community, pdu, retries, timeout)(handler)
  }
  
 /**
   * Start an asynchronous iteration of SNMP GETBULKs
   * <p>
   * Will continue sending GETBULK requests until the entire requested OID is walked, results will be buffered internally and the handler is only executed with all the results when the last result is received.
   * 
   * @param targetIp		Target IP address
   * @param community	SNMP Community, defaults to "public"
   * @param oid			An OID to walk
   * @param maxRepetitions	Maximum number of repetitions for a GETBULK, defaults to 500
   * @param nonRepeaters	Number of non repeater variable bindings for a GETBULK, defaults to 0
   * @param retries		Number of retries, defaults to 2
   * @param timeout		Timeout in milliseconds, defaults to 1000
   * @param handler		A handler method that is executed once the operation is finished, will be called with None if a timeout occurred, otherwise a Some[List[VariableBinding]] containing zero or more entries. It is super important that this never throws an exception, as this will crash the SNMP stack listen thread.
   */
  def sendContinuousGetBulk(
		  targetIp: String,
		  community: String = "public",
		  oid: OID,
		  maxRepetitions: Int = 500,
		  nonRepeaters: Int = 0,
		  retries: Int = 2,
		  timeout: Long = 1000)(handler: (Option[List[VariableBinding]]) => Unit): Unit = {
    /* Build target */
    val target = new CommunityTarget()
    target.setCommunity(new OctetString(community))

    target.setAddress(GenericAddress.parse("udp:" + targetIp + "/161"))
    target.setRetries(retries)
    target.setTimeout(timeout)
    target.setVersion(SnmpConstants.version2c)

    /* Build PDU that we will re-use during all our iterations */
    val pdu = new PDU()

    pdu.setType(PDU.GETBULK)
    pdu.setMaxRepetitions(maxRepetitions)
    pdu.setNonRepeaters(nonRepeaters)

    def getBulkSet(start: OID, listener: ResponseListener): Unit = {
      pdu.clear()
      pdu.add(new VariableBinding(start))
      /* Send the request */
      snmp.send(pdu, target, null, listener)
    }

    /* We create an new object that SNMP4J will call on a reply, this object parses the result and might send a new request if there are still entries left to get */
    val listener = new ResponseListener() {
      /* Mutable buffer where we will store the resulting VarBindings until we have reached the end and send it off to the handler */
      val resultSet = collection.mutable.ListBuffer[VariableBinding]()
      var itercount = 0

      /* Wrap handler in a try catch block for extra protection */
      private def runHandler(data: Option[List[VariableBinding]]) = try {
        handler(data) /* Run supplied closure to handle the reply */
      } catch {
        case e: Exception => error("!!!!!!!! Do not let your handler throw an Exception, this will break the SNMP stack: " + e, e)
      }

      def onResponse(event: ResponseEvent) = {
        event.getSource().asInstanceOf[Snmp].cancel(event.getRequest(), this) /* Need to cancel async request not to cause memory leak */

        val response = event.getResponse
        event.getResponse match {
          case null => runHandler(None)
          case response => {
            itercount += 1

            for(x <- response.toArray if x.getOid.startsWith(oid)) resultSet += x /* Valid OID, adding to resultset */
            
            if(response.toArray.find(!_.getOid.startsWith(oid)) != None) {
                /* We got a new OID, which means that we have all the results for our original request */
                debug("Getbulk finished, iterations: " + itercount + " items: " + resultSet.size)
                
                runHandler(Some(resultSet.toList))
            } else getBulkSet(resultSet.last.getOid, this) /* Carry on */
          }
        }
      }
    }

    /* Kick it off */
    getBulkSet(oid, listener)
  }
}