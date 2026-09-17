package learning.storefront.infrai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) result.append(',');
                first = false;
                result.append(write(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder result = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) result.append(',');
                first = false;
                result.append(write(item));
            }
            return result.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    static Map<String, Object> readObject(String source) {
        Object value = new Parser(source).parse();
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected a JSON object");
        Map<String, Object> object = new LinkedHashMap<>();
        raw.forEach((key, item) -> object.put(String.valueOf(key), item));
        return object;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Object parse() {
            Object value = value();
            whitespace();
            if (index != source.length()) throw invalid();
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) throw invalid();
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            index++;
            whitespace();
            if (take(']')) return result;
            do {
                result.add(value());
                whitespace();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char current = source.charAt(index++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                if (index >= source.length()) throw invalid();
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw invalid();
                        result.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw invalid();
                }
            }
            throw invalid();
        }

        private Object number() {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            if (start == index) throw invalid();
            String text = source.substring(start, index);
            return text.contains(".") || text.contains("e") || text.contains("E")
                    ? Double.parseDouble(text) : Long.parseLong(text);
        }

        private Object literal(String text, Object value) {
            if (!source.startsWith(text, index)) throw invalid();
            index += text.length();
            return value;
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw invalid();
        }

        private IllegalArgumentException invalid() {
            return new IllegalArgumentException("Invalid JSON at character " + index);
        }
    }
}
