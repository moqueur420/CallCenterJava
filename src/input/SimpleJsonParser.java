package input;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {
    private final String text;
    private int index;

    private SimpleJsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        return new SimpleJsonParser(text).parseDocument();
    }

    private Object parseDocument() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (!isAtEnd()) {
            throw error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        if (isAtEnd()) {
            throw error("Unexpected end of input");
        }

        char current = currentChar();
        if (current == '{') {
            return parseObject();
        }
        if (current == '[') {
            return parseArray();
        }
        if (current == '"') {
            return parseString();
        }
        if (current == 't') {
            consumeLiteral("true");
            return Boolean.TRUE;
        }
        if (current == 'f') {
            consumeLiteral("false");
            return Boolean.FALSE;
        }
        if (current == 'n') {
            consumeLiteral("null");
            return null;
        }
        if (current == '-' || Character.isDigit(current)) {
            return parseNumber();
        }

        throw error("Unexpected character: " + current);
    }

    private Map<String, Object> parseObject() {
        expect('{');
        skipWhitespace();

        Map<String, Object> result = new LinkedHashMap<>();
        if (tryConsume('}')) {
            return result;
        }

        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, parseValue());
            skipWhitespace();

            if (tryConsume('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        skipWhitespace();

        List<Object> result = new ArrayList<>();
        if (tryConsume(']')) {
            return result;
        }

        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();

            if (tryConsume(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();

        while (!isAtEnd()) {
            char current = text.charAt(index++);
            if (current == '"') {
                return builder.toString();
            }
            if (current == '\\') {
                builder.append(parseEscapeSequence());
            } else {
                builder.append(current);
            }
        }

        throw error("Unterminated string");
    }

    private String parseEscapeSequence() {
        if (isAtEnd()) {
            throw error("Invalid escape sequence");
        }

        char escaped = text.charAt(index++);
        return switch (escaped) {
            case '"', '\\', '/' -> String.valueOf(escaped);
            case 'b' -> "\b";
            case 'f' -> "\f";
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case 'u' -> parseUnicodeEscape();
            default -> throw error("Unsupported escape sequence: \\" + escaped);
        };
    }

    private String parseUnicodeEscape() {
        if (index + 4 > text.length()) {
            throw error("Incomplete unicode escape");
        }

        String hex = text.substring(index, index + 4);
        index += 4;
        try {
            return String.valueOf((char) Integer.parseInt(hex, 16));
        } catch (NumberFormatException ex) {
            throw error("Invalid unicode escape: \\u" + hex);
        }
    }

    private Number parseNumber() {
        int start = index;

        if (currentChar() == '-') {
            index++;
        }

        consumeDigits();

        boolean isFractional = false;
        if (!isAtEnd() && currentChar() == '.') {
            isFractional = true;
            index++;
            consumeDigits();
        }

        if (!isAtEnd()) {
            char current = currentChar();
            if (current == 'e' || current == 'E') {
                isFractional = true;
                index++;
                if (!isAtEnd() && (currentChar() == '+' || currentChar() == '-')) {
                    index++;
                }
                consumeDigits();
            }
        }

        String value = text.substring(start, index);
        try {
            return isFractional ? Double.parseDouble(value) : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw error("Invalid number: " + value);
        }
    }

    private void consumeDigits() {
        int start = index;
        while (!isAtEnd() && Character.isDigit(currentChar())) {
            index++;
        }
        if (start == index) {
            throw error("Expected digit");
        }
    }

    private void consumeLiteral(String literal) {
        if (!text.startsWith(literal, index)) {
            throw error("Expected literal: " + literal);
        }
        index += literal.length();
    }

    private void expect(char expected) {
        if (isAtEnd() || text.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean tryConsume(char expected) {
        if (!isAtEnd() && text.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private char currentChar() {
        return text.charAt(index);
    }

    private boolean isAtEnd() {
        return index >= text.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + index);
    }
}
