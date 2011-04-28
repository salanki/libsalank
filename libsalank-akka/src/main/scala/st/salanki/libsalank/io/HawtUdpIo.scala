package st.salanki.libsalank
package io

import akka.actor.{ Actor, ActorRef }
import java.nio.channels.{ SelectionKey, DatagramChannel }
import java.net.{ SocketAddress, InetSocketAddress }
import java.io.IOException
import org.fusesource.hawtdispatch.DispatchSource
import org.fusesource.hawtdispatch.ScalaDispatch._
import java.nio.ByteBuffer
import scala.collection.mutable.Queue
import akka.dispatch.HawtDispatcher

/**
 * UDP IO using HawtDispatch
 * <p>
 * Easy UDP IO for actor using HawtDispatcher.<br/>
 * Actor preStart() should call start()<br/>
 * Actor postStop and preRestart should call stop()<br/>
 * HawtUdpIo is automatically stopped on a socket crash (I/O Exception mostly) and needs to be manually restarted. If the <code>crashError</code> is used to restart the parent actor the IO instance will also be restarted automatically as start() is run in actor preStart()<br/>
 * 
 * @param	actor			ActorRef to parent actor, just pass <code>self</code> in here
 * @param	packetHandler	Function that is executed on all received packets on the socket. The <code>ByteBuffer</code> supplied is mutable and reused for each packet, do not let it leave the function. Not catching Exceptions in your code here might lead to socket stalling. 
 * @param 	crashHandler	Function that is executed on a socket crash (I/O Exception mostly). If using actor supervision a standard implementation would be: <code>{e => error("I/O Error", e); ActorShared.supervisor ! Exit(self, e)}</code>
 * @param	bindAddress		Optional SocketAddress to bind to
 * @param	pin				Pin actor so it is always executed on the selector thread
 * @param	readBufferSize	Read buffer size. Defaults to 4096
 */
class HawtUdpIo(actor: ActorRef, packetHandler: (ByteBuffer, SocketAddress) => Unit, crashHandler: (Exception) => Unit, bindAddress: Option[SocketAddress] = None, pin: Boolean = false, readBufferSize: Int = 4096) {
  private var channel: DatagramChannel = _
  private var writeSource: DispatchSource = _
  private var readSource: DispatchSource = _
  private val readBuffer: ByteBuffer = ByteBuffer.allocate(readBufferSize) /* Random number */
  private var writeBuffer: Option[ByteBuffer] = None
  private var writeTarget: InetSocketAddress = _

  private var closed = false
  var writeCounter = 0L
  val writeQueue = Queue[(InetSocketAddress, Array[Byte])]()

  /** Allocate and start socket for communication to DHCP Server */
  def start() = {
    channel = DatagramChannel.open()
    channel.configureBlocking(false)
    bindAddress.foreach { channel.socket().bind(_) }

    readSource = createSource(channel, SelectionKey.OP_READ, HawtDispatcher.queue(actor));
    readSource.setEventHandler(^ { read })

    writeSource = createSource(channel, SelectionKey.OP_WRITE, HawtDispatcher.queue(actor));
    writeSource.setEventHandler(^ { write })

    readSource.resume
    if(!writeQueue.isEmpty) writeSource.resume

    if(pin) HawtDispatcher.pin(actor)
  }

  /** Place a packet in the send-queue and enable writing */
  def enqueuePacket(packet: Array[Byte], target: InetSocketAddress, prepend: Boolean = false) = {
    if (writeQueue.isEmpty && writeSource != null) writeSource.resume
    
    if(prepend) Pair(target, packet) +: writeQueue
    else writeQueue += Pair(target, packet)
  }

  /** Catch IO Exception */
  private def catchIo(func: => Unit): Unit = {
    try {
      func
    } catch {
      case e: IOException => socketCrash(e)
    }
  }

  /** Read data from socket */
  private def read(): Unit = catchIo {
    channel.receive(readBuffer) match {
      case null => // "Should this really be able to happen?"
      case peer: SocketAddress => {
        packetHandler(readBuffer, peer)
        readBuffer.clear
      }
    }
  }

  /** Write data to socket */
  private def write() = catchIo {
    /* If the write buffer is empty, we get a new packet and target from our queue. The queue should always have at least one item at this stage */
    if (writeBuffer == None) {
      val (target, packet) = writeQueue.dequeue
      writeBuffer = Some(ByteBuffer.wrap(packet))
      writeTarget = target
    }

    /* Write it */
    writeCounter += channel.send(writeBuffer.get, writeTarget)

    /* If we have written everything in this buffer, clear it. If the queue is empty, suspend the write source */
    if (writeBuffer.get.remaining == 0) {
      writeBuffer = None
      if (writeQueue.isEmpty) writeSource.suspend
    }
  }

  /** Shutdown due to socket IO error and call crashHandler */
  private def socketCrash(e: Exception) = {
    stop()
    crashHandler(e) 
  }

  /** 
   * Stop and shutdown the socket
   * <p>
   * Should be called in actor postStop and preRestart, automatically called on socket crash (I/O Exception and such) 
   */
  def stop() = {
    if (!closed) {
      closed = true
      if (writeSource != null) {
        writeSource.release
        writeSource = null
      }
      if (readSource != null) {
        readSource.release
        readSource = null
      }
      if (channel != null) channel.close
    }
  }
  
  override def toString = "HawtUdpIO for: " + actor
}

