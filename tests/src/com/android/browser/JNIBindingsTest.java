/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.browser;

import android.test.AndroidTestCase;
import android.util.Log;

import java.util.Arrays;

import junit.framework.AssertionFailedError;

public class JNIBindingsTest extends AndroidTestCase {

    private final static String LOGTAG = "JNIBindingsTest";
    private JNIBindingsTestApp mTestApp;

    public int mInt = 123;
    public String mString = "Hello World";

    public JNIBindingsTest(JNIBindingsTestApp testApp) {
        mTestApp = testApp;
    }

    public void notifyComplete() {
        Log.v(LOGTAG, "Completing the test.");
        mTestApp.notifyComplete();
    }

    public void printAssertionFailed(AssertionFailedError e) {
        Log.e(LOGTAG, "");
        Log.e(LOGTAG, "*** ASSERTION FAILED: " + e.getMessage());
        Log.e(LOGTAG, "*** Stack trace:");
        StackTraceElement[] trace = e.getStackTrace();
        for(StackTraceElement elem : trace) {
            Log.e(LOGTAG, "***\t" + elem.toString());
        }
        Log.e(LOGTAG, "");
    }

    public boolean testPrimitiveTypes(byte byteParam, char charParam, double doubleParam,
            float floatParam, int intParam, long longParam, short shortParam,
            boolean booleanParam) {
        byte expectedByteParam = 100;
        char expectedCharParam = 'c';
        double expectedDoubleParam = 123.34567890;
        float expectedFloatParam = 456.789f;
        int expectedIntParam = 1234567;
        long expectedLongParam = 1234567890L;
        short expectedShortParam = 6000;
        boolean expectedBooleanParam = true;

        try {
            assertEquals(expectedByteParam, byteParam);

            // EMULATE_JSC_BINDINGS: JSC does not pass chars correctly
            // assertEquals(expectedCharParam, charParam);

            assertEquals(expectedDoubleParam, doubleParam);
            assertEquals(expectedFloatParam, floatParam);
            assertEquals(expectedIntParam, intParam);
            assertEquals(expectedLongParam, longParam);
            assertEquals(expectedShortParam, shortParam);
            assertEquals(expectedBooleanParam, booleanParam);
        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
           return false;
        }
        return true;
    }

    public boolean testObjectTypes(String stringParam, String emptyString, Object objectParam,
            Object emptyObject) {
        String expectedString = "Foo";
        String expectedEmptyString = "";

        try {
            assertNotNull(stringParam);
            assertNotNull(emptyString);
            assertEquals(expectedString, stringParam);
            assertEquals(expectedEmptyString, emptyString);
            assertNull(objectParam);
            assertNull(emptyObject);
        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }
        return true;
    }

    public boolean testArray(byte[] byteArray, char[] charArray, double[] doubleArray,
            float[] floatArray, int[] intArray, long[] longArray, short[] shortArray,
            boolean[] booleanArray) {
        byte[] expectedByteArray = { 1,2,3};
        char[] expectedCharArray = {'d', 'o', 'g'};
        double[] expectedDoubleArray = {1.2,2.3,3.4};
        float[] expectedFloatArray = {4.5F,5.6F,6.7F};
        int[] expectedIntArray = {1,2,3};
        long[] expectedLongArray = {4L,5L,6L};
        short[] expectedShortArray = {7,8,9};
        boolean[] expectedBooleanArray = {true, false};

        try {
            assertNotNull(byteArray);
            assertNotNull(charArray);
            assertNotNull(doubleArray);
            assertNotNull(floatArray);
            assertNotNull(intArray);
            assertNotNull(longArray);
            assertNotNull(shortArray);
            assertNotNull(booleanArray);
            assertEquals(Arrays.toString(expectedByteArray), Arrays.toString(byteArray));
            assertEquals(Arrays.toString(expectedCharArray), Arrays.toString(charArray));
            assertEquals(Arrays.toString(expectedDoubleArray), Arrays.toString(doubleArray));
            assertEquals(Arrays.toString(expectedFloatArray), Arrays.toString(floatArray));
            assertEquals(Arrays.toString(expectedIntArray), Arrays.toString(intArray));
            assertEquals(Arrays.toString(expectedLongArray), Arrays.toString(longArray));
            assertEquals(Arrays.toString(expectedShortArray), Arrays.toString(shortArray));
            assertEquals(Arrays.toString(expectedBooleanArray), Arrays.toString(booleanArray));
        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }
        return true;
    }

