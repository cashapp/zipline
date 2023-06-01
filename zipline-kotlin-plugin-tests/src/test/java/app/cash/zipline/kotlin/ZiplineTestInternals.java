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

import app.cash.zipline.ZiplineFunction;
import app.cash.zipline.ZiplineScope;
import app.cash.zipline.internal.bridge.Endpoint;
import app.cash.zipline.internal.bridge.OutboundCallHandler;
import app.cash.zipline.internal.bridge.OutboundService;
import app.cash.zipline.internal.bridge.ReturningZiplineFunction;
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter;
import app.cash.zipline.testing.EchoRequest;
import app.cash.zipline.testing.EchoResponse;
import app.cash.zipline.testing.EchoService;
import app.cash.zipline.testing.EchoZiplineService;
import app.cash.zipline.testing.GenericEchoService;
import app.cash.zipline.testing.KotlinSerializersKt;
import app.cash.zipline.testing.SignatureHashKt;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import kotlin.Pair;
import kotlin.PublishedApi;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClassifiers;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializersKt;
import kotlinx.serialization.modules.SerializersModule;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import androidx.annotation.NonNull;

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
    return app.cash.zipline.testing.EndpointsKt.newEndpointPair(
      scope, KotlinSerializersKt.getKotlinBuiltInSerializersModule());
  }

  /** Simulate generated code for outbound calls. */
  public static EchoService takeEchoClient(Endpoint endpoint, String name) {
    return endpoint.take(name, new ZiplineScope(), EchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for inbound calls. */
  public static void bindEchoService(Endpoint endpoint, String name, EchoService echoService) {
    endpoint.bind(name, echoService, EchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for outbound calls. */
  public static GenericEchoService<String> takeGenericEchoService(Endpoint endpoint, String name) {
    return endpoint.take(name, new ZiplineScope(), GenericEchoServiceAdapter.INSTANCE);
  }

  /** Simulate generated code for inbound calls. */
  public static void bindGenericEchoService(
      Endpoint endpoint, String name, GenericEchoService<String> echoService) {
    endpoint.bind(name, echoService, GenericEchoServiceAdapter.INSTANCE);
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class EchoServiceAdapter extends ZiplineServiceAdapter<EchoService> {
    public static final EchoServiceAdapter INSTANCE = new EchoServiceAdapter();

    @Override public String getSimpleName() {
      return "EchoService";
    }

    @Override public String getSerialName() {
      return "app.cash.zipline.kotlin.EchoService";
    }

    @Override public List<KSerializer<?>> getSerializers() {
      return Collections.emptyList();
    }

    @Override public List<ZiplineFunction<EchoService>> ziplineFunctions(
        SerializersModule serializersModule) {
      KSerializer<?> requestSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, echoRequestKt);
      KSerializer<?> responseSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, echoResponseKt);
      String name = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse";
      return Arrays.asList(
        new ReturningZiplineFunction<EchoService>(SignatureHashKt.signatureHash(name), name,
            Arrays.asList(requestSerializer), responseSerializer) {
          @Override
          public Object call(EchoService service, List<?> args) {
            return service.echo((EchoRequest) args.get(0));
          }
        }
      );
    }

    @Override public EchoService outboundService(OutboundCallHandler callHandler) {
      return new GeneratedOutboundService(callHandler);
    }

    private static class GeneratedOutboundService
        implements EchoService, OutboundService {
      private final OutboundCallHandler callHandler;

      GeneratedOutboundService(OutboundCallHandler callHandler) {
        this.callHandler = callHandler;
      }

      @Override public OutboundCallHandler getCallHandler() {
        return callHandler;
      }

      @Override public EchoResponse echo(EchoRequest request) {
        return (EchoResponse) callHandler.call(this, 0, request);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class GenericEchoServiceAdapter
      extends ZiplineServiceAdapter<GenericEchoService<String>> {
    public static final GenericEchoServiceAdapter INSTANCE = new GenericEchoServiceAdapter();

    @Override public String getSimpleName() {
      return "GenericEchoService";
    }

    @Override public String getSerialName() {
      return "app.cash.zipline.kotlin.GenericEchoService<kotlin.String>";
    }

    @Override public List<KSerializer<?>> getSerializers() {
      return Collections.emptyList();
    }

    @Override
    public List<ZiplineFunction<GenericEchoService<String>>> ziplineFunctions(
        SerializersModule serializersModule) {
      KSerializer<?> requestSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, stringKt);
      KSerializer<?> responseSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, listOfStringKt);
      String name = "fun genericEcho(T): kotlin.collections.List<T>";
      return Arrays.asList(
        new ReturningZiplineFunction<GenericEchoService<String>>(
            SignatureHashKt.signatureHash(name), name, Arrays.asList(requestSerializer),
            responseSerializer) {
          @Override public Object call(GenericEchoService<String> service, List<?> args) {
            return service.genericEcho((String) args.get(0));
          }
        });
    }

    @Override public GenericEchoService<String> outboundService(OutboundCallHandler callHandler) {
      return new GeneratedOutboundService(callHandler);
    }

    private static class GeneratedOutboundService
        implements GenericEchoService<String>, OutboundService {
      private final OutboundCallHandler callHandler;

      GeneratedOutboundService(OutboundCallHandler callHandler) {
        this.callHandler = callHandler;
      }

      @Override public OutboundCallHandler getCallHandler() {
        return callHandler;
      }

      @Override public List<String> genericEcho(String request) {
        return (List<String>) callHandler.call(this, 0, request);
      }

      @Override public void close() {
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter. */
  public static class EchoZiplineServiceAdapter extends ZiplineServiceAdapter<EchoZiplineService> {
    public static final EchoZiplineServiceAdapter INSTANCE = new EchoZiplineServiceAdapter();

    @Override public String getSimpleName() {
      return "EchoService";
    }

    @Override public String getSerialName() {
      return "app.cash.zipline.kotlin.EchoZiplineService";
    }

    @Override public List<KSerializer<?>> getSerializers() {
      return Collections.emptyList();
    }

    @Override
    public List<ZiplineFunction<EchoZiplineService>> ziplineFunctions(
        SerializersModule serializersModule) {
      KSerializer<?> requestSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, echoRequestKt);
      KSerializer<?> responseSerializer
        = (KSerializer) SerializersKt.serializer(serializersModule, echoResponseKt);
      String name = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse";
      return Arrays.asList(
        new ReturningZiplineFunction<EchoZiplineService>(SignatureHashKt.signatureHash(name), name,
            Arrays.asList(requestSerializer), responseSerializer) {
          @Override public Object call(EchoZiplineService service, List<?> args) {
            return service.echo((EchoRequest) args.get(0));
          }
        });
    }

    @Override public EchoZiplineService outboundService(OutboundCallHandler callHandler) {
      return new GeneratedOutboundService(callHandler);
    }

    private static class GeneratedOutboundService
        implements EchoZiplineService, OutboundService {
      private final OutboundCallHandler callHandler;

      GeneratedOutboundService(OutboundCallHandler callHandler) {
        this.callHandler = callHandler;
      }

      @Override public OutboundCallHandler getCallHandler() {
        return callHandler;
      }

      @Override public EchoResponse echo(EchoRequest request) {
        return (EchoResponse) callHandler.call(this, 0, request);
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
    return endpoint.take(name, new ZiplineScope(), EchoZiplineServiceAdapter.INSTANCE);
  }

  private ZiplineTestInternals() {
  }
}
