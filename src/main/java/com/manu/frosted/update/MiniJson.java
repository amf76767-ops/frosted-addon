package com.manu.frosted.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON reader (objects, arrays, strings, numbers, booleans, null). It exists so the
 * updater has no dependency on a particular JSON library. Objects become Map, arrays List.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        Object value = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.error("trailing data");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String string(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String str ? str : null;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        return m != null && Boolean.TRUE.equals(m.get(key));
    }

    public static long number(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Double d ? d.longValue() : 0L;
    }

    // ------------------------------------------------------------------ parser

    private Object value() {
        ws();
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        ws();
        if (peek() == '}') { i++; return map; }
        while (true) {
            ws();
            if (peek() != '"') throw error("expected key");
            String key = str();
            ws();
            if (peek() != ':') throw error("expected ':'");
            i++;
            map.put(key, value());
            ws();
            char c = peek();
            i++;
            if (c == '}') return map;
            if (c != ',') throw error("expected ',' or '}'");
        }
    }

    private List<Object> arr() {
        List<Object> list = new ArrayList<>();
        i++; // [
        ws();
        if (peek() == ']') { i++; return list; }
        while (true) {
            list.add(value());
            ws();
            char c = peek();
            i++;
            if (c == ']') return list;
            if (c != ',') throw error("expected ',' or ']'");
        }
    }

    private String str() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (true) {
            if (i >= s.length()) throw error("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) throw error("bad escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: throw error("bad escape");
            }
        }
    }

    private Double num() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("unexpected character '" + s.charAt(i) + "'");
        return Double.parseDouble(s.substring(start, i));
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw error("expected " + word);
        i += word.length();
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON: " + msg + " at " + i);
    }
}
