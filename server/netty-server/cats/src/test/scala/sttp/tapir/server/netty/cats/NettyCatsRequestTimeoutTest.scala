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
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir._
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

class NettyCatsRequestTimeoutTest(
    dispatcher: Dispatcher[IO],
    eventLoopGroup: EventLoopGroup,
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]
)(implicit
    ec: ExecutionContext
) {
  def tests(): List[Test] = List(
    Test("part data send") {
//      val e: PublicEndpoint[(Long, fs2.Stream[cats.effect.IO, Byte]), Unit, (Long, fs2.Stream[cats.effect.IO, Byte]), Fs2Streams[IO]] =
//        sttp.tapir.tests.Streaming.in_stream_out_stream_with_content_length(Fs2Streams[IO])

//      val e1 = endpoint.post
      //        .in("api" / "echo")
//        .in(header[Long](HeaderNames.ContentLength))
//        .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
//        .out(header[Long](HeaderNames.ContentLength))
//        .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain())
//        .serverLogicSuccess[Future] { case (length, stream) =>
//          Future.successful((length, stream))
//        }
//        .serverLogicSuccess[Future] { body =>
//          Future.successful(body)
//        }

      import sttp.tapir.server.ServerEndpoint

//      val e: ServerEndpoint.Full[Unit, Unit, String, Unit, String, Any, Future] = endpoint.post
//        .in(stringBody)
//        .out(stringBody)
//        .serverLogicSuccess[Future] { body =>
//          Thread.sleep(2000); Future.successful(body)
//        }

//      val endpointModel: PublicEndpoint[ZStream[Any, Throwable, Byte], Unit, ZStream[Any, Throwable, Byte], ZioStreams] =
//        endpoint.post
//          .in("hello")
//          .in(streamBinaryBody(ZioStreams)(CsvCodecFormat))
//          .out(streamBinaryBody(ZioStreams)(CsvCodecFormat))

      val streamingEndpoint: PublicEndpoint[Unit, Unit, (Long, Stream[IO, Byte]), Fs2Streams[IO]] =
        endpoint.get
          .in("receive")
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

      val streamingEndpoint2: Endpoint[Unit, (Long, Stream[IO, Byte]), Unit, (Long, Stream[IO, Byte]), Fs2Streams[IO]] =
        endpoint.post
          .in(header[Long](HeaderNames.ContentLength))
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))

      val serverEndpoint2: ServerEndpoint[Fs2Streams[IO], Future] = streamingEndpoint2
        .serverLogicSuccess { body => Future.successful(body) }

      val endpointModel2: PublicEndpoint[(Long, fs2.Stream[IO, Byte]), Unit, (Long, fs2.Stream[IO, Byte]), Fs2Streams[IO]] =
        Endpoint[Unit, Unit, Unit, Unit, Any](
          emptyInput,
          emptyInput,
          emptyOutput,
          emptyOutput,
          EndpointInfo(None, None, None, Vector.empty, deprecated = false, AttributeMap.Empty)
        ).post
//        endpoint.post
          .in(header[Long](HeaderNames.ContentLength))
//          .in[fs2.Stream[IO, Byte], fs2.Stream[IO, Byte], (Long, fs2.Stream[IO, Byte]), Any](
//            streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain())
//          )
          .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
          .out(header[Long](HeaderNames.ContentLength))
          .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))

//      val e: ServerEndpoint.Full[Unit, (Long, fs2.Stream[cats.effect.IO, Byte]), Unit, String, Any, Future] = endpoint.post
      val e: ServerEndpoint.Full[
        Unit,
        Unit,
        (Long, fs2.Stream[IO, Byte]),
        Unit,
        (Long, fs2.Stream[IO, Byte]),
        Fs2Streams[IO],
        IO
      ] = endpointModel2
//        .serverLogic[Future](x => Right(x))
        .serverLogicSuccess[IO] { case (length, stream) =>
          IO((length, stream))
        }

      val options = NettyCatsServerOptions.default[IO](dispatcher)

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
        private var charsToGo: Int = howManyChars // - 1

        def hasNext: Boolean = {
          Thread.sleep(1000)
          charsToGo > 0
        }

        def next(): Byte = {
          charsToGo -= 1
          'A'.toByte
        }
      }

      val inputStream = fs2.Stream.fromIterator[IO](iterator(howManyChars), chunkSize = 10)

//      val sb = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))

//      val e = endpoint.post
//        .in(streamTextBody())
//        .out(streamTextBody)
//        .serverLogicSuccess[Future] { body =>
//          Thread.sleep(2000); Future.successful(body)
//        }

//      endpoint.get
//        .in("receive")
//        .out(header[Long](HeaderNames.ContentLength))
//        .out(streamTextBody(ZioStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

//      val sb = streamTextBody(inputStream)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))

//      val e = sttp.tapir.tests.Streaming
//        .in_stream_out_stream_with_content_length(Fs2Streams[IO])
//        .serverLogic[Future](((in: (Long, sttp.capabilities.Streams.BinaryStream)) => pureResult(in.asRight[Unit])))
//        .serverLogicSuccess[Future] { case (length, stream) =>
//          Future.successful(Right((length, stream)))
//        }
//      val sb1 = streamTextBody[S](s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))
//      endpoint.post.in("api" / "echo")
//        .in(header[Long](HeaderNames.ContentLength))
//        .in(sb)
//        .out(header[Long](HeaderNames.ContentLength))
//        .out(sb)
//      val e =
//        endpoint.post
//          .in(stringBody)
//          .out(stringBody)
//          .serverLogicSuccess[Future] { body =>
//            Thread.sleep(2000); Future.successful(body)
//          }

      Resource
        .make(bind)(_.stop())
        .map(_.port)
        .use { port =>
          basicRequest
            .post(uri"http://localhost:$port")
            .contentLength(howManyChars)
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
            //            .timeout(500.millis)
            .map { response =>
              response.body shouldBe Right("AAAAAAAAAAAAAAAAAAAA")
              println("zrobione")
              response.contentLength shouldBe Some(howManyChars)
            }
        }
        .unsafeToFuture()
    }
  )
}
