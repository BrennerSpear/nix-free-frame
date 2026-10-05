package works.tycho.frame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restricted data-only JavaScript literal reader. Never evaluates code. */
final class JsDataParser {
    private final String text;
    private int pos, nodes;
    private JsDataParser(String text) { this.text = text; }
    static Object parse(String text) throws IOException {
        if (text.length() > 8 * 1024 * 1024) throw bad();
        JsDataParser parser = new JsDataParser(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.pos != text.length()) throw bad();
        return value;
    }
    static IOException bad() { return new IOException("Unsupported album metadata"); }
    private void space() throws IOException {
        while (pos < text.length()) {
            if (Character.isWhitespace(text.charAt(pos))) { pos++; continue; }
            if (text.startsWith("//", pos)) {
                int end = text.indexOf('\n', pos + 2); pos = end < 0 ? text.length() : end + 1;
            } else if (text.startsWith("/*", pos)) {
                int end = text.indexOf("*/", pos + 2); if (end < 0) throw bad(); pos = end + 2;
            } else break;
        }
    }
    private boolean take(char c) throws IOException {
        space(); if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; } return false;
    }
    private Object value(int depth) throws IOException {
        if (depth > 64 || ++nodes > 200000) throw bad();
        space(); if (pos == text.length()) throw bad();
        char c = text.charAt(pos);
        if (c == '\'' || c == '"') return string();
        if (take('[')) {
            List<Object> list = new ArrayList<>();
            if (take(']')) return list;
            while (true) {
                list.add(value(depth + 1));
                if (take(']')) return list;
                if (!take(',')) throw bad();
                if (take(']')) return list;
            }
        }
        if (take('{')) {
            Map<String,Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            while (true) {
                space(); if (pos == text.length()) throw bad();
                String key;
                c = text.charAt(pos);
                if (c == '\'' || c == '"') key = string();
                else {
                    int start = pos;
                    while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_' || text.charAt(pos) == '$')) pos++;
                    if (pos == start) throw bad(); key = text.substring(start, pos);
                }
                if (!take(':') || map.containsKey(key)) throw bad();
                map.put(key, value(depth + 1));
                if (take('}')) return map;
                if (!take(',')) throw bad();
                if (take('}')) return map;
            }
        }
        int start = pos;
        while (pos < text.length() && ",]} \r\n\t".indexOf(text.charAt(pos)) < 0) pos++;
        String token = text.substring(start, pos);
        if (token.equals("null")) return null;
        if (token.equals("true")) return Boolean.TRUE;
        if (token.equals("false")) return Boolean.FALSE;
        if (!token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")) throw bad();
        try { double number = Double.parseDouble(token); if (Double.isInfinite(number)) throw bad(); return number; }
        catch (NumberFormatException e) { throw bad(); }
    }
    private String string() throws IOException {
        char quote = text.charAt(pos++);
        StringBuilder result = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == quote) return result.toString();
            if (c < 32) throw bad();
            if (c == '\\') {
                if (pos == text.length()) throw bad(); c = text.charAt(pos++);
                switch (c) {
                    case 'n': c = '\n'; break; case 'r': c = '\r'; break; case 't': c = '\t'; break;
                    case 'b': c = '\b'; break; case 'f': c = '\f'; break;
                    case '\\': case '/': case '\'': case '"': break;
                    case 'u': case 'x':
                        int count = c == 'u' ? 4 : 2;
                        if (pos + count > text.length()) throw bad();
                        try { c = (char) Integer.parseInt(text.substring(pos, pos + count), 16); }
                        catch (NumberFormatException e) { throw bad(); }
                        pos += count; break;
                    default: throw bad();
                }
            }
            result.append(c);
        }
        throw bad();
    }
}
