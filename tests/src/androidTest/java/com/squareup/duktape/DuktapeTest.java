/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.duktape;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.TimeZone;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class DuktapeTest {
  private Duktape duktape;

  @Before public void setUp() {
    duktape = Duktape.create();
  }

  @After public void tearDown() {
    duktape.close();
  }

  @Test public void helloWorld() {
    String hello = (String) duktape.evaluate("'hello, world!'.toUpperCase();");
    assertThat(hello).isEqualTo("HELLO, WORLD!");
  }

  @Test public void evaluateReturnsNumber() {
    int result = ((Double) duktape.evaluate("2 + 3;")).intValue();
    assertThat(result).isEqualTo(5);
  }

  @Test public void exceptionsInScriptThrowInJava() {
    try {
      duktape.evaluate("nope();");
      fail();
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
    }
  }

  @Test public void exceptionsInScriptIncludeStackTrace() {
    try {
      duktape.evaluate("\n"
            + "f1();\n"           // Line 2.
            + "\n"
            + "function f1() {\n"
            + "  f2();\n"         // Line 5.
            + "}\n"
            + "\n"
            + "\n"
            + "function f2() {\n"
            + "  nope();\n"       // Line 10.
            + "}\n", "test.js");
      fail();
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
      assertThat(e.getStackTrace()).asList().containsAllOf(
              new StackTraceElement("JavaScript", "eval", "test.js", 2),
              new StackTraceElement("JavaScript", "f1", "test.js", 5),
              new StackTraceElement("JavaScript", "f2", "test.js", 10));
    }
  }

  @Test public void dateTimezoneOffset() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+2:00"));
      String date = duktape.evaluate("new Date(0).toString();").toString();
      assertThat(date).isEqualTo("1970-01-01 02:00:00.000+02:00");
      int offset = ((Double) duktape.evaluate("new Date(0).getTimezoneOffset();")).intValue();
      assertThat(offset).isEqualTo(-120);
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test public void parseDates() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"));
      assertThat(duktape.evaluate("new Date('2015-03-25').toString();"))
          .isEqualTo("2015-03-25 02:00:00.000+02:00");
      assertThat(duktape.evaluate("new Date('03/25/2015').toString();"))
          .isEqualTo("2015-03-25 00:00:00.000+02:00");
      assertThat(duktape.evaluate("new Date('2015/03/25').toString();"))
          .isEqualTo("2015-03-25 00:00:00.000+02:00");
      assertThat(duktape.evaluate("new Date('Mar 25 2015').toString();"))
          .isEqualTo("2015-03-25 00:00:00.000+02:00");
      assertThat(duktape.evaluate("new Date('25 Mar 2015').toString();"))
          .isEqualTo("2015-03-25 00:00:00.000+02:00");
      assertThat(duktape.evaluate("new Date('Wednesday March 25 2015').toString();"))
          .isEqualTo("2015-03-25 00:00:00.000+02:00");
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test public void parseDateAndTime() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT-02:00"));
      assertThat(duktape.evaluate("new Date('2015-03-25T23:45:12').toString();"))
          .isEqualTo("2015-03-25 21:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('2015-03-25T23:45:12-02:00').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('03/25/2015 23:45:12').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('2015/03/25 23:45:12').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('Mar 25 2015 23:45:12').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('25 Mar 2015 23:45:12').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
      assertThat(duktape.evaluate("new Date('Wednesday March 25 2015 23:45:12').toString();"))
          .isEqualTo("2015-03-25 23:45:12.000-02:00");
    } finally {
      TimeZone.setDefault(original);
    }
  }

  // https://github.com/square/duktape-android/issues/86
  @Test public void issue86() {
    String code = ""
        + "var id = \"d02930b13da82dafa86cc83bb596f6a402a1c4bee10dd1f1ddb607dbb4a38e04c78a87d20ad1"
            + "c3a8ce03daacffae04cdba839a0bd0d1f18606adeca2a501cc82cff20b\"\n"
        + "var decoded = \"\";\n"
        + "var document = {};\n"
        + "var window = this;\n"
        + "var $ = function() {\n"
        + "    return {\n"
        + "        text: function(a) {\n"
        + "            if (a)\n"
        + "                decoded = a;\n"
        + "            else\n"
        + "                return id;\n"
        + "        },\n"
        + "        ready: function(a) {\n"
        + "            a()\n"
        + "        }\n"
        + "    }\n"
        + "};\n"
        + "\n"
        + "(function(d) {\n"
        + "    var f = function() {};\n"
        + "    var s = '';\n"
        + "    var o = null;\n"
        + "    ['close', 'createAttribute', 'createDocumentFragment', 'createElement', "
            + "'createElementNS', 'createEvent', 'createNSResolver', 'createRange', "
            + "'createTextNode', 'createTreeWalker', 'evaluate', 'execCommand', 'getElementById', "
            + "'getElementsByName', 'getElementsByTagName', 'importNode', 'open', "
            + "'queryCommandEnabled', 'queryCommandIndeterm', 'queryCommandState', "
            + "'queryCommandValue', 'write', 'writeln'].forEach(function(e) {\n"
        + "        d[e] = f;\n"
        + "    });\n"
        + "    ['anchors', 'applets', 'body', 'defaultView', 'doctype', 'documentElement', "
            + "'embeds', 'firstChild', 'forms', 'images', 'implementation', 'links', 'location', "
            + "'plugins', 'styleSheets'].forEach(function(e) {\n"
        + "        d[e] = o;\n"
        + "    });\n"
        + "    ['URL', 'characterSet', 'compatMode', 'contentType', 'cookie', 'designMode', "
            + "'domain', 'lastModified', 'referrer', 'title'].forEach(function(e) {\n"
        + "        d[e] = s;\n"
        + "    });\n"
        + "})(document);\n"
        + "\n"
        + "//Obfuscated code extracted from Openload\n"
        + "ﾟωﾟﾉ= /｀ｍ´）ﾉ ~┻━┻   //*´∇｀*/ ['_']; o=(ﾟｰﾟ)  =_=3; c=(ﾟΘﾟ) =(ﾟｰﾟ)-(ﾟｰﾟ); (ﾟДﾟ) =(ﾟΘﾟ)"
            + "= (o^_^o)/ (o^_^o);(ﾟДﾟ)={ﾟΘﾟ: '_' ,ﾟωﾟﾉ : ((ﾟωﾟﾉ==3) +'_') [ﾟΘﾟ] ,ﾟｰﾟﾉ :(ﾟωﾟﾉ+ '_')"
            + "[o^_^o -(ﾟΘﾟ)] ,ﾟДﾟﾉ:((ﾟｰﾟ==3) +'_')[ﾟｰﾟ] }; (ﾟДﾟ) [ﾟΘﾟ] =((ﾟωﾟﾉ==3) +'_') [c^_^o];("
            + "ﾟДﾟ) ['c'] = ((ﾟДﾟ)+'_') [ (ﾟｰﾟ)+(ﾟｰﾟ)-(ﾟΘﾟ) ];(ﾟДﾟ) ['o'] = ((ﾟДﾟ)+'_') [ﾟΘﾟ];(ﾟoﾟ)"
            + "=(ﾟДﾟ) ['c']+(ﾟДﾟ) ['o']+(ﾟωﾟﾉ +'_')[ﾟΘﾟ]+ ((ﾟωﾟﾉ==3) +'_') [ﾟｰﾟ] + ((ﾟДﾟ) +'_') [(ﾟ"
            + "ｰﾟ)+(ﾟｰﾟ)]+ ((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+((ﾟｰﾟ==3) +'_') [(ﾟｰﾟ) - (ﾟΘﾟ)]+(ﾟДﾟ) ['c']+((ﾟДﾟ)"
            + "+'_') [(ﾟｰﾟ)+(ﾟｰﾟ)]+ (ﾟДﾟ) ['o']+((ﾟｰﾟ==3) +'_') [ﾟΘﾟ];(ﾟДﾟ) ['_'] =(o^_^o) [ﾟoﾟ] ["
            + "ﾟoﾟ];(ﾟεﾟ)=((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+ (ﾟДﾟ) .ﾟДﾟﾉ+((ﾟДﾟ)+'_') [(ﾟｰﾟ) + (ﾟｰﾟ)]+((ﾟｰﾟ==3) "
            + "+'_') [o^_^o -ﾟΘﾟ]+((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+ (ﾟωﾟﾉ +'_') [ﾟΘﾟ]; (ﾟｰﾟ)+=(ﾟΘﾟ); (ﾟДﾟ)[ﾟεﾟ"
            + "]='\\\\'; (ﾟДﾟ).ﾟΘﾟﾉ=(ﾟДﾟ+ ﾟｰﾟ)[o^_^o -(ﾟΘﾟ)];(oﾟｰﾟo)=(ﾟωﾟﾉ +'_')[c^_^o];(ﾟДﾟ) [ﾟoﾟ]"
            + "='\\\"';(ﾟДﾟ) ['_'] ( (ﾟДﾟ) ['_'] (ﾟεﾟ+(ﾟДﾟ)[ﾟoﾟ]+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((o^_^o) +(o^_^"
            + "o) +(c^_^o))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (-~0)+ (ﾟДﾟ)[ﾟεﾟ"
            + "]+(-~0)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o) +(c^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~3)+ (-~3"
            + ")+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((o^_^o) "
            + "+(o^_^o) +(c^_^o))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o)"
            + " +(c^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((o^_^o) +(o^_^o) +(c^_^o))+ ((c^_^o)-(c^_^o))+ (ﾟДﾟ)["
            + "ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(-~3)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)["
            + "ﾟεﾟ]+(-~0)+ ((c^_^o)-(c^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((c^_^o)-(c^_^o))+"
            + " ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ ((o^_^o) +(o^_^o) +(c^_^o))+ (-~3)+ (ﾟДﾟ)[ﾟεﾟ]"
            + "+(-~0)+ (-~0)+ ((o^_^o) +(o^_^o) +(c^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~3)+ (-~3)+ (ﾟДﾟ)[ﾟε"
            + "ﾟ]+(-~0)+ (-~0)+ (-~3)+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~3)+ (-~0)+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~0)+ ((ﾟ"
            + "ｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~3)+ (-~0)+ (ﾟДﾟ)[ﾟεﾟ]+(-~0)+ (-~0)+ ((ﾟｰﾟ) + (o"
            + "^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(-~3)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ (-~-~1)+ "
            + "(ﾟДﾟ)[ﾟoﾟ]) (ﾟΘﾟ)) ('_');var _0x921f=['\\x74\\x65\\x78\\x74','\\x23\\x73\\x74\\x72"
            + "\\x65\\x61\\x6d\\x75\\x72\\x6c','\\x42\\x41\\x6b','\\x6c\\x73\\x74','\\x6c\\x65\\x6e"
            + "\\x67\\x74\\x68','\\x35\\x7c\\x38\\x7c\\x31\\x7c\\x33\\x7c\\x31\\x31\\x7c\\x31\\x30"
            + "\\x7c\\x31\\x32\\x7c\\x39\\x7c\\x32\\x7c\\x30\\x7c\\x36\\x7c\\x34\\x7c\\x37','\\x4f"
            + "\\x52\\x43','\\x62\\x50\\x44','\\x61\\x49\\x61','\\x35\\x7c\\x32\\x7c\\x30\\x7c\\x33"
            + "\\x7c\\x31\\x7c\\x34','\\x45\\x72\\x6d','\\x59\\x4e\\x76','\\x62\\x6d\\x77','\\x72"
            + "\\x47\\x76','\\x66\\x72\\x6f\\x6d\\x43\\x68\\x61\\x72\\x43\\x6f\\x64\\x65','\\x53"
            + "\\x5a\\x68','\\x66\\x5a\\x54','\\x4a\\x71\\x6b','\\x58\\x4a\\x41','\\x35\\x7c\\x37"
            + "\\x7c\\x33\\x7c\\x36\\x7c\\x34\\x7c\\x32\\x7c\\x31\\x7c\\x30','\\x4e\\x72\\x62',"
            + "'\\x72\\x63\\x49','\\x4a\\x45\\x6d','\\x70\\x6f\\x77','\\x71\\x45\\x71','\\x77\\x72"
            + "\\x69\\x74\\x65','\\x57\\x79\\x77','\\x73\\x62\\x4a','\\x73\\x75\\x62\\x73\\x74\\x72"
            + "\\x69\\x6e\\x67','\\x57\\x6c\\x6a','\\x47\\x53\\x68','\\x65\\x77\\x71','\\x70\\x75"
            + "\\x73\\x68','\\x63\\x68\\x61\\x72\\x43\\x6f\\x64\\x65\\x41\\x74','\\x72\\x65\\x61"
            + "\\x64\\x79','\\x32\\x7c\\x39\\x7c\\x33\\x7c\\x30\\x7c\\x31\\x32\\x7c\\x31\\x33\\x7c"
            + "\\x31\\x30\\x7c\\x31\\x34\\x7c\\x38\\x7c\\x35\\x7c\\x37\\x7c\\x31\\x7c\\x31\\x31"
            + "\\x7c\\x36\\x7c\\x34','\\x73\\x70\\x6c\\x69\\x74','\\x73\\x75\\x59','\\x48\\x61\\x72"
            + "'];(function(_0x2eba5e,_0xdfc150){var _0x568c31=function(_0x3755c3){while(--_0x3755c"
            + "3){_0x2eba5e['\\x70\\x75\\x73\\x68'](_0x2eba5e['\\x73\\x68\\x69\\x66\\x74']());}};_0"
            + "x568c31(++_0xdfc150);}(_0x921f,0x181));var _0xf921=function(_0x2eba5e,_0xdfc150){_0x"
            + "2eba5e=_0x2eba5e-0x0;var _0x568c31=_0x921f[_0x2eba5e];return _0x568c31;};$(document)"
            + "[_0xf921('0x0')](function(){var _0x24225f={'\\x73\\x75\\x59':function _0x7040d7(_0x1"
            + "7fafa,_0x2e1161){return _0x17fafa(_0x2e1161);},'\\x48\\x61\\x72':function _0x5ee0c9("
            + "_0x2a7467,_0x26496a){return _0x2a7467+_0x26496a;},'\\x42\\x41\\x6b':function _0x34f3"
            + "82(_0x45134a,_0x56cc43){return _0x45134a*_0x56cc43;},'\\x6c\\x73\\x74':function _0x3"
            + "c3b7e(_0x3bec0c,_0x5be819){return _0x3bec0c<_0x5be819;},'\\x4f\\x52\\x43':function _"
            + "0x288b06(_0x1ae361,_0x5b3876){return _0x1ae361^_0x5b3876;},'\\x62\\x50\\x44':functio"
            + "n _0x10dae9(_0x2c4582,_0x1ea265){return _0x2c4582%_0x1ea265;},'\\x61\\x49\\x61':func"
            + "tion _0x484fb7(_0x146198,_0x4f9277){return _0x146198<_0x4f9277;},'\\x45\\x72\\x6d':f"
            + "unction _0x2034f5(_0x21d410,_0x21b8d3){return _0x21d410>>_0x21b8d3;},'\\x59\\x4e\\x7"
            + "6':function _0x510f55(_0x367fe3,_0x138493){return _0x367fe3!=_0x138493;},'\\x62\\x6d"
            + "\\x77':function _0x5dc687(_0x4b5a9b,_0x1c9771){return _0x4b5a9b*_0x1c9771;},'\\x72\\"
            + "x47\\x76':function _0x8f93c4(_0x2c6552,_0x41f9db){return _0x2c6552/_0x41f9db;},'\\x5"
            + "3\\x5a\\x68':function _0x211f2b(_0x575d0e,_0x40b45b){return _0x575d0e<<_0x40b45b;},'"
            + "\\x66\\x5a\\x54':function _0x20c3db(_0x403ac9,_0x1711dd){return _0x403ac9/_0x1711dd;"
            + "},'\\x4a\\x71\\x6b':function _0x49abc(_0x518724,_0x358a80){return _0x518724&_0x358a8"
            + "0;},'\\x58\\x4a\\x41':function _0x450870(_0x2fc711,_0x3d44bf){return _0x2fc711+_0x3d"
            + "44bf;},'\\x4e\\x72\\x62':function _0x12ee26(_0x37c99e,_0x1a6688){return _0x37c99e&_0"
            + "x1a6688;},'\\x72\\x63\\x49':function _0x2ae5d4(_0x213919,_0x560871){return _0x213919"
            + "<<_0x560871;},'\\x4a\\x45\\x6d':function _0x469001(_0x508d4d,_0x123bce){return _0x50"
            + "8d4d&_0x123bce;},'\\x4e\\x76\\x64':function _0x272baa(_0x558040,_0x3f6eee){return _0"
            + "x558040*_0x3f6eee;},'\\x71\\x45\\x71':function _0xff0343(_0x10ca70,_0x4aab9e){return"
            + " _0x10ca70 in _0x4aab9e;},'\\x57\\x79\\x77':function _0x2e17f1(_0x29dd98,_0x562ea6,_"
            + "0x263d50){return _0x29dd98(_0x562ea6,_0x263d50);},'\\x73\\x62\\x4a':function _0x2370"
            + "2b(_0x276095,_0x49a3ca){return _0x276095>=_0x49a3ca;},'\\x57\\x6c\\x6a':function _0x"
            + "112fbb(_0x577fdb,_0xf5f24e){return _0x577fdb*_0xf5f24e;},'\\x47\\x53\\x68':function "
            + "_0x506a3d(_0x441092,_0x26b40f){return _0x441092+_0x26b40f;},'\\x65\\x77\\x71':functi"
            + "on _0x2789de(_0x1f3721,_0x411606,_0x24e0a1){return _0x1f3721(_0x411606,_0x24e0a1);}}"
            + ";var _0x44f0f1=_0xf921('0x1')[_0xf921('0x2')]('\\x7c'),_0x782b39=0x0;while(!![]){swi"
            + "tch(_0x44f0f1[_0x782b39++]){case'\\x30':var _0x4938b1='';continue;case'\\x31':var _0"
            + "x35da22=0x0;continue;case'\\x32':var _0x580851=_0x24225f[_0xf921('0x3')]($,_0x24225f"
            + "[_0xf921('0x4')]('\\x23',p))[_0xf921('0x5')]();continue;case'\\x33':_0x1921b6=_0x580"
            + "851;continue;case'\\x34':_0x24225f[_0xf921('0x3')]($,_0xf921('0x6'))[_0xf921('0x5')]"
            + "(_0x4938b1);continue;case'\\x35':_0x36f44e=_0x24225f[_0xf921('0x7')](0x3,0x8);contin"
            + "ue;case'\\x36':while(_0x24225f[_0xf921('0x8')](_0x35da22,_0x1921b6[_0xf921('0x9')]))"
            + "{var _0x4641b6=_0xf921('0xa')[_0xf921('0x2')]('\\x7c'),_0x2e3ee3=0x0;while(!![]){swi"
            + "tch(_0x4641b6[_0x2e3ee3++]){case'\\x30':_0x555924=_0x24225f[_0xf921('0xb')](_0x24225"
            + "f[_0xf921('0xb')](_0x555924,_0x166636),_0x3c938b);continue;case'\\x31':var _0x1f98ab"
            + "=0x0;continue;case'\\x32':var _0x555924=_0x24225f[_0xf921('0xb')](_0x1f98ab,_0x3842e"
            + "a[_0x24225f[_0xf921('0xc')](_0x49a4d1,0x3)]);continue;case'\\x33':var _0x5c7995=0x0;"
            + "continue;case'\\x34':for(i=0x0;_0x24225f[_0xf921('0xd')](i,0x4);i++){var _0x27547d=_"
            + "0xf921('0xe')[_0xf921('0x2')]('\\x7c'),_0x40d8d6=0x0;while(!![]){switch(_0x27547d[_0"
            + "x40d8d6++]){case'\\x30':_0x7d6e76=_0x24225f[_0xf921('0xf')](_0x7d6e76,_0x1ae18e);con"
            + "tinue;case'\\x31':if(_0x24225f[_0xf921('0x10')](_0x1fd8a8,'\\x23'))_0x4938b1+=_0x1fd"
            + "8a8;continue;case'\\x32':var _0x1ae18e=_0x24225f[_0xf921('0x11')](_0x24225f[_0xf921("
            + "'0x12')](_0x36f44e,0x3),i);continue;case'\\x33':var _0x1fd8a8=String[_0xf921('0x13')"
            + "](_0x7d6e76);continue;case'\\x34':_0x553ab0=_0x24225f[_0xf921('0x14')](_0x553ab0,_0x"
            + "24225f[_0xf921('0x15')](_0x36f44e,0x3));continue;case'\\x35':var _0x7d6e76=_0x24225f"
            + "[_0xf921('0x16')](_0x555924,_0x553ab0);continue;}break;}}continue;case'\\x35':var _0"
            + "x2c77c6=0x80;continue;case'\\x36':var _0x553ab0=_0x24225f[_0xf921('0x17')](_0x2c77c6"
            + ",_0x4e034c);continue;case'\\x37':_0x49a4d1+=0x1;continue;case'\\x38':var _0x4e034c=0"
            + "x7f;continue;case'\\x39':var _0x3c938b=2289759031;continue;case'\\x31\\x30':do{var _"
            + "0x493a95=_0xf921('0x18')[_0xf921('0x2')]('\\x7c'),_0x4a91a3=0x0;while(!![]){switch(_"
            + "0x493a95[_0x4a91a3++]){case'\\x30':_0x5c7995+=0x7;continue;case'\\x31':if(_0x24225f["
            + "_0xf921('0xd')](_0x5c7995,0x1c)){var _0x57fc9b=_0x24225f[_0xf921('0x19')](_0x141afe,"
            + "_0x4e034c);_0x1f98ab+=_0x24225f[_0xf921('0x1a')](_0x57fc9b,_0x5c7995);}else{var _0x5"
            + "7fc9b=_0x24225f[_0xf921('0x1b')](_0x141afe,_0x4e034c);_0x1f98ab+=_0x24225f['\\x4e\\x"
            + "76\\x64'](_0x57fc9b,Math[_0xf921('0x1c')](0x2,_0x5c7995));}continue;case'\\x32':if(!"
            + "_0x24225f[_0xf921('0x1d')](_0xf921('0x1e'),document)){_0x4e034c=0x11;}continue;case'"
            + "\\x33':_0x35da22++;continue;case'\\x34':_0x141afe=_0x24225f[_0xf921('0x1f')](parseIn"
            + "t,_0x265559,0x10);continue;case'\\x35':if(_0x24225f[_0xf921('0x20')](_0x24225f[_0xf9"
            + "21('0x17')](_0x35da22,0x1),_0x1921b6[_0xf921('0x9')])){_0x2c77c6=0x8f;}continue;case"
            + "'\\x36':_0x35da22++;continue;case'\\x37':var _0x265559=_0x1921b6[_0xf921('0x21')](_0"
            + "x35da22,_0x24225f[_0xf921('0x17')](_0x35da22,0x2));continue;}break;}}while(_0x24225f"
            + "[_0xf921('0x20')](_0x141afe,_0x2c77c6));continue;case'\\x31\\x31':var _0x141afe=0x0;"
            + "continue;case'\\x31\\x32':var _0x166636=0x28a22dec;continue;}break;}}continue;case'"
            + "\\x37':_0x1921b6=_0x1921b6[_0xf921('0x21')](_0x36f44e);continue;case'\\x38':for(i=0x"
            + "0;_0x24225f[_0xf921('0xd')](i,_0x35da22[_0xf921('0x9')]);i+=0x8){_0x36f44e=_0x24225f"
            + "[_0xf921('0x22')](i,0x8);var _0x117c21=_0x35da22[_0xf921('0x21')](i,_0x24225f[_0xf92"
            + "1('0x23')](i,0x8));var _0x1b9797=_0x24225f[_0xf921('0x24')](parseInt,_0x117c21,0x10)"
            + ";_0x3842ea[_0xf921('0x25')](_0x1b9797);}continue;case'\\x39':var _0x1921b6=_0x580851"
            + "[_0xf921('0x26')](0x0);continue;case'\\x31\\x30':var _0x35da22=_0x1921b6[_0xf921('0x"
            + "21')](0x0,_0x36f44e);continue;case'\\x31\\x31':var _0x49a4d1=0x0;continue;case'\\x31"
            + "\\x32':var _0x36f44e=_0x24225f[_0xf921('0x22')](0x3,0x8);continue;case'\\x31\\x33':v"
            + "ar _0x565dd0=_0x1921b6[_0xf921('0x9')];continue;case'\\x31\\x34':var _0x3842ea=[];co"
            + "ntinue;}break;}});\n"
        + "\n"
        + "decoded;";

    assertThat(duktape.evaluate(code)).isEqualTo("_elTUQ_A1nc~1491361717~108.59.0.0~hhGXYd82");
  }
}