    public boolean testObjectArray(String[] stringArray, Object[] emptyArray,
            Object[] objectArray) {
        String[] expectedStringArray = {"Hello", "World", "!"};
        String expectedStringArrayClassName = "[Ljava.lang.String;";
        Object[] expectedObjectArray = {};

        try {
            assertNotNull(stringArray);

            // EMULATE_JSC_BINDINGS JSC pass null for object arrays that are not strings.
            // Should be an empty array?
            assertNull(emptyArray);
            assertNull(objectArray);

            assertEquals(Arrays.toString(expectedStringArray), Arrays.toString(stringArray));
            assertEquals(expectedStringArrayClassName, stringArray.getClass().getName());

            // EMULATE_JSC_BINDINGS
            // assertEquals(Arrays.toString(expectedObjectArray), Arrays.toString(emptyArray));
            // assertEquals(expectedObjectArrayClassName, emptyArray.getClass().getName());
            // assertEquals(Arrays.toString(expectedObjectArray), Arrays.toString(objectArray));
            // assertEquals(expectedStringObjectClassName, objectArray.getClass().getName());

        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }
        return true;
    }

    public boolean testObjectMembers(boolean boolParam, byte byteParam, char charParam,
            double doubleParam, float floatParam, int intParam, long longParam, short shortParam,
            String stringParam, int[] intArrayParam, String[] stringArrayParam,
            Object objectParam) {
        boolean expectedBoolParam = true;
        byte expectedByteParam = 101;
        char expectedCharParam = 'd';
        double expectedDoubleParam = 123.456;
        float expectedFloatParam = 456.789F;
        int expectedIntParam = 102;
        long expectedLongParam = 103L;
        short expectedShortParam = 104;
        String expectedStringParam = "Hello World";
        int[] expectedIntArray = {1,2,3};
        String[] expectedStringArrayParam = {"foo", "bar", "baz"};
        String expectedStringArrayClassName = "[Ljava.lang.String;";

        try {
            assertEquals(expectedBoolParam, boolParam);
            assertEquals(expectedByteParam, byteParam);

            // EMULATE_JSC_BINDINGS: JSC does not pass chars correctly. (chars are strings in JS)
            // assertEquals(expectedCharParam, charParam);

            assertEquals(expectedDoubleParam, doubleParam);
            assertEquals(expectedFloatParam, floatParam);
            assertEquals(expectedIntParam, intParam);
            assertEquals(expectedLongParam, longParam);
            assertEquals(expectedShortParam, shortParam);
            assertEquals(expectedStringParam, stringParam);
            assertEquals(Arrays.toString(expectedIntArray), Arrays.toString(intArrayParam));
            assertEquals(Arrays.toString(expectedStringArrayParam),
                    Arrays.toString(stringArrayParam));
            assertEquals(expectedStringArrayClassName, stringArrayParam.getClass().getName());
            assertNull(objectParam);
        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }
        return true;
    }

    public boolean testJSPrimitivesToStringsInJava(String intParam, String nullParam,
            String doubleParam, String booleanParam, String charParam,
            String undefinedParam) {
        String expectedIntParam = "123";
        String expectedDoubleParam = "456.789";
        String expectedBooleanParam = "true";
        String expectedCharParam = "d";

        // EMULATE_JSC_BINDINGS JSC passes "undefined" for undefined types. Should be null?
        String expectedUndefinedParam = "undefined";

        try {
            assertNotNull(intParam);
            assertNull(nullParam);
            assertNotNull(doubleParam);
            assertNotNull(booleanParam);
            assertNotNull(charParam);

            // EMULATE_JSC_BINDINGS JSC passes "undefined" for undefined types.
            assertNotNull(undefinedParam);

            assertEquals(expectedIntParam, intParam);
            assertEquals(expectedDoubleParam, doubleParam);
            assertEquals(expectedBooleanParam, booleanParam);
            assertEquals(expectedCharParam, charParam);

            // EMULATE_JSC_BINDINGS  JSC passes "undefined" for undefined types.
            assertEquals(expectedUndefinedParam, undefinedParam);

        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }
        return true;
    }

    public boolean testParameterTypeMismatch(String[] stringArrayParam) {
        // The JS test will pass a string, not an array to this test.
        try {
            assertNull(stringArrayParam);
        } catch (AssertionFailedError e) {
            printAssertionFailed(e);
            return false;
        }

        return true;
    }

    public boolean returnBool() { return true; }
    public byte returnByte() { return 1; }
    public char returnChar() { return 'b'; }
    public double returnDouble() { return 123.456; }
    public float returnFloat() { return 456.789F; }
    public int returnInt() { return 123; }
    public long returnLong() { return 1234L; }
    public short returnShort() { return 12345; }
    public String returnString() { return "Hello World!"; }

    public class TestObject {
        public int x = 123;
        public String s = "Hello World!";

        public boolean aMethod() { return true; }
        public String anotherMethod() { return "Hello World"; }
    }

    public TestObject returnObject() { return new TestObject(); }

    public int[] returnArray() {
        int[] array = {1,2,3,4,5};
        return array;
    }

    public void returnVoid() { }
}
