package io.buoyant.test.h2

import com.twitter.finagle.buoyant.h2.{Frame, Stream}
import com.twitter.io.Buf
import com.twitter.util.Future
import io.netty.handler.codec.http2._

object StreamTestUtils {

  def readDataStream(stream: Stream): Future[Buf] = {
    stream.read().flatMap {
      case frame: Frame.Data if frame.isEnd =>
        // Copy the data so that the underlying buffer can be released.
        val bbCopy = Buf.ByteBuffer.Shared.extract(frame.buf)
        val _ = frame.release()
        Future.value(Buf.ByteBuffer.Owned(bbCopy))
      case frame: Frame.Data =>
        // Copy the data so that the underlying buffer can be released.
        val bbCopy = Buf.ByteBuffer.Shared.extract(frame.buf)
        val _ = frame.release()
        readDataStream(stream).map { rest =>
          Buf.ByteBuffer.Owned(bbCopy).concat(rest)
        }
      case frame: Frame.Trailers =>
        val _ = frame.release()
        Future.value(Buf.Empty)
    }
  }

  def readDataString(stream: Stream): Future[String] =
    readDataStream(stream).map(Buf.Utf8.unapply).map(_.get)

  /**
   * Enhances a [[Stream]] by providing the [[readToEnd()]] function in the
   * method position
   *
   * @param stream the underlying [[Stream]]
   */
  implicit class ReadAllStream(val stream: Stream) extends AnyVal {
    @inline def readToEnd: Future[Unit] = Stream.readToEnd(stream)
    @inline def readDataString: Future[String] = StreamTestUtils.readDataString(stream)
  }

  def mkNewHeaderStreamFrame(hs: Http2Headers, streamId: Int, state: Http2Stream.State, eos: Boolean) =
    new DefaultHttp2HeadersFrame(hs, eos).stream(H2FrameStream(streamId, state))

}
