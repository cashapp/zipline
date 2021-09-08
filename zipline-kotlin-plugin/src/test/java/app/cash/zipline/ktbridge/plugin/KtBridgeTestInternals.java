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
package app.cash.zipline.ktbridge.plugin;

import app.cash.zipline.internal.bridge.InboundCall;
import app.cash.zipline.internal.bridge.InboundService;
import app.cash.zipline.internal.bridge.KtBridge;
import app.cash.zipline.internal.bridge.OutboundCall;
import app.cash.zipline.internal.bridge.OutboundClientFactory;
import app.cash.zipline.testing.EchoRequest;
import app.cash.zipline.testing.EchoResponse;
import app.cash.zipline.testing.EchoService;
import app.cash.zipline.testing.GenericEchoService;
import java.util.List;
import kotlin.Pair;
import kotlin.PublishedApi;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClassifiers;
import kotlinx.coroutines.test.TestCoroutineDispatcher;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.modules.SerializersModule;

import static app.cash.zipline.testing.EchoKt.getEchoSerializersModule;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static kotlinx.serialization.SerializersKt.serializer;

/**
 * Call these {@link PublishedApi} internal APIs from Java rather than from Kotlin to hack around
 * visibility of internal APIs.
 */
@SuppressWarnings("KotlinInternalInJava")
public final class KtBridgeTestInternals {
  private static final KType echoResponseKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(EchoResponse.class), emptyList(), false, emptyList());
  private static final KType echoRequestKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(EchoRequest.class), emptyList(), false, emptyList());
  private static final KType stringKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(String.class), emptyList(), false, emptyList());
  private static final KType listOfStringKt = KClassifiers.createType(
      JvmClassMappingKt.getKotlinClass(List.class),
      singletonList(KTypeProjection.invariant(stringKt)), false, emptyList());

  public static Pair<KtBridge, KtBridge> newKtBridgePair() {
    return app.cash.zipline.testing.BridgesKt.newKtBridgePair(new TestCoroutineDispatcher());
  }

  /** Simulate generated code for outbound calls. */
  public static EchoService getEchoClient(KtBridge ktBridge, String name) {
    final SerializersModule serializersModule = getEchoSerializersModule();
    return ktBridge.get(name, new OutboundClientFactory<EchoService>(serializersModule) {
      @Override public EchoService create(OutboundCall.Factory callFactory) {
        return new EchoService() {
          @Override public EchoResponse echo(EchoRequest request) {
            OutboundCall outboundCall = callFactory.create("echo", 1);
            KSerializer<EchoRequest> parameterSerializer
                = (KSerializer) serializer(serializersModule, echoRequestKt);
            KSerializer<EchoResponse> resultSerializer
                = (KSerializer) serializer(serializersModule, echoResponseKt);
            outboundCall.parameter(parameterSerializer, request);
            return outboundCall.invoke(resultSerializer);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setEchoService(KtBridge ktBridge, String name, EchoService echoService) {
    SerializersModule serializersModule = getEchoSerializersModule();
    ktBridge.set(name, new InboundService<EchoService>(serializersModule) {
      @Override public byte[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("echo")) {
          KSerializer<EchoResponse> resultSerializer
              = (KSerializer) serializer(serializersModule, echoResponseKt);
          KSerializer<EchoRequest> parameterSerializer
              = (KSerializer) serializer(serializersModule, echoRequestKt);
          return inboundCall.result(resultSerializer, echoService.echo(
              inboundCall.parameter(parameterSerializer)));
        } else {
          return inboundCall.unexpectedFunction();
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super byte[]> continuation) {
        return inboundCall.unexpectedFunction();
      }
    });
  }

  /** Simulate generated code for outbound calls. */
  public static GenericEchoService<String> getGenericEchoService(KtBridge ktBridge, String name) {
    final SerializersModule serializersModule = getEchoSerializersModule();
    return ktBridge.get(name, new OutboundClientFactory<GenericEchoService<String>>(
        serializersModule) {
      @Override public GenericEchoService<String> create(OutboundCall.Factory callFactory) {
        return new GenericEchoService<String>() {
          @Override public List<String> genericEcho(String request) {
            OutboundCall outboundCall = callFactory.create("genericEcho", 1);
            KSerializer<String> parameterSerializer
                = (KSerializer) serializer(serializersModule, stringKt);
            KSerializer<List<String>> resultSerializer
                = (KSerializer) serializer(serializersModule, listOfStringKt);
            outboundCall.parameter(parameterSerializer, request);
            return outboundCall.invoke(resultSerializer);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setGenericEchoService(
      KtBridge ktBridge, String name, GenericEchoService<String> echoService) {
    final SerializersModule serializersModule = getEchoSerializersModule();
    ktBridge.set(name, new InboundService<GenericEchoService<String>>(serializersModule) {
      @Override public byte[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("genericEcho")) {
          KSerializer<List<String>> resultSerializer
              = (KSerializer) serializer(serializersModule, listOfStringKt);
          KSerializer<String> parameterSerializer
              = (KSerializer) serializer(serializersModule, stringKt);
          return inboundCall.result(resultSerializer, echoService.genericEcho(
              inboundCall.parameter(parameterSerializer)));
        } else {
          return inboundCall.unexpectedFunction();
        }
      }

      @Override public Object callSuspending(
          InboundCall inboundCall, Continuation<? super byte[]> continuation) {
        return inboundCall.unexpectedFunction();
      }
    });
  }

  private KtBridgeTestInternals() {
  }
}
