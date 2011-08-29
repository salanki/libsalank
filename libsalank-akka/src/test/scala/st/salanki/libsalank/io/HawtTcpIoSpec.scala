package st.salanki.libsalank
package io

import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.BeforeAndAfterEach

import akka.actor.{ Actor, ActorRef }
import akka.dispatch.HawtDispatcher
import java.nio.ByteBuffer
import java.net.{ ServerSocket, Socket, SocketAddress, InetSocketAddress }
import java.io.{ BufferedReader, InputStreamReader }

object Shared {
  val port = 9000 + ((new java.util.Random).nextInt % 500)
  val hawtDispatcher = new HawtDispatcher
}

/* Messages for TcpTestActor */
case object Accept
case object Close

case object GetData
case object Success
case object Fail

/** Test fixture to verify operations **/
class TcpTestActor extends Actor {
  var server: ServerSocket = null
  var connection: Socket = null

  override def preStart = {
    server = new ServerSocket(Shared.port)
    server.setSoTimeout(5000) /* Timeout all operations in 5s, should be enough time for our tests */
  }

  override def postStop = {
    if (connection != null) connection.close()
    if (server != null) server.close()
  }

  private def accept() = {
    try {
      connection = server.accept()
      self.reply(Success)
    } catch {
      case e: java.net.SocketTimeoutException => self.reply(Fail)
    }
  }

  def getData() = {
    val is = connection.getInputStream()
    val reader = new BufferedReader(new InputStreamReader(is))

    self.reply(reader.readLine)
  }

  def receive = {
    case Close => connection.close()
    case Accept => accept()
    case GetData => getData()

    case msg => error("received unknown message: " + msg)
  }
}

/* Messages for HawtTcpClientTestActor */
case object Start
case object GetCrashState
case object GetBindExceptionState
case object GetConnectFailedState
case object GetOnConnectState
case object GetConnectedState
case class Send(data: List[String])

/** Our actor using the HawtTcpClient, should also be a good simple example of usage */
class HawtTcpClientTestActor(val bindAddress: Option[SocketAddress] = None) extends Actor {
  self.dispatcher = Shared.hawtDispatcher
  var crash = false
  var bindException = false
  var connectFailed = false
  var onConnectRun = false

  val io = new HawtTcpClient(self, new java.net.InetSocketAddress("localhost", Shared.port), msgHandler, crashHandler, onConnect, bindAddress, tcpNoDelay = true)

  override def preStart = {}

  private def msgHandler(packet: ByteBuffer) = {

  }

  private def onConnect() = onConnectRun = true

  private def crashHandler(e: Exception) {
    println("CRASH: ", e)
    crash = true
    e match {
      case e: java.net.ConnectException => connectFailed = true
      case _ =>
    }
  }

  private def start = {
    crash = false
    connectFailed = false
    try {
      io.start()
    } catch {
      case e: java.net.BindException => bindException = true
    }
  }

  def receive = {
    case Start => start
    case GetCrashState => self.reply(crash)
    case GetBindExceptionState => self.reply(bindException)
    case GetConnectFailedState => self.reply(connectFailed)
    case GetOnConnectState => self.reply(onConnectRun)
    case GetConnectedState => self.reply(io.connected)
    case Send(data) => data.foreach { row => io.enqueuePacket(row.getBytes) }

    case msg => error("received unknown message: " + msg)
  }

  override def postStop = {
    io.stop()
  }
}

class HawtTcpIoConnectSpec extends FeatureSpec with GivenWhenThen with ShouldMatchers {

  info("Using port: " + Shared.port)

  feature("A TCP Connection can be created") {
    scenario("start is invoked with an existing listening host") {
      given("an actor listening")
      val listener = Actor.actorOf(new TcpTestActor).start()

      given("an actor connecing with HawtTcpClient")
      val io = Actor.actorOf(new HawtTcpClientTestActor).start()

      when("The actor is started")

      then("The onConnect trigger should NOT have been run")
      (io !!! GetOnConnectState).await.result should be(Some(false))

      then("The client should know that it is NOT connected")
      (io !!! GetConnectedState).await.result should be(Some(false))

      when("TcpClient is connecting")
      val listenFuture = listener !!! Accept
      io ! Start

      then("The listener should have accepted the connection")
      listenFuture.await.result should be(Some(Success))

      then("The client should NOT have yielded an error")
      (io !!! GetCrashState).await.result should be(Some(false))

      Thread.sleep(100) /* Want to make sure that messages had time to pass */
      then("The onConnect trigger should have been run")
      (io !!! GetOnConnectState).await.result should be(Some(true))

      then("The client should know that it is connected")
      (io !!! GetConnectedState).await.result should be(Some(true))

      /* Cleanup */
      io.stop()
      listener.stop()
    }

    scenario("start is invoked without a listening host") {
      given("an actor connecing with HawtTcpClient")
      val io = Actor.actorOf(new HawtTcpClientTestActor).start()

      when("TcpClient is connecting")
      io ! Start

      Thread.sleep(1000)

      then("The client should have yielded a connection error")
      (io !!! GetConnectFailedState).await.result should be(Some(true))

      then("The onConnect trigger should NOT have been run")
      (io !!! GetOnConnectState).await.result should be(Some(false))

      then("The client should know that it is NOT connected")
      (io !!! GetConnectedState).await.result should be(Some(false))

      /* Cleanup */
      io.stop()
    }
  }

  feature("Binding") {
    scenario("start is invoked with an invalid bind-address") {
      given("an actor connecing with HawtTcpClient")
      val io = Actor.actorOf(new HawtTcpClientTestActor(Some(new java.net.InetSocketAddress("193.25.199.1", 53)))).start()

      when("TcpClient is connecting")
      io ! Start

      Thread.sleep(1000)

      then("The client should have yielded a bind exception")
      (io !!! GetBindExceptionState).await.result should be(Some(true))

      /* Cleanup */
      io.stop()
    }
  }
}

class HawtTcpClientSpec extends FeatureSpec with GivenWhenThen with ShouldMatchers with BeforeAndAfterEach {
  private var listener: akka.actor.ActorRef = _
  private var io: akka.actor.ActorRef = _

  override def beforeEach() {
    listener = Actor.actorOf(new TcpTestActor).start()
    io = Actor.actorOf(new HawtTcpClientTestActor).start()

    val listenFuture = listener !!! Accept
    io ! Start

    listenFuture.await.result should be(Some(Success))
    (io !!! GetCrashState).await.result should be(Some(false))

  }

  override def afterEach() {
    io.stop()
    listener.stop()
  }

  feature("A remote close can be detected") {
    scenario("a remote host closes the connection") {

      when("the listener closes the connection")
      listener ! Close

      Thread.sleep(1000)

      then("The client should have yielded an error")
      (io !!! GetCrashState).await.result should be(Some(true))

    }
  }

  feature("Can send data to a remote socket") {
    scenario("sending a short line of data") {
      val data = List("data\n")

      when("sending data")
      io ! Send(data)

      then("the listener should have received the same data")
      (listener !!! GetData).await.result should be(Some("data"))
    }

    scenario("sending a large amount of data in one chunk")(pending)
    scenario("sending a large amount of data in a lot of smaller chunks")(pending)
  }

  feature("Can receive data from a remote socket") {
    scenario("receiving a small amount of data")(pending)
    scenario("receiving a large amount of data")(pending)
  }
}