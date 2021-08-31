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
package app.cash.quickjs.ktbridge.plugin;

import app.cash.quickjs.internal.bridge.InboundCall;
import app.cash.quickjs.internal.bridge.InboundService;
import app.cash.quickjs.internal.bridge.KtBridge;
import app.cash.quickjs.internal.bridge.OutboundCall;
import app.cash.quickjs.internal.bridge.OutboundClientFactory;
import app.cash.quickjs.testing.EchoJsAdapter;
import app.cash.quickjs.testing.EchoRequest;
import app.cash.quickjs.testing.EchoResponse;
import app.cash.quickjs.testing.EchoService;
import app.cash.quickjs.testing.GenericEchoService;
import app.cash.quickjs.testing.GenericJsAdapter;
import java.util.List;
import kotlin.Pair;
import kotlin.PublishedApi;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KClassifiers;
import kotlinx.coroutines.CoroutineDispatcher;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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

  /** Refuse to dispatch anything. */
  private static final CoroutineDispatcher NULL_DISPATCHER = new CoroutineDispatcher() {
    @Override public void dispatch(CoroutineContext coroutineContext, Runnable runnable) {
      throw new IllegalStateException();
    }
  };


  public static Pair<KtBridge, KtBridge> newKtBridgePair() {
    return app.cash.quickjs.testing.BridgesKt.newKtBridgePair(NULL_DISPATCHER);
  }

  /** Simulate generated code for outbound calls. */
  public static EchoService getEchoClient(KtBridge ktBridge, String name) {
    return ktBridge.get(name, new OutboundClientFactory<EchoService>(EchoJsAdapter.INSTANCE) {
      @Override public EchoService create(OutboundCall.Factory callFactory) {
        return new EchoService() {
          @Override public EchoResponse echo(EchoRequest request) {
            OutboundCall outboundCall = callFactory.create("echo", 1);
            outboundCall.parameter(echoRequestKt, request);
            return outboundCall.invoke(echoResponseKt);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setEchoService(KtBridge ktBridge, String name, EchoService echoService) {
    ktBridge.set(name, new InboundService<EchoService>(EchoJsAdapter.INSTANCE) {
      @Override public byte[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("echo")) {
          return inboundCall.result(echoResponseKt,
              echoService.echo(inboundCall.parameter(echoRequestKt)));
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
    return ktBridge.get(name, new OutboundClientFactory<GenericEchoService<String>>(
        GenericJsAdapter.INSTANCE) {
      @Override public GenericEchoService<String> create(OutboundCall.Factory callFactory) {
        return new GenericEchoService<String>() {
          @Override public List<String> genericEcho(String request) {
            OutboundCall outboundCall = callFactory.create("genericEcho", 1);
            outboundCall.parameter(stringKt, request);
            return outboundCall.invoke(listOfStringKt);
          }
        };
      }
    });
  }

  /** Simulate generated code for inbound calls. */
  public static void setGenericEchoService(
      KtBridge ktBridge, String name, GenericEchoService<String> echoService) {
    ktBridge.set(name, new InboundService<GenericEchoService<String>>(GenericJsAdapter.INSTANCE) {
      @Override public byte[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("genericEcho")) {
          return inboundCall.result(listOfStringKt,
              echoService.genericEcho(inboundCall.parameter(stringKt)));
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
