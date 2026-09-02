package com.comext.livechattts.adapter.in.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON codec for the local API; it accepts JSON objects only and has no external dependency. */
final class HttpJson {
    private HttpJson() { }
    static String field(String input, String name) { Object value = parseObject(input).get(name); return value instanceof String string ? string : null; }
    static Integer integer(String input, String name) { Object value = parseObject(input).get(name); if (!(value instanceof java.math.BigDecimal number)) return null; try { return number.intValueExact(); } catch (ArithmeticException exception) { throw new IllegalArgumentException(name + " debe ser un entero"); } }
    static String object(Map<String, ?> values) { return encode(values); }
    private static Map<String, Object> parseObject(String input) { Object parsed = new StrictJsonParser(input == null ? "" : input).parse(); if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("Se esperaba un objeto JSON"); Map<String, Object> result = new LinkedHashMap<>(); map.forEach((key, value) -> result.put(String.valueOf(key), value)); return result; }
    private static String encode(Object value) { if (value == null) return "null"; if (value instanceof Number || value instanceof Boolean) return value.toString(); if (value instanceof Map<?, ?> map) { List<String> items = new ArrayList<>(); map.forEach((key, nested) -> items.add(encode(String.valueOf(key)) + ":" + encode(nested))); return "{" + String.join(",", items) + "}"; } if (value instanceof Iterable<?> iterable) { List<String> items = new ArrayList<>(); iterable.forEach(item -> items.add(encode(item))); return "[" + String.join(",", items) + "]"; } return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""; }
}
