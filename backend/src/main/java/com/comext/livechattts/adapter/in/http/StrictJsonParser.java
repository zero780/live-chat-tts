package com.comext.livechattts.adapter.in.http;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StrictJsonParser {
    private final String source;
    private int index;
    StrictJsonParser(String source) { this.source = source; }
    Object parse() { Object value = value(); skipSpace(); if (index != source.length()) throw invalid(); return value; }
    private Object value() { skipSpace(); if (index >= source.length()) throw invalid(); return switch (source.charAt(index)) { case '{' -> object(); case '[' -> array(); case '"' -> string(); case 't' -> literal("true", Boolean.TRUE); case 'f' -> literal("false", Boolean.FALSE); case 'n' -> literal("null", null); default -> number(); }; }
    private Map<String, Object> object() { expect('{'); Map<String, Object> result = new LinkedHashMap<>(); if (take('}')) return result; while (true) { skipSpace(); if (index >= source.length() || source.charAt(index) != '"') throw invalid(); String key = string(); expect(':'); result.put(key, value()); skipSpace(); if (take('}')) return result; expect(','); } }
    private List<Object> array() { expect('['); List<Object> result = new ArrayList<>(); if (take(']')) return result; while (true) { result.add(value()); skipSpace(); if (take(']')) return result; expect(','); } }
    private String string() { expect('"'); StringBuilder result = new StringBuilder(); while (index < source.length()) { char current = source.charAt(index++); if (current == '"') return result.toString(); if (current < 0x20) throw invalid(); if (current != '\\') { result.append(current); continue; } appendEscape(result); } throw invalid(); }
    private void appendEscape(StringBuilder result) { if (index >= source.length()) throw invalid(); char escape = source.charAt(index++); switch (escape) { case '"', '\\', '/' -> result.append(escape); case 'b' -> result.append('\b'); case 'f' -> result.append('\f'); case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t'); case 'u' -> appendUnicode(result); default -> throw invalid(); } }
    private void appendUnicode(StringBuilder result) { if (index + 4 > source.length()) throw invalid(); try { result.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); index += 4; } catch (NumberFormatException error) { throw invalid(); } }
    private Number number() { int start = index; take('-'); digits(); if (take('.')) digits(); if (take('e') || take('E')) { take('+'); take('-'); digits(); } try { return new BigDecimal(source.substring(start, index)); } catch (NumberFormatException error) { throw invalid(); } }
    private void digits() { if (index >= source.length() || !Character.isDigit(source.charAt(index))) throw invalid(); if (source.charAt(index) == '0') index++; else while (index < source.length() && Character.isDigit(source.charAt(index))) index++; }
    private Object literal(String literal, Object value) { if (!source.startsWith(literal, index)) throw invalid(); index += literal.length(); return value; }
    private void skipSpace() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
    private void expect(char expected) { skipSpace(); if (!take(expected)) throw invalid(); }
    private boolean take(char expected) { if (index < source.length() && source.charAt(index) == expected) { index++; return true; } return false; }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("JSON invalido"); }
}
