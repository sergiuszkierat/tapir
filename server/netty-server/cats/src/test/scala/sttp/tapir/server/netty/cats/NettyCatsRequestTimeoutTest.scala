package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.netty.channel.EventLoopGroup
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model.{HeaderNames}
import sttp.tapir._
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.tests.Test

import scala.concurrent.{ExecutionContext}
import scala.concurrent.duration.DurationInt
import sttp.tapir.server.ServerEndpoint

import java.net.http.HttpTimeoutException

class NettyCatsRequestTimeoutTest(
    dispatcher: Dispatcher[IO],
    eventLoopGroup: EventLoopGroup,
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]
)(implicit
    ec: ExecutionContext
) {
  def tests(): List[Test] = List(
    Test("part data send") {

      val streamingEndpoint2: PublicEndpoint[(Long, fs2.Stream[IO, Byte]), Unit, (Long, fs2.Stream[IO, Byte]), Fs2Streams[IO]] =
        endpoint.post
          .in(header[Long](HeaderNames.ContentLength))
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))

      val e: ServerEndpoint.Full[
        Unit,
        Unit,
        (Long, fs2.Stream[IO, Byte]),
        Unit,
        (Long, fs2.Stream[IO, Byte]),
        Fs2Streams[IO],
        IO
      ] = streamingEndpoint2
        .serverLogicSuccess[IO] { case (length, stream) =>
          IO((length, stream))
        }

      val config =
        NettyConfig.default
          .eventLoopGroup(eventLoopGroup)
          .randomPort
          .withDontShutdownEventLoopGroupOnClose
          .noGracefulShutdown
          .requestTimeout(2.second) // ???
//          .idleTimeout(300.millis) //???

      val bind = NettyCatsServer(dispatcher, config).addEndpoint(e).start()

      val howManyChunks: Int = 2
      val chunkSize = 100

      def iterator(howManyChunks: Int): Iterator[Byte] = new Iterator[Iterator[Byte]] {
        private var chunksToGo: Int = howManyChunks

        def hasNext: Boolean = {
          println(s"hasNext $chunksToGo")
          Thread.sleep(3000)
          println(s"hasNext $chunksToGo after sleep")
          chunksToGo > 0
        }

        def next(): Iterator[Byte] = {
          println(s"next $chunksToGo")
//          Thread.sleep(3000)
          chunksToGo -= 1
          println(s"next $chunksToGo after sleep")
          List.fill('A')(chunkSize).map(_.toByte).iterator
        }
      }.flatten

      val inputStream = fs2.Stream.fromIterator[IO](iterator(howManyChunks), chunkSize = chunkSize)

      Resource
        .make(bind)(_.stop())
        .map(_.port)
        .use { port =>
          basicRequest
            .post(uri"http://localhost:$port")
            .contentLength(howManyChunks * chunkSize)
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
//            .timeout(1.second)
            .map { response =>
              println("zrobione")
              println(response)
              fail("I've got a bad feeling about this.")
//              response.body shouldBe Right("AAAAAAAAAAAAAAAAAAAA")
//              response.contentLength shouldBe Some(howManyChars)
            }
        }
        .attempt
        .map {
          case Left(ex: sttp.client3.SttpClientException.TimeoutException) =>
            ex.getCause.getMessage shouldBe "request timed out"
          case Left(ex: sttp.client3.SttpClientException.ReadException) if ex.getCause.isInstanceOf[java.io.IOException] =>
            fail(s"Unexpected IOException: $ex")
          case Left(ex) =>
            fail(s"Unexpected exception: $ex")
          case Right(_) =>
            fail("Expected an exception but got success")
        }
        .unsafeToFuture()
    }
  )
}
