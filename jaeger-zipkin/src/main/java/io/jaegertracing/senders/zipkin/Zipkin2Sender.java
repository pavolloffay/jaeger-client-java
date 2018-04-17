/*
 * Copyright (c) 2018, The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.senders.zipkin;

import io.jaegertracing.exceptions.SenderException;
import io.jaegertracing.senders.Sender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.ToString;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * This sends V1 or V2 (default) JSON encoded spans to a Zipkin Collector (usually a
 * zipkin-server).
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * reporter = new RemoteReporter(Zipkin2Sender.create("http://localhost:9411/api/v2/spans"));
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    ...
 * }</pre>
 *
 * <p>
 * See https://github.com/openzipkin/zipkin/tree/master/zipkin-server
 */
@ToString(exclude = "spanBuffer")
public final class Zipkin2Sender implements Sender {

  static final BytesEncoder<Span> DEFAULT_ENCODER = SpanBytesEncoder.JSON_V2;

  /**
   * @param endpoint The POST URL for zipkin's <a href="http://zipkin.io/zipkin-api/#/">v2 api</a>,
   * usually "http://zipkinhost:9411/api/v2/spans"
   * @return new sender
   */
  public static Zipkin2Sender create(String endpoint) {
    return new Zipkin2Sender(URLConnectionSender.create(endpoint), DEFAULT_ENCODER);
  }

  public static Zipkin2Sender create(String endpoint, BytesEncoder<Span> encoder) {
    return new Zipkin2Sender(URLConnectionSender.create(endpoint), encoder);
  }

  /**
   * Use this to dispatch to an existing Zipkin sender which is configured for
   * {@link SpanBytesEncoder#JSON_V2 JSON_V2 encoding}.
   *
   * <p>
   * Ex. for Kafka ("io.zipkin2.reporter:zipkin-sender-kafka08")
   *
   * <pre>{@code
   * sender = Zipkin2Sender.create(KafkaSender.create("192.168.99.100:9092"));
   * }</pre>
   *
   * @param delegate indicates an alternate sender library than {@link URLConnectionSender}
   * @return new sender
   */
  public static Zipkin2Sender create(zipkin2.reporter.Sender delegate) {
    return new Zipkin2Sender(delegate, DEFAULT_ENCODER);
  }

  public static Zipkin2Sender create(zipkin2.reporter.Sender delegate, BytesEncoder<Span> encoder) {
    return new Zipkin2Sender(delegate, encoder);
  }

  final BytesEncoder<Span> encoder;
  final zipkin2.reporter.Sender delegate;
  final List<byte[]> spanBuffer;

  Zipkin2Sender(zipkin2.reporter.Sender delegate, BytesEncoder<Span> encoder) {
    this.delegate = delegate;
    this.encoder = encoder;
    this.spanBuffer = new ArrayList<byte[]>();
  }

  /*
   * Adds spans to an internal queue that flushes them with the configured delegate later.
   * This function does not need to be synchronized because the reporter creates
   * a single thread that calls this append function
   */
  @Override
  public int append(io.jaegertracing.Span span) throws SenderException {
    byte[] next = encoder.encode(V2SpanConverter.convertSpan(span));
    int messageSizeOfNextSpan = delegate.messageSizeInBytes(Collections.singletonList(next));
    // don't enqueue something larger than we can drain
    if (messageSizeOfNextSpan > delegate.messageMaxBytes()) {
      throw new SenderException(
          delegate.toString() + " received a span that was too large", null, 1);
    }

    spanBuffer.add(next); // speculatively add to the buffer so we can size it
    int nextSizeInBytes = delegate.messageSizeInBytes(spanBuffer);
    // If we can fit queued spans and the next into one message...
    if (nextSizeInBytes <= delegate.messageMaxBytes()) {

      // If there's still room, don't flush yet.
      if (nextSizeInBytes < delegate.messageMaxBytes()) {
        return 0;
      }
      // If we have exactly met the max message size, flush
      return flush();
    }

    // Otherwise, remove speculatively added span and flush until we have room for it.
    spanBuffer.remove(spanBuffer.size() - 1);
    int n;
    try {
      n = flush();
    } catch (SenderException e) {
      // +1 for the span not submitted in the buffer above
      throw new SenderException(e.getMessage(), e.getCause(), e.getDroppedSpanCount() + 1);
    }

    // Now that there's room, add the span as the only element in the buffer
    spanBuffer.add(next);
    return n;
  }

  @Override
  public int flush() throws SenderException {
    if (spanBuffer.isEmpty()) {
      return 0;
    }

    int n = spanBuffer.size();
    try {
      Call<Void> call = delegate.sendSpans(spanBuffer);
      call.execute();
    } catch (RuntimeException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } catch (IOException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } finally {
      spanBuffer.clear();
    }
    return n;
  }

  @Override
  public int close() throws SenderException {
    int n = flush();
    try {
      delegate.close();
    } catch (IOException e) {
      throw new SenderException("Failed to close " + delegate, e, n);
    }
    return n;
  }
}
