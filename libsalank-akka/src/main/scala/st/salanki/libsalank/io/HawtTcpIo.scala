package st.salanki.libsalank
package io

import akka.actor.{ Actor, ActorRef }
import java.nio.channels.{ SelectionKey, SocketChannel }
import java.net.{ SocketAddress, InetSocketAddress }
import java.io.IOException
import org.fusesource.hawtdispatch.DispatchSource
import org.fusesource.hawtdispatch.ScalaDispatch._
import java.nio.ByteBuffer
import scala.collection.mutable.Queue
import akka.dispatch.HawtDispatcher

/** Thrown if channel.finishConnect() doesn't return true */
class ConnectionFinaliztionException extends IOException("Connection failed")

/**
 * TCP IO Client using HawtDispatch
 * <p>
 * Easy TCP IO Client for actor using HawtDispatcher.<br/>
 * Actor preStart() should call start()<br/>
 * Actor postStop and preRestart should call stop()<br/>
 * HawtUdpIo is automatically stopped on a socket crash (I/O Exception mostly) and needs to be manually restarted. If the <code>crashError</code> is used to restart the parent actor the IO instance will also be restarted automatically as start() is run in actor preStart()<br/>
 * Connects are nonblocking, if a connection fails java.net.ConnectException will be sent to the crashHandler. Packets can be safely enqueued while connection is in process.
 *
 * @param	actor			ActorRef to parent actor, just pass <code>self</code> in here
 * @param	target			Server to connect to
 * @param	packetHandler	Function that is executed on all received packets on the socket. The <code>ByteBuffer</code> supplied is mutable and reused for each packet, do not let it leave the function.
 * @param 	crashHandler	Function that is executed on a socket crash (I/O Exception mostly). If using actor supervision a standard implementation would be: <code>{e => error("I/O Error", e); ActorShared.supervisor ! Exit(self, e)}</code>
 * @param	onConnect		Function that is executed when the TCP socket is connected. Does not have to be defined.
 * @param	bindAddress		Optional SocketAddress to bind to
 * @param	pin				Pin actor so it is always executed on the selector thread
 * @param	keepAlive		Enable TCP KeepAlive. Defaults to true
 * @param	tcpNoDelay		Enable TCP NoDelay. Defaults to false
 * @param	readBufferSize	Read buffer size. Defaults to 4096
 */
class HawtTcpClient(actor: ActorRef, target: SocketAddress, packetHandler: ByteBuffer => Unit, crashHandler: (Exception) => Unit, onConnect: () => Unit = () => (), bindAddress: Option[SocketAddress] = None, pin: Boolean = false, keepAlive: Boolean = true, tcpNoDelay: Boolean = false, readBufferSize: Int = 4096) {
  private var channel: SocketChannel = _
  private var connectSource: DispatchSource = _
  private var writeSource: DispatchSource = _
  private var readSource: DispatchSource = _
  private val readBuffer: ByteBuffer = ByteBuffer.allocate(readBufferSize)
  private var writeBuffer: Option[ByteBuffer] = None

  private var closed = false
  var writeCounter = 0L
  var readCounter = 0L
  private val writeQueue = Queue[Array[Byte]]()

  /** Allocate and start socket for communication to DHCP Server */
  def start() = {
    channel = SocketChannel.open()
    channel.configureBlocking(false)
    channel.socket.setKeepAlive(keepAlive)
    channel.socket.setTcpNoDelay(tcpNoDelay)
    bindAddress.foreach { channel.socket().bind(_) }

    channel.connect(target)

    connectSource = createSource(channel, SelectionKey.OP_CONNECT, HawtDispatcher.queue(actor))
    connectSource.setEventHandler(^ { connectOp })
    connectSource.resume

    if (pin) HawtDispatcher.pin(actor)
  }

  /** On connect operation */
  private def connectOp() =
    try {
      if (channel.finishConnect()) {
        /* Connection successful */
        readSource = createSource(channel, SelectionKey.OP_READ, HawtDispatcher.queue(actor))
        readSource.setEventHandler(^ { read })
        readSource.setCancelHandler(^ { socketCrash(new IOException("Connection was closed")) })

        writeSource = createSource(channel, SelectionKey.OP_WRITE, HawtDispatcher.queue(actor))
        writeSource.setEventHandler(^ { write })
        writeSource.setCancelHandler(^ { socketCrash(new IOException("Connection was closed")) })

        connectSource.release
        connectSource = null
        
        readSource.resume
        if(!writeQueue.isEmpty) writeSource.resume
        
        onConnect()
      } else {
        /* Connection failed */
        socketCrash(new ConnectionFinaliztionException)
      }
    } catch {
      case e: IOException => socketCrash(e)
    }

  /** Place a packet in the send-queue and enable writing */
  def enqueuePacket(packet: Array[Byte]) = {
    if (writeQueue.isEmpty && writeSource != null) writeSource.resume
    writeQueue += packet
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
    channel.read(readBuffer) match {
      case -1 => socketCrash(new IOException("Connection was closed"))
      case 0 =>
      case count => {
        readCounter += count
        packetHandler(readBuffer)
        readBuffer.clear
      }
    }
  }

  /** Write data to socket */
  private def write() = catchIo {
    /* If the write buffer is empty, we get a new packet and target from our queue. The queue should always have at least one item at this stage */
    if (writeBuffer == None) {
      val packet = writeQueue.dequeue
      writeBuffer = Some(ByteBuffer.wrap(packet))
    }

    /* Write it */
    writeCounter += channel.write(writeBuffer.get)

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

  
  /** Check if socket is connected */
  def connected: Boolean = writeSource != null
  
  /**
   * Stop and shutdown the socket
   * <p>
   * Should be called in actor postStop and preRestart, automatically called on socket crash (I/O Exception and such)
   */
  def stop() = {
    if (!closed) {
      closed = true
      if (connectSource != null) connectSource.release
      if (writeSource != null) {
        writeSource.setCancelHandler(^ {})
        writeSource.release
        writeSource = null
      }
      if (readSource != null) {
        readSource.setCancelHandler(^ {})
        readSource.release
        readSource = null
      }
      if (channel != null) channel.close
    }
  }

  override def toString = "HawtTcpIO for: " + actor +" server: " +target + " connected: " + connected
}
