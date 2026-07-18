package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Конвертер между ObjectArray/ObjectElement и JSON без внешних библиотек.
 * Поддерживает стандартный JSON (массивы, объекты, строки, числа, булевы, null)
 * и автоматическое распознавание Base64-строк как byte[].
 */
public class ObjectArrayJsonConverter {
    private static final Pattern base64 = Pattern.compile("^([A-Za-z0-9+\\/]{4})*([A-Za-z0-9+\\/]{4}|[A-Za-z0-9+\\/]{3}=|[A-Za-z0-9+\\/]{2}==)$");

    // ==================== Прямое преобразование ====================
    public static String to(ObjectArray objectArray, boolean silent) {
        String result = "[]";
        try {
            result = to(objectArray);
        } catch (Exception e) {
            if (!silent)
                throw e;
        }
        return result;
    }

    private static String to(ObjectArray objectArray) {
        if (objectArray == null || objectArray.size() == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < objectArray.size(); i++) {
            Object v = objectArray.get(i);
            if (i > 0)
                sb.append(',');
            if (objectArray.getType() == ObjectType.BYTES) {
                sb.append('"');
                sb.append(Base64.getEncoder().encodeToString((byte[]) v));
                sb.append('"');
            } else if (objectArray.getType() == ObjectType.STRING) {
                sb.append('"');
                sb.append(escapeJsonString(v.toString()));
                sb.append('"');
            } else if (objectArray.getType() == ObjectType.OBJECT_ARRAY) {
                sb.append(to((ObjectArray) v));
            } else if (objectArray.getType() == ObjectType.OBJECT_ELEMENT) {
                sb.append(to((ObjectElement) v));
            } else {
                sb.append(v.toString());
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static String to(ObjectElement objectElement) {
        if (objectElement == null || objectElement.getFields().size() == 0)
            return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < objectElement.getFields().size(); i++) {
            ObjectField f = objectElement.getFields().get(i);
            if (i > 0)
                sb.append(',');
            sb.append('"');
            sb.append(f.getName());
            sb.append('"');
            sb.append(':');
            Object v = f.getValue();
            if (f.getType() == ObjectType.BYTES) {
                sb.append('"');
                sb.append(Base64.getEncoder().encodeToString((byte[]) v));
                sb.append('"');
            } else if (f.getType() == ObjectType.STRING) {
                sb.append('"');
                sb.append(escapeJsonString(v.toString()));
                sb.append('"');
            } else if (f.getType() == ObjectType.OBJECT_ARRAY) {
                sb.append(to((ObjectArray) v));
            } else if (f.getType() == ObjectType.OBJECT_ELEMENT) {
                sb.append(to((ObjectElement) v));
            } else {
                sb.append(v.toString());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Экранирует строку для JSON (кавычки, обратная косая черта, управляющие символы).
     */
    private static String escapeJsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    // ==================== Обратное преобразование (JSON → ObjectArray/ObjectElement) ====================
    public static ObjectArray from(String json, boolean silent) throws IllegalArgumentException {
        ObjectArray result = new ObjectArray();
        if (json == null || json.trim().isEmpty())
            return result;
        try {
            JsonParser parser = new JsonParser(json);
            Map.Entry<ObjectType, Object> entry = parser.parseValue();
            if (entry != null && entry.getValue() != null) {
                if (entry.getKey() == ObjectType.OBJECT_ARRAY) {
                    result = (ObjectArray) entry.getValue();
                } else if (entry.getKey() == ObjectType.OBJECT_ELEMENT) {
                    result = new ObjectArray(List.of(entry.getValue()), ObjectType.OBJECT_ELEMENT);
                } else {
                    result = new ObjectArray(List.of(entry.getValue()), ObjectType.VALUE_ANY);
                }
            } else {
                throw new IllegalArgumentException("Wrong JSON format");
            }
        } catch (Exception e) {
            if (!silent)
                throw e;
        }
        return result;
    }

    // ==================== Внутренний парсер JSON ====================
    private static class JsonParser {
        private final String json;
        private int pos;
        private final int len;

        JsonParser(String json) {
            this.json = json;
            this.pos = 0;
            this.len = json.length();
        }

        /**
         * Пропускает пробелы, табуляции, переводы строк.
         */
        private void skipWhitespace() {
            while (pos < len && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        /**
         * Проверяет, что текущий символ равен ожидаемому, и продвигает позицию.
         */
        private void expect(char expected) {
            skipWhitespace();
            if (pos >= len || json.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }

        /**
         * Основной метод разбора значения.
         * Возвращает: String, Number (Integer/Long/Double), Boolean, null,
         * ObjectArray, ObjectElement или byte[] (если строка распознана как Base64).
         */
        private Map.Entry<ObjectType, Object> parseValue() {
            skipWhitespace();
            if (pos >= len) throw new IllegalArgumentException("Unexpected end of input");
            char c = json.charAt(pos);
            switch (c) {
                case '[':
                    return Map.entry(ObjectType.OBJECT_ARRAY, parseArray());
                case '{':
                    return Map.entry(ObjectType.OBJECT_ELEMENT, parseObject());
                case '"':
                    return parseString(true);
                case 't':
                    expect("true");
                    return Map.entry(ObjectType.BOOLEAN, Boolean.TRUE);
                case 'f':
                    expect("false");
                    return Map.entry(ObjectType.BOOLEAN, Boolean.FALSE);
                case 'n':
                    expect("null");
                    return new AbstractMap.SimpleEntry<>(ObjectType.STRING, null);
                default:
                    if (c == '-' || Character.isDigit(c))
                        return parseNumber();
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
            }
        }

        /**
         * Разбирает JSON-массив -> ObjectArray
         */
        private ObjectArray parseArray() {
            expect('[');
            // Определим временный список для анализа типов
            List<Object> elements = new ArrayList<>();
            while (true) {
                skipWhitespace();
                if (pos < len && json.charAt(pos) == ']') {
                    pos++;
                    break;
                }
                if (!elements.isEmpty())
                    expect(',');
                Map.Entry<ObjectType, Object> entry = parseValue();
                elements.add(entry.getValue());
            }
            // Определяем единый тип для всего массива и заполняем ObjectArray
            ObjectType unifiedType = determineUnifiedType(elements);
            return new ObjectArray(elements, unifiedType);
        }

        /**
         * Разбирает JSON-объект -> ObjectElement
         */
        private ObjectElement parseObject() {
            expect('{');
            ObjectElement element = new ObjectElement();
            while (true) {
                skipWhitespace();
                if (pos < len && json.charAt(pos) == '}') {
                    pos++;
                    break;
                }
                if (element.getFields().size() > 0)
                    expect(',');
                Map.Entry<ObjectType, Object> entry = parseString(false);// имя поля должно быть строкой
                String name = entry.getValue().toString();
                expect(':');
                entry = parseValue();
                element.getFields().add(new ObjectField(name, entry.getKey(), entry.getValue()));
            }
            return element;
        }

        /**
         * Разбирает JSON-строку с учётом escape-последовательностей
         */
        private Map.Entry<ObjectType, Object> parseString(boolean checkBytes) {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = json.charAt(pos);
                if (c == '"') {
                    pos++;
                    break;
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= len) throw new IllegalArgumentException("Unterminated escape sequence");
                    char esc = json.charAt(pos);
                    pos++;
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > len) throw new IllegalArgumentException("Unterminated Unicode escape");
                            String hex = json.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                int code = Integer.parseInt(hex, 16);
                                sb.append((char) code);
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Invalid Unicode escape: \\u" + hex);
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape sequence: \\" + esc);
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            String str = sb.toString();
            // Проверяем, является ли строка Base64
            if (checkBytes && isBase64(str)) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(str);
                    return Map.entry(ObjectType.BYTES, bytes);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return Map.entry(ObjectType.STRING, str);
        }

        /**
         * Разбирает число (целое или дробное)
         */
        private Map.Entry<ObjectType, Object> parseNumber() {
            int start = pos;
            // знак
            if (pos < len && json.charAt(pos) == '-')
                pos++;
            // целая часть
            if (pos < len && json.charAt(pos) == '0') {
                pos++;
            } else {
                while (pos < len && Character.isDigit(json.charAt(pos)))
                    pos++;
            }
            // дробная часть
            if (pos < len && json.charAt(pos) == '.') {
                pos++;
                while (pos < len && Character.isDigit(json.charAt(pos)))
                    pos++;
            }
            // экспонента
            if (pos < len && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                pos++;
                if (pos < len && (json.charAt(pos) == '+' || json.charAt(pos) == '-'))
                    pos++;
                while (pos < len && Character.isDigit(json.charAt(pos)))
                    pos++;
            }
            String numStr = json.substring(start, pos);
            try {
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    return Map.entry(ObjectType.DOUBLE, Double.parseDouble(numStr));
                } else {
                    long longVal = Long.parseLong(numStr);
                    if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                        return Map.entry(ObjectType.INTEGER, (int) longVal);
                    return Map.entry(ObjectType.LONG, longVal);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number format: " + numStr);
            }
        }

        /**
         * Проверяет, что строка является корректным Base64 (длина кратна 4, только допустимые символы)
         */
        private static boolean isBase64(String s) {
            if (s == null || s.isEmpty())
                return false;
            // Допустимые символы: A-Z a-z 0-9 + / = (максимум два = в конце)
            // return s.length() % 4 == 0 && s.matches("^[A-Za-z0-9+/]*={0,2}$");
            return s.length() >= 2 && !s.isBlank() && s.length() % 4 == 0 && (s.endsWith("=") || s.length() > 30) && base64.matcher(s).find();
        }

        /**
         * Определяет единый тип для всех элементов массива
         */
        private ObjectType determineUnifiedType(List<Object> elements) {
            if (elements.isEmpty())
                return ObjectType.OBJECT_ELEMENT;
            ObjectType firstType = null;
            boolean allSame = true;
            for (Object elem : elements) {
                ObjectType t = ModuleUtils.getObjectType(elem);
                if (t == null)
                    t = ObjectType.STRING;
                if (firstType == null) {
                    firstType = t;
                } else if (t != firstType) {
                    allSame = false;
                }
            }
            if (allSame)
                return firstType;
            return ObjectType.VALUE_ANY;
        }

        /**
         * Проверяет, что следующие символы равны заданной строке (для true/false/null)
         */
        private void expect(String literal) {
            skipWhitespace();
            if (!json.startsWith(literal, pos))
                throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
            pos += literal.length();
        }
    }

}
