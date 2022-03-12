/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.kotlin;

import app.cash.zipline.internal.bridge.Endpoint;
import app.cash.zipline.internal.bridge.InboundBridge;
import app.cash.zipline.internal.bridge.InboundCall;
import app.cash.zipline.internal.bridge.InboundCallHandler;
import app.cash.zipline.internal.bridge.OutboundBridge;
import app.cash.zipline.internal.bridge.OutboundCall;
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter;
import app.cash.zipline.testing.EchoRequest;
import app.cash.zipline.testing.EchoResponse;
import app.cash.zipline.testing.EchoService;
import app.cash.zipline.testing.EchoZiplineService;
import app.cash.zipline.testing.GenericEchoService;
import java.util.Arrays;
import java.util.List;
import kotlin.Pair;
import kotlin.PublishedApi;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClassifiers;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializersKt;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static kotlinx.serialization.SerializersKt.serializer;

/**
 * Call these {@link PublishedApi} internal APIs from Java rather than from Kotlin to hack around
 * visibility of internal APIs.
 */
@SuppressWarnings("KotlinInternalInJava")
public final class ZiplineTestInternals {
  private static final KType echoResponseKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(EchoResponse.class), emptyList(), false, emptyList());
  private static final KType echoRequestKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(EchoRequest.class), emptyList(), false, emptyList());
  private static final KType stringKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(String.class), emptyList(), false, emptyList());
  private static final KType listOfStringKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(List.class),
      singletonList(KTypeProjection.invariant(stringKt)), false, emptyList());

  public static Pair<Endpoint, Endpoint> newEndpointPair() {
    CoroutineScope scope = CoroutineScopeKt.CoroutineScope(EmptyCoroutineContext.INSTANCE);
    return app.cash.zipline.testing.EndpointsKt.newEndpointPair(scope);
  }

  /** Simulate generated code for outbound calls. */
  public static EchoService takeEchoClient(Endpoint endpoint, String name) {
    return endpoint.take(name, EchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for inbound calls. */
  public static void bindEchoService(Endpoint endpoint, String name, EchoService echoService) {
    endpoint.bind(name, echoService, EchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for outbound calls. */
  public static GenericEchoService<String> takeGenericEchoService(Endpoint endpoint, String name) {
    return endpoint.take(name, GenericEchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for inbound calls. */
  public static void bindGenericEchoService(
      Endpoint endpoint, String name, GenericEchoService<String> echoService) {
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class EchoServiceAdapter extends ZiplineServiceAdapter<EchoService> {
    public static final EchoServiceAdapter INSTANCE = new EchoServiceAdapter();

    @Override public String getSerialName() {
      return "EchoService";
    }

    @Override public InboundCallHandler inboundCallHandler(
        EchoService service, InboundBridge.Context context) {
      return new GeneratedInboundCallHandler(service, context);
    }

    @Override public EchoService outboundService(OutboundBridge.Context context) {
      return new GeneratedOutboundService(context);
    }

    private static class GeneratedInboundCallHandler implements InboundCallHandler {
      private final EchoService service;
      private final InboundBridge.Context context;
      private final KSerializer<EchoRequest> requestSerializer;
      private final KSerializer<EchoResponse> responseSerializer;

      private final List<String> supportedFunctionNames = Arrays.asList(
        "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
      );

      GeneratedInboundCallHandler(EchoService service, InboundBridge.Context context) {
        this.service = service;
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoRequestKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoResponseKt);
      }

      @Override
      public InboundBridge.Context getContext() {
        return context;
      }

      @Override
      public String[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse")) {
          return inboundCall.result(responseSerializer, service.echo(
            inboundCall.parameter(requestSerializer)));
        } else {
          return inboundCall.unexpectedFunction(supportedFunctionNames);
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super String[]> continuation) {
        return inboundCall.unexpectedFunction(supportedFunctionNames);
      }
    }

    private static class GeneratedOutboundService implements EchoService {
      private final OutboundBridge.Context context;
      private final KSerializer<EchoRequest> requestSerializer;
      private final KSerializer<EchoResponse> responseSerializer;

      GeneratedOutboundService(OutboundBridge.Context context) {
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoRequestKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoResponseKt);
      }

      @Override public EchoResponse echo(EchoRequest request) {
        OutboundCall outboundCall = context.newCall("fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse", 1);
        outboundCall.parameter(requestSerializer, request);
        return outboundCall.invoke(responseSerializer);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class GenericEchoServiceAdapter
      extends ZiplineServiceAdapter<GenericEchoService<String>> {
    public static final GenericEchoServiceAdapter INSTANCE = new GenericEchoServiceAdapter();

    @Override public String getSerialName() {
      return "GenericEchoService";
    }

    @Override public InboundCallHandler inboundCallHandler(
        GenericEchoService<String> service, InboundBridge.Context context) {
      return new GeneratedInboundCallHandler(service, context);
    }

    @Override public GenericEchoService<String> outboundService(OutboundBridge.Context context) {
      return new GeneratedOutboundService(context);
    }

    private static class GeneratedInboundCallHandler implements InboundCallHandler {
      private final GenericEchoService<String> service;
      private final InboundBridge.Context context;
      private final KSerializer<String> requestSerializer;
      private final KSerializer<List<String>> responseSerializer;

      private final List<String> supportedFunctionNames = Arrays.asList(
        "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
      );

      GeneratedInboundCallHandler(
          GenericEchoService<String> service, InboundBridge.Context context) {
        this.service = service;
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), stringKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), listOfStringKt);
      }

      @Override
      public InboundBridge.Context getContext() {
        return context;
      }

      @Override
      public String[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("fun genericEcho(T): kotlin.collections.List<T>")) {
          return inboundCall.result(responseSerializer, service.genericEcho(
            inboundCall.parameter(requestSerializer)));
        } else {
          return inboundCall.unexpectedFunction(supportedFunctionNames);
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super String[]> continuation) {
        return inboundCall.unexpectedFunction(supportedFunctionNames);
      }
    }

    private static class GeneratedOutboundService implements GenericEchoService<String> {
      private final OutboundBridge.Context context;
      private final KSerializer<String> requestSerializer;
      private final KSerializer<List<String>> responseSerializer;

      GeneratedOutboundService(OutboundBridge.Context context) {
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), stringKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), listOfStringKt);
      }

      @Override public List<String> genericEcho(String request) {
        OutboundCall outboundCall = context.newCall("fun genericEcho(T): kotlin.collections.List<T>", 1);
        outboundCall.parameter(requestSerializer, request);
        return outboundCall.invoke(responseSerializer);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class EchoZiplineServiceAdapter extends ZiplineServiceAdapter<EchoZiplineService> {
    public static final EchoZiplineServiceAdapter INSTANCE = new EchoZiplineServiceAdapter();

    @Override public String getSerialName() {
      return "EchoZiplineService";
    }

    @Override public InboundCallHandler inboundCallHandler(
        EchoZiplineService service,
        InboundBridge.Context context) {
      return new GeneratedInboundCallHandler(service, context);
    }

    @Override public EchoZiplineService outboundService(
      OutboundBridge.Context context) {
      return new GeneratedOutboundService(context);
    }

    private static class GeneratedInboundCallHandler
        implements InboundCallHandler {
      private final EchoZiplineService service;
      private final InboundBridge.Context context;
      private final KSerializer<EchoRequest> requestSerializer;
      private final KSerializer<EchoResponse> responseSerializer;

      private final List<String> supportedFunctionNames = Arrays.asList(
        "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
      );

      GeneratedInboundCallHandler(EchoZiplineService service,
          InboundBridge.Context context) {
        this.service = service;
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoRequestKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoResponseKt);
      }

      @Override
      public InboundBridge.Context getContext() {
        return context;
      }

      @Override
      public String[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse")) {
          return inboundCall.result(responseSerializer, service.echo(
            inboundCall.parameter(requestSerializer)));
        } else {
          return inboundCall.unexpectedFunction(supportedFunctionNames);
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super String[]> continuation) {
        return inboundCall.unexpectedFunction(supportedFunctionNames);
      }
    }

    private static class GeneratedOutboundService
        implements EchoZiplineService {
      private final OutboundBridge.Context context;
      private final KSerializer<EchoRequest> requestSerializer;
      private final KSerializer<EchoResponse> responseSerializer;

      GeneratedOutboundService(OutboundBridge.Context context) {
        this.context = context;
        this.requestSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoRequestKt);
        this.responseSerializer
          = (KSerializer) SerializersKt.serializer(context.getSerializersModule(), echoResponseKt);
      }

      @Override public EchoResponse echo(EchoRequest request) {
        OutboundCall outboundCall = context.newCall("fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse", 1);
        outboundCall.parameter(requestSerializer, request);
        return outboundCall.invoke(responseSerializer);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate generated code for inbound calls. */
  public static void bindEchoZiplineService(
      Endpoint endpoint, String name, EchoZiplineService service) {
    endpoint.bind(name, service, EchoZiplineServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for outbound calls. */
  public static EchoZiplineService takeEchoZiplineService(Endpoint endpoint, String name) {
    return endpoint.take(name, EchoZiplineServiceAdapter.INSTANCE);
  }

  private ZiplineTestInternals() {
  }
}
