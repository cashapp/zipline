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
  public static EchoService getEchoClient(Endpoint endpoint, String name) {
    return endpoint.get(name, new OutboundBridge<EchoService>() {
      @Override public EchoService create(OutboundBridge.Context context) {
        KSerializer<EchoRequest> parameterSerializer
            = (KSerializer) serializer(context.getSerializersModule(), echoRequestKt);
        KSerializer<EchoResponse> resultSerializer
            = (KSerializer) serializer(context.getSerializersModule(), echoResponseKt);
        return new EchoService() {
          @Override public EchoResponse echo(EchoRequest request) {
            OutboundCall outboundCall = context.newCall("echo", 1);
            outboundCall.parameter(parameterSerializer, request);
            return outboundCall.invoke(resultSerializer);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setEchoService(Endpoint endpoint, String name, EchoService echoService) {
    endpoint.set(name, new InboundBridge<EchoService>() {
      @Override public EchoService getService() {
        return echoService;
      }

      @Override public InboundCallHandler create(Context context) {
        KSerializer<EchoResponse> resultSerializer
            = (KSerializer) serializer(context.getSerializersModule(), echoResponseKt);
        KSerializer<EchoRequest> parameterSerializer
            = (KSerializer) serializer(context.getSerializersModule(), echoRequestKt);

        return new InboundCallHandler() {
          @Override public Context getContext() {
            return context;
          }

          @Override public String[] call(InboundCall inboundCall) {
            if (inboundCall.getFunName().equals("echo")) {
              return inboundCall.result(resultSerializer, getService().echo(
                  inboundCall.parameter(parameterSerializer)));
            } else {
              return inboundCall.unexpectedFunction();
            }
          }

          @Override public Object callSuspending(
              InboundCall inboundCall, Continuation<? super String[]> continuation) {
            return inboundCall.unexpectedFunction();
          }
        };
      }
    });
  }

  /** Simulate generated code for outbound calls. */
  public static GenericEchoService<String> getGenericEchoService(Endpoint endpoint, String name) {
    return endpoint.get(name, new OutboundBridge<GenericEchoService<String>>() {
      @Override public GenericEchoService<String> create(OutboundBridge.Context context) {
        KSerializer<String> parameterSerializer
            = (KSerializer) serializer(context.getSerializersModule(), stringKt);
        KSerializer<List<String>> resultSerializer
            = (KSerializer) serializer(context.getSerializersModule(), listOfStringKt);
        return new GenericEchoService<String>() {
          @Override public List<String> genericEcho(String request) {
            OutboundCall outboundCall = context.newCall("genericEcho", 1);
            outboundCall.parameter(parameterSerializer, request);
            return outboundCall.invoke(resultSerializer);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setGenericEchoService(
      Endpoint endpoint, String name, GenericEchoService<String> echoService) {
    endpoint.set(name, new InboundBridge<GenericEchoService<String>>() {
      @Override public GenericEchoService<String> getService() {
        return echoService;
      }

      @Override public InboundCallHandler create(Context context) {
        KSerializer<List<String>> resultSerializer
            = (KSerializer) serializer(context.getSerializersModule(), listOfStringKt);
        KSerializer<String> parameterSerializer
            = (KSerializer) serializer(context.getSerializersModule(), stringKt);

        return new InboundCallHandler() {
          @Override public Context getContext() {
            return context;
          }

          @Override public String[] call(InboundCall inboundCall) {
            if (inboundCall.getFunName().equals("genericEcho")) {
              return inboundCall.result(resultSerializer, getService().genericEcho(
                  inboundCall.parameter(parameterSerializer)));
            } else {
              return inboundCall.unexpectedFunction();
            }
          }

          @Override public Object callSuspending(
              InboundCall inboundCall, Continuation<? super String[]> continuation) {
            return inboundCall.unexpectedFunction();
          }
        };
      }
    });
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
        if (inboundCall.getFunName().equals("echo")) {
          return inboundCall.result(responseSerializer, service.echo(
            inboundCall.parameter(requestSerializer)));
        } else {
          return inboundCall.unexpectedFunction();
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super String[]> continuation) {
        return inboundCall.unexpectedFunction();
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
        OutboundCall outboundCall = context.newCall("echo", 1);
        outboundCall.parameter(requestSerializer, request);
        return outboundCall.invoke(responseSerializer);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate generated code for inbound calls. */
  public static void setEchoZiplineService(
      Endpoint endpoint, String name, EchoZiplineService service) {
    endpoint.setService(name, service, EchoZiplineServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for inbound calls. */
  public static EchoZiplineService getEchoZiplineService(Endpoint endpoint, String name) {
    return endpoint.getService(name, EchoZiplineServiceAdapter.INSTANCE);
  }

  private ZiplineTestInternals() {
  }
}
