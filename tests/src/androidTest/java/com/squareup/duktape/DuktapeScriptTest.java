package com.squareup.duktape;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public final class DuktapeScriptTest {
	private Duktape duktape;

	@Before public void setUp() throws Exception {
		duktape = Duktape.create();
	}

	@After public void tearDown() throws Exception {
		duktape.close();
	}

	@Test public void testGetDouble() throws Exception {
		DuktapeScript script = duktape.loadScript("var value = 3.2;");

		double value = script.getDouble("value");

		assertThat(value).isWithin(0.1).of(3.2);

		script.close();
	}

	@Test public void testGetString() throws Exception {
		DuktapeScript script = duktape.loadScript("var valueStr = \"String from JS\";");

		assertThat(script.getString("valueStr")).isEqualTo("String from JS");

		script.close();
	}

	@Test public void testGetLong() throws Exception {
		DuktapeScript script = duktape.loadScript(
				"var smallValue = 123;" +
				"var bigValue = 999999999999999;");

		assertThat(script.getLong("smallValue")).isEqualTo(123);
		assertThat(script.getLong("bigValue")).isEqualTo(999999999999999L);

		script.close();
	}

	@Test public void testGetBoolean() throws Exception {
		DuktapeScript script = duktape.loadScript(
				"var trueValue = true;\n" +
				"var falseValue = false;\n" +
				"var isEqual = 1 == 1;");

		assertThat(script.getBoolean("trueValue")).isTrue();
		assertThat(script.getBoolean("isEqual")).isTrue();
		assertThat(script.getBoolean("falseValue")).isFalse();

		script.close();
	}

	@Test public void testSumFunction() throws Exception {
		DuktapeScript script = duktape.loadScript("function sum() {\n" +
				"    return 1 + 2;\n" +
				"}");

		assertThat(script.callFunction("sum")).isEqualTo("3");

		script.close();
	}

	@Test public void testSumWithArgs() throws Exception {
		DuktapeScript script = duktape.loadScript("function sum(a, b) {\n" +
				"    return a + b;\n" +
				"}");

		assertThat(script.callFunction("sum", 3, 6)).isEqualTo("9");

		script.close();
	}

	@Test public void testCallFunction() throws Exception {
		DuktapeScript script = duktape.loadScript("function sayHello(name) {\n" +
				"    return name;" +
				"}");

		String result = script.callFunction("sayHello", "World");

		assertThat(result).isEqualTo("World");

		script.close();
	}

	@Test public void testInsertValue() throws Exception {
		DuktapeScript script = duktape.loadScript("function displayName() {\n" +
				"    return name;\n" +
				"}");

		script.put("name", "Value from Java!");

		assertThat(script.callFunction("displayName")).isEqualTo("Value from Java!");

		script.close();
	}

	@Test public void testChangeValue() throws Exception {
		DuktapeScript script = duktape.loadScript(
				"var name = \"Pietro\";\n" +
				"function displayName() {\n" +
				"    return \"Name is \" + name;\n" +
				"}");

		assertThat(script.callFunction("displayName")).isEqualTo("Name is Pietro");

		script.put("name", "Jack");

		assertThat(script.callFunction("displayName")).isEqualTo("Name is Jack");

		script.close();
	}

	@Test public void testPutValues() throws Exception {
		DuktapeScript script = duktape.loadScript("");

		script.put("name", "Pietro");
		script.put("year", 1992L);
		script.put("value", true);
		script.put("floatPoint", 3.56);
		script.put("nullObject", null);

		assertThat(script.getBoolean("value")).isTrue();
		assertThat(script.getDouble("floatPoint")).isWithin(0.1).of(3.56);
		assertThat(script.getLong("year")).isEqualTo(1992L);
		assertThat(script.getString("nullObject")).isNull();
		assertThat(script.isNull("nullObject")).isTrue();
		assertThat(script.getString("name")).isEqualTo("Pietro");

		script.close();
	}
}
