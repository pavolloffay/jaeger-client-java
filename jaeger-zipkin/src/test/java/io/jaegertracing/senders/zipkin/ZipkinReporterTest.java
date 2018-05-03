package io.jaegertracing.senders.zipkin;

import static org.junit.Assert.*;

import io.jaegertracing.Tracer;
import io.jaegertracing.samplers.ConstSampler;
import org.junit.Rule;
import org.junit.Test;
import zipkin.junit.ZipkinRule;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * @author Pavol Loffay
 */
public class ZipkinReporterTest {

  @Rule
  public ZipkinRule zipkinRule = new ZipkinRule();

  @Test
  public void testV1() {
    AsyncReporter<Span> zipkinReporter = AsyncReporter
        .builder(URLConnectionSender.create(zipkinRule.httpUrl() + "/api/v1/spans"))
        .build(SpanBytesEncoder.JSON_V1);

    ZipkinReporter reporterAdapter = new ZipkinReporter(zipkinReporter);

    Tracer tracer = new Tracer.Builder("test")
        .withReporter(reporterAdapter)
        .withSampler(new ConstSampler(true))
        .build();

    tracer.buildSpan("foo").start().finish();
    zipkinReporter.flush();

    assertEquals("foo", zipkinRule.getTraces().get(0).get(0).name);
  }

  @Test
  public void testV2() {
    AsyncReporter<Span> zipkinReporter = AsyncReporter
        .builder(URLConnectionSender.create(zipkinRule.httpUrl() + "/api/v2/spans"))
        .build();

    ZipkinReporter reporterAdapter = new ZipkinReporter(zipkinReporter);

    Tracer tracer = new Tracer.Builder("test")
            .withReporter(reporterAdapter)
            .withSampler(new ConstSampler(true))
            .build();


    tracer.buildSpan("foo").start().finish();
    zipkinReporter.flush();

    assertEquals("foo", zipkinRule.getTraces().get(0).get(0).name);
  }
}
