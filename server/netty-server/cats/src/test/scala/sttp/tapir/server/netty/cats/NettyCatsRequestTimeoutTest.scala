package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.netty.channel.EventLoopGroup
import org.scalatest.matchers.should.Matchers.*
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.tests.Test

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
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

      val streamingEndpoint2: Endpoint[Unit, (Long, Stream[IO, Byte]), Unit, (Long, Stream[IO, Byte]), Fs2Streams[IO]] =
        endpoint.post
          .in(header[Long](HeaderNames.ContentLength))
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))

      val endpointModel2: PublicEndpoint[(Long, fs2.Stream[IO, Byte]), Unit, (Long, fs2.Stream[IO, Byte]), Fs2Streams[IO]] =
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
      ] = endpointModel2
        .serverLogicSuccess[IO] { case (length, stream) =>
          IO((length, stream))
        }

      val config =
        NettyConfig.default
          .eventLoopGroup(eventLoopGroup)
          .randomPort
          .withDontShutdownEventLoopGroupOnClose
          .noGracefulShutdown
          .requestTimeout(500.millis) // ???

      val bind = NettyCatsServer(dispatcher, config).addEndpoint(e).start()

      val howManyChars: Int = 20

      def iterator(howManyChars: Int): Iterator[Byte] = new Iterator[Byte] {
        private var charsToGo: Int = howManyChars

        def hasNext: Boolean = {
          Thread.sleep(3000)
          charsToGo > 0
        }

        def next(): Byte = {
          charsToGo -= 1
          'A'.toByte
        }
      }

      val inputStream = fs2.Stream.fromIterator[IO](iterator(howManyChars), chunkSize = 10)

      Resource
        .make(bind)(_.stop())
        .map(_.port)
        .use { port =>
            basicRequest
            .post(uri"http://localhost:$port")
            .contentLength(howManyChars)
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
            .map { response =>
              println("zrobione")
              fail("I've got a bad feeling about this.")
//              response.body shouldBe Right("AAAAAAAAAAAAAAAAAAAA")
//              response.contentLength shouldBe Some(howManyChars)
            }
        }
//        .attempt
//        .map {
//          case Left(ex: sttp.client3.SttpClientException.TimeoutException) => ex.getMessage shouldBe "request timed out"
//          case Left(ex) => fail(s"Unexpected exception: $ex")
//          case Right(_) => fail("Expected an exception but got success")
//        }
        .unsafeToFuture()
    }
  )
}
