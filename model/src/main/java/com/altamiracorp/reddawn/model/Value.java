package com.altamiracorp.reddawn.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

public class Value {
    private final byte[] value;

    public Value(Object value) {
        if (value == null) {
            throw new NullPointerException("Value cannot be null");
        }
        this.value = toBytes(value);
    }

    private byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return stringToBytes((String) value);
        }

        if (value instanceof Long) {
            return longToBytes((Long) value);
        }

        if (value instanceof Double) {
            return doubleToBytes((Double) value);
        }

        if (value instanceof byte[]) {
            return (byte[]) value;
        }

        throw new RuntimeException("Unhandled type to convert: " + value.getClass().getName());
    }

    private byte[] doubleToBytes(Double value) {
        return ByteBuffer.allocate(8).putDouble(value).array();
    }

    private byte[] stringToBytes(String value) {
        return value.getBytes();
    }

    private byte[] longToBytes(Long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    public byte[] toBytes() {
        return this.value;
    }

    public Long toLong() {
        if (this.value.length != 8) {
            throw new RuntimeException("toLong failed. Expected 8 bytes found " + this.value.length);
        }
        return ByteBuffer.wrap(this.value).getLong();
    }

    public Double toDouble() {
        if (this.value.length != 8) {
            throw new RuntimeException("toDouble failed. Expected 8 bytes found " + this.value.length);
        }
        return ByteBuffer.wrap(this.value).getDouble();
    }

    @Override
    public String toString() {
        return new String(this.value);
    }

    public static byte[] toBytes(Value value) {
        if (value == null) {
            return null;
        }
        return value.toBytes();
    }

    public static String toString(Value value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public static Long toLong(Value value) {
        if (value == null) {
            return null;
        }
        return value.toLong();
    }

    public static Double toDouble(Value value) {
        if (value == null) {
            return null;
        }
        return value.toDouble();
    }

    public static JSONObject toJson(Value value) {
        if (value == null) {
            return null;
        }
        try {
            String str = toString(value);
            if (str.trim().length() == 0) {
                return null;
            }
            return new JSONObject(str);
        } catch (JSONException e) {
            throw new RuntimeException("Could not parse JSON", e);
        }
    }
}
