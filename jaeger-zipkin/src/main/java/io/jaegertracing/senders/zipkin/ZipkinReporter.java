package io.jaegertracing.senders.zipkin;

import static io.jaegertracing.senders.zipkin.V2SpanConverter.convertSpan;

import io.jaegertracing.Span;
import io.jaegertracing.reporters.Reporter;

/**
 * @author Pavol Loffay
 */
public class ZipkinReporter implements Reporter {

  private zipkin2.reporter.Reporter<zipkin2.Span> delegateV2;

  public ZipkinReporter(zipkin2.reporter.Reporter<zipkin2.Span> reporterV2) {
    this.delegateV2 = reporterV2;
  }

  @Override
  public void report(Span span) {
      delegateV2.report(convertSpan(span));
  }

  @Override
  public void close() {
    // noop zipkin cannot be closed
  }
}
