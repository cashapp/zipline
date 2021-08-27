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

import app.cash.quickjs.InboundCall;
import app.cash.quickjs.InboundService;
import app.cash.quickjs.OutboundCall;
import app.cash.quickjs.OutboundClientFactory;
import app.cash.quickjs.QuickJs;
import app.cash.quickjs.testing.EchoJsAdapter;
import app.cash.quickjs.testing.EchoRequest;
import app.cash.quickjs.testing.EchoResponse;
import app.cash.quickjs.testing.EchoService;
import kotlin.PublishedApi;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClassifiers;

import static java.util.Collections.emptyList;

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

  /** Simulate generated code for outbound calls. */
  public static EchoService getEchoClient(QuickJs quickJs, String name) {
    return quickJs.get(name, new OutboundClientFactory<EchoService>(EchoJsAdapter.INSTANCE) {
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
  public static void setEchoService(QuickJs quickJs, String name, EchoService echoService) {
    quickJs.set(name, new InboundService<EchoService>(EchoJsAdapter.INSTANCE) {
      @Override public byte[] call(InboundCall inboundCall) {
        if (inboundCall.getFunName().equals("echo")) {
          return inboundCall.result(echoResponseKt,
              echoService.echo(inboundCall.parameter(echoRequestKt)));
        } else {
          return inboundCall.unexpectedFunction();
        }
      }
    });
  }

  private KtBridgeTestInternals() {
  }
}
