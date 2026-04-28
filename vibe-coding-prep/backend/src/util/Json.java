package util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny JSON reader/writer.
 *
 * We don't pull in Gson or Jackson — the JDK has no built-in JSON, but our
 * needs are simple: parse objects/arrays of strings and numbers, and write
 * them back out. This keeps the project zero-dependency.
 *
 * Usage:
 *   Map<String,Object> obj = Json.parseObject(body);
 *   String  username = (String) obj.get("username");
 *
 *   String out = Json.write(Map.of("ok", true, "id", 5));
 */
public class Json {

    // =====================================================================
    //  WRITER
    // =====================================================================

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            writeMap(sb, (Map<?, ?>) v);
        } else if (v instanceof List) {
            writeList(sb, (List<?>) v);
        } else if (v.getClass().isArray()) {
            Object[] arr = (Object[]) v;
            writeList(sb, java.util.Arrays.asList(arr));
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, e.getKey().toString());
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list) {
        sb.append('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(sb, o);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else          sb.append(c);
            }
        }
        sb.append('"');
    }

    // =====================================================================
    //  PARSER
    // =====================================================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {
        Object o = parse(s);
        if (!(o instanceof Map)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>) o;
    }

    public static Object parse(String s) {
        Parser p = new Parser(s == null ? "" : s.trim());
        Object v = p.readValue();
        p.skipWs();
        if (p.pos < p.src.length()) throw new IllegalArgumentException(
                "Unexpected trailing content at position " + p.pos);
        return v;
    }

    private static class Parser {
        final String src;
        int pos;
        Parser(String s) { this.src = s; }

        Object readValue() {
            skipWs();
            if (pos >= src.length()) throw new IllegalArgumentException("Empty input");
            char c = src.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': case 'f': return readBool();
                case 'n': return readNull();
                default:  return readNumber();
            }
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWs();
                String k = readString();
                skipWs();
                expect(':');
                Object v = readValue();
                m.put(k, v);
                skipWs();
                char c = src.charAt(pos++);
                if (c == ',') continue;
                if (c == '}') return m;
                throw new IllegalArgumentException("Expected , or } at " + pos);
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> a = new ArrayList<>();
            skipWs();
            if (peek() == ']') { pos++; return a; }
            while (true) {
                a.add(readValue());
                skipWs();
                char c = src.charAt(pos++);
                if (c == ',') continue;
                if (c == ']') return a;
                throw new IllegalArgumentException("Expected , or ] at " + pos);
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            int code = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            sb.append((char) code);
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException("Bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Boolean readBool() {
            if (src.startsWith("true",  pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("Expected true/false at " + pos);
        }

        Object readNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new IllegalArgumentException("Expected null at " + pos);
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length() && "0123456789.eE+-".indexOf(src.charAt(pos)) >= 0) pos++;
            String n = src.substring(start, pos);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            try { return Long.parseLong(n); }
            catch (NumberFormatException e) { return Double.parseDouble(n); }
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }
        char peek() { return src.charAt(pos); }
        void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c)
                throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            pos++;
        }
    }
}
