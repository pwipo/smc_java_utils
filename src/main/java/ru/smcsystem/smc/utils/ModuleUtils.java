package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.*;
import ru.smcsystem.api.enumeration.*;
import ru.smcsystem.api.tools.ConfigurationTool;
import ru.smcsystem.api.tools.execution.ExecutionContextTool;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleUtils {
    public static Number getNumber(IMessage m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(IMessage m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(IMessage m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static String toString(IMessage m) {
        if (m == null)
            return null;
        String result;
        switch (m.getType()) {
            case STRING:
                result = (String) m.getValue();
                break;
            case BYTES:
                result = Base64.getEncoder().encodeToString((byte[]) m.getValue());
                break;
            default:
                result = m.getValue().toString();
                break;
        }
        return result;
    }

    public static ObjectArray getObjectArray(IMessage m) {
        return isObjectArray(m) ? (ObjectArray) m.getValue() : null;
    }


    /**
     * deserialize messages to object array
     * if first message type ObjectArray, when return it
     * use object serialization format
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 14-OBJECT_ELEMENT_OPTIMIZED, 15-OBJECT_ELEMENT_SIMPLE_OPTIMIZED
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                      number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                  list of values for each element. format:
     *                      field value. depend of type. format:
     *                          if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                          if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                          if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                          else: any type - simple value.
     *              else:
     *                  number - number of fields in object.
     *                  for each element:
     *                      for each field. format:
     *                          string - field name.
     *                          number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                          field value. depend of type. format:
     *                              if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                              if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                              if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                              else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                  list of values for each element. format:
     *                      any type - simple value.
     *              else:
     *                  number - number of fields in each object (one for all).
     *                  list of each object fields. format:
     *                      string - field name.
     *                      any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     *
     * @param messages - list of messages for deserialization
     * @return ObjectArray
     */
    public static ObjectArray deserializeToObject(LinkedList<IMessage> messages) {
        IMessage m = messages.peek();
        if (isObjectArray(m))
            return getObjectArray(messages.poll());
        ObjectArray objectArray = new ObjectArray();
        if (messages.size() < 2)
            return objectArray;
        Number typeId = getNumber(m);
        if (typeId == null)
            return objectArray;
        int typeIdI = typeId.intValue();
        if (typeIdI < 0 || ObjectTypePrivate.values().length <= typeIdI)
            return objectArray;
        ObjectTypePrivate typePrivate = ObjectTypePrivate.values()[typeIdI];
        messages.poll();

        Number size = getNumber(messages.peek());
        if (size == null) {
            messages.addFirst(m);
            return objectArray;
        }
        messages.poll();
        int count = size.intValue();

        objectArray = new ObjectArray(count, typePrivate.convert());
        try {
            switch (typePrivate) {
                case OBJECT_ARRAY:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObject(messages));
                    break;
                case OBJECT_ELEMENT:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, null));
                    break;
                case OBJECT_ELEMENT_SIMPLE: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields != null) {
                        messages.poll();
                        for (int i = 0; i < count; i++)
                            objectArray.add(deserializeToObjectElement(messages, countFields.intValue(), null));
                    }
                    break;
                }
                case VALUE_ANY:
                case STRING:
                case BYTE:
                case SHORT:
                case INTEGER:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BIG_INTEGER:
                case BIG_DECIMAL:
                case BYTES:
                    for (int i = 0; i < count; i++)
                        objectArray.add(messages.poll());
                    break;
                case OBJECT_ELEMENT_OPTIMIZED: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields == null)
                        break;
                    int countFieldsI = countFields.intValue();
                    if (messages.size() <= countFieldsI * 2 + 1)
                        break;
                    messages.poll();
                    List<Map.Entry<String, ObjectTypePrivate>> definedFields = new ArrayList<>(countFieldsI + 1);
                    boolean hasErrors = false;
                    for (int i = 0; i < countFieldsI; i++) {
                        String fieldName = toString(messages.poll());
                        Number fieldType = getNumber(messages.peek());
                        if (fieldType == null) {
                            hasErrors = true;
                            break;
                        }
                        int fieldTypeI = fieldType.intValue();
                        if (fieldTypeI < 0 || ObjectTypePrivate.values().length <= fieldTypeI) {
                            hasErrors = true;
                            break;
                        }
                        messages.poll();
                        definedFields.add(Map.entry(fieldName, ObjectTypePrivate.values()[fieldTypeI]));
                    }
                    if (hasErrors)
                        break;
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, definedFields));
                    break;
                }
                case OBJECT_ELEMENT_SIMPLE_OPTIMIZED: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields == null)
                        break;
                    int countFieldsI = countFields.intValue();
                    if (messages.size() <= countFieldsI + 1)
                        break;
                    messages.poll();
                    List<Map.Entry<String, ObjectTypePrivate>> definedFields = new ArrayList<>(countFieldsI + 1);
                    for (int i = 0; i < countFieldsI; i++)
                        definedFields.add(Map.entry(toString(messages.poll()), ObjectTypePrivate.VALUE_ANY));
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, definedFields));
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectArray;
    }

    private static ObjectElement deserializeToObjectElement(LinkedList<IMessage> messages, int countFields, List<Map.Entry<String, ObjectTypePrivate>> definedFields) {
        IMessage m = messages.peek();
        if (isObjectArray(m)) {
            ObjectArray objectArray = getObjectArray(messages.poll());
            if (isArrayContainObjectElements(objectArray))
                return (ObjectElement) objectArray.get(0);
            return new ObjectElement();
        }
        ObjectElement objectElement = new ObjectElement();

        int count = -1;
        if (countFields < 0) {
            if (definedFields == null) {
                Number size = getNumber(messages.peek());
                if (size == null)
                    return objectElement;
                messages.poll();
                count = size.intValue();
            } else {
                count = definedFields.size();
            }
        } else {
            count = countFields;
        }

        try {
            if (definedFields != null) {
                definedFields.forEach(entry -> deserializeToObjectElementValue(messages, objectElement, entry.getKey(), entry.getValue()));
            } else {
                for (int i = 0; i < count; i++) {
                    if ((countFields > -1 && messages.size() < 2) || (countFields < 0 && messages.size() < 3))
                        break;
                    String fieldName = toString(messages.poll());
                    ObjectTypePrivate type = ObjectTypePrivate.VALUE_ANY;
                    if (countFields < 0) {
                        Number typeId = getNumber(messages.peek());
                        if (typeId == null)
                            break;
                        int typeIdI = typeId.intValue();
                        if (typeIdI < 0 || ObjectTypePrivate.values().length <= typeIdI)
                            break;
                        messages.poll();
                        type = ObjectTypePrivate.values()[typeId.intValue()];
                    }
                    deserializeToObjectElementValue(messages, objectElement, fieldName, type);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return objectElement;
    }

    private static void deserializeToObjectElementValue(LinkedList<IMessage> messages, ObjectElement objectElement, String fieldName, ObjectTypePrivate type) {
        switch (type) {
            case OBJECT_ARRAY:
                objectElement.getFields().add(new ObjectField(fieldName, deserializeToObject(messages)));
                break;
            case OBJECT_ELEMENT:
                objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, -1, null)));
                break;
            case OBJECT_ELEMENT_SIMPLE: {
                Number countFields2 = getNumber(messages.peek());
                if (countFields2 != null) {
                    messages.poll();
                    objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, countFields2.intValue(), null)));
                }
                break;
            }
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case BYTES:
                objectElement.getFields().add(new ObjectField(fieldName, messages.poll()));
                break;
        }
    }

    /**
     * serialize objects to messages
     * use object serialization format
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 14-OBJECT_ELEMENT_OPTIMIZED, 15-OBJECT_ELEMENT_SIMPLE_OPTIMIZED
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                      number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                  list of values for each element. format:
     *                      field value. depend of type. format:
     *                          if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                          if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                          if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                          else: any type - simple value.
     *              else:
     *                  number - number of fields in object.
     *                  for each element:
     *                      for each field. format:
     *                          string - field name.
     *                          number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                          field value. depend of type. format:
     *                              if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                              if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                              if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                              else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                  list of values for each element. format:
     *                      any type - simple value.
     *              else:
     *                  number - number of fields in each object (one for all).
     *                  list of each object fields. format:
     *                      string - field name.
     *                      any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     *
     * @param mainList - list of objects for serialization
     * @return List of values, ready for send as messages
     */
    public static List<Object> serializeFromObject(ObjectArray mainList) {
        List<Object> result = new LinkedList<>();
        if (mainList == null)
            return result;
        ObjectTypePrivate typePrivate = ObjectTypePrivate.valueOf(mainList.getType().name());
        result.add(typePrivate.ordinal());
        result.add(mainList.size());
        if (mainList.size() == 0)
            return result;
        if (typePrivate == ObjectTypePrivate.OBJECT_ELEMENT && mainList.size() > 1) {
            boolean isSimple = true;
            List<String> fieldNames = null;
            for (int i = 0; i < mainList.size(); i++) {
                ObjectElement objectElement = (ObjectElement) mainList.get(i);
                if (i == 0) {
                    fieldNames = objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList());
                    if (!objectElement.isSimple()) {
                        isSimple = false;
                        break;
                    }
                } else if (!objectElement.isSimple() || !objectElement.getFields().stream()
                        .map(ObjectField::getName).collect(Collectors.toList()).equals(fieldNames)) {
                    isSimple = false;
                    break;
                }
            }
            if (isSimple)
                typePrivate = ObjectTypePrivate.OBJECT_ELEMENT_SIMPLE;
        }
        switch (typePrivate) {
            case OBJECT_ARRAY:
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectArray) mainList.get(i)));
                break;
            case OBJECT_ELEMENT: {
                List<String> definedFields = null;
                if (mainList.size() > 1 && isSameFields(mainList)) {
                    List<Map.Entry<String, ObjectType>> definedFieldsTmp = new ArrayList<>(mainList.size() + 1);
                    ObjectElement objectElement = (ObjectElement) mainList.get(0);
                    objectElement.getFields().forEach(f -> definedFieldsTmp.add(Map.entry(f.getName(), f.getType())));
                    result.clear();
                    result.add(ObjectTypePrivate.OBJECT_ELEMENT_OPTIMIZED.ordinal());
                    result.add(mainList.size());
                    result.add(definedFieldsTmp.size());
                    definedFieldsTmp.forEach(f -> {
                        result.add(f.getKey());
                        result.add(ObjectTypePrivate.valueOf(f.getValue().name()).ordinal());
                    });
                    definedFields = definedFieldsTmp.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                }
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), false, definedFields));
                break;
            }
            case OBJECT_ELEMENT_SIMPLE:
                List<String> definedFields = null;
                if (mainList.size() > 1 && isSameFields(mainList)) {
                    List<String> definedFieldsTmp = new ArrayList<>(mainList.size() + 1);
                    ObjectElement objectElement = (ObjectElement) mainList.get(0);
                    objectElement.getFields().forEach(f -> definedFieldsTmp.add(f.getName()));
                    result.clear();
                    result.add(ObjectTypePrivate.OBJECT_ELEMENT_SIMPLE_OPTIMIZED.ordinal());
                    result.add(mainList.size());
                    result.add(definedFieldsTmp.size());
                    result.addAll(definedFieldsTmp);
                    definedFields = definedFieldsTmp;
                } else {
                    result.add(mainList.size() > 0 ? ((ObjectElement) mainList.get(0)).getFields().size() : 0);
                }
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), true, definedFields));
                break;
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES: {
                for (int i = 0; i < mainList.size(); i++)
                    result.add(mainList.get(i));
                break;
            }
        }
        return result;
    }

    private static List<Object> serializeFromObject(ObjectElement objectElement, boolean isSimple, List<String> definedFields) {
        List<Object> result = new LinkedList<>();
        if (objectElement == null)
            return result;
        if (definedFields != null) {
            // because its check
            // definedFields.stream()
            //         .map(objectElement::findField)
            //         .filter(Optional::isPresent)
            //         .map(Optional::get)
            objectElement.getFields().forEach(f -> result.addAll(serializeFromObjectFieldValue(f.getType(), f.getValue())));
        } else {
            if (!isSimple)
                result.add(objectElement.getFields().size());
            objectElement.getFields().forEach(objField -> {
                result.add(objField.getName());
                if (!isSimple)
                    result.add(ObjectTypePrivate.valueOf(objField.getType().name()).ordinal());
                result.addAll(serializeFromObjectFieldValue(objField.getType(), objField.getValue()));
            });
        }
        return result;
    }

    private static List<Object> serializeFromObjectFieldValue(ObjectType type, Object value) {
        switch (type) {
            case OBJECT_ARRAY:
                return serializeFromObject((ObjectArray) value);
            case OBJECT_ELEMENT:
                if (((ObjectElement) value).isSimple()) {
                    List<Object> result = new LinkedList<>();
                    result.add(((ObjectElement) value).getFields().size());
                    result.addAll(serializeFromObject((ObjectElement) value, true, null));
                    return result;

                } else {
                    return serializeFromObject((ObjectElement) value, false, null);
                }
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
                return List.of(value);
        }
        throw new IllegalArgumentException(type.name());
    }

    public static boolean isNumber(IMessage m) {
        return m != null && ((ValueType.BYTE.equals(m.getType()) || ValueType.SHORT.equals(m.getType()) || ValueType.INTEGER.equals(m.getType()) || ValueType.LONG.equals(m.getType()) || ValueType.FLOAT.equals(m.getType()) || ValueType.DOUBLE.equals(m.getType()) || ValueType.BIG_INTEGER.equals(m.getType()) || ValueType.BIG_DECIMAL.equals(m.getType())));
    }

    public static boolean isString(IMessage m) {
        return m != null && ValueType.STRING.equals(m.getType());
    }

    public static boolean isBytes(IMessage m) {
        return m != null && ValueType.BYTES.equals(m.getType());
    }

    public static boolean isObjectArray(IMessage m) {
        return m != null && ValueType.OBJECT_ARRAY.equals(m.getType());
    }

    public static boolean hasErrors(ICommand c) {
        if (c == null)
            return false;
        return !c.getActions().isEmpty() && c.getActions().stream().anyMatch(ModuleUtils::hasErrors);
    }

    public static boolean hasData(ICommand c) {
        if (c == null)
            return false;
        return !c.getActions().isEmpty() && c.getActions().stream().anyMatch(ModuleUtils::hasData);
    }

    public static boolean hasErrors(IAction a) {
        if (a == null)
            return false;
        return !a.getMessages().isEmpty()
                // && ActionType.EXECUTE.equals(a.getType())
                // && a.getMessages().stream().anyMatch(m -> MessageType.DATA.equals(m.getMessageType()))
                && a.getMessages().stream().anyMatch(m -> MessageType.ERROR.equals(m.getMessageType()) || MessageType.ACTION_ERROR.equals(m.getMessageType()));
    }

    public static boolean hasData(IAction a) {
        if (a == null)
            return false;
        return !a.getMessages().isEmpty()
                && ActionType.EXECUTE.equals(a.getType())
                && a.getMessages().stream().anyMatch(m -> MessageType.DATA.equals(m.getMessageType()));
    }

    public static boolean isArrayContainObjectElements(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && ObjectType.OBJECT_ELEMENT == objectArray.getType();
    }

    public static boolean isArrayContainArrays(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && ObjectType.OBJECT_ARRAY == objectArray.getType();
    }

    public static boolean isNumber(ObjectField m) {
        return m != null && ((ObjectType.BYTE.equals(m.getType()) || ObjectType.SHORT.equals(m.getType()) || ObjectType.INTEGER.equals(m.getType()) || ObjectType.LONG.equals(m.getType()) || ObjectType.FLOAT.equals(m.getType()) || ObjectType.DOUBLE.equals(m.getType()) || ObjectType.BIG_INTEGER.equals(m.getType()) || ObjectType.BIG_DECIMAL.equals(m.getType())));
    }

    public static boolean isString(ObjectField m) {
        return m != null && ObjectType.STRING.equals(m.getType());
    }

    public static boolean isBytes(ObjectField m) {
        return m != null && ObjectType.BYTES.equals(m.getType());
    }

    public static boolean isObjectArray(ObjectField m) {
        return m != null && ObjectType.OBJECT_ARRAY.equals(m.getType());
    }

    public static boolean isObjectElement(ObjectField m) {
        return m != null && ObjectType.OBJECT_ELEMENT.equals(m.getType());
    }

    public static Number getNumber(ObjectField m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(ObjectField m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(ObjectField m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static ObjectArray getObjectArray(ObjectField m) {
        return isObjectArray(m) ? (ObjectArray) m.getValue() : null;
    }

    public static String toString(ObjectField m) {
        if (m == null)
            return null;
        String result;
        switch (m.getType()) {
            case STRING:
                result = (String) m.getValue();
                break;
            case BYTES:
                result = Base64.getEncoder().encodeToString((byte[]) m.getValue());
                break;
            default:
                result = m.getValue().toString();
                break;
        }
        return result;
    }

    public static ObjectElement getObjectElement(ObjectField m) {
        if (m == null)
            return null;
        if (m.getType() == ObjectType.OBJECT_ARRAY) {
            ObjectArray objectArray = (ObjectArray) m.getValue();
            if (objectArray.size() > 0 && objectArray.getType() == ObjectType.OBJECT_ELEMENT)
                return (ObjectElement) objectArray.get(0);
        } else if (m.getType() == ObjectType.OBJECT_ELEMENT) {
            return (ObjectElement) m.getValue();
        }
        return null;
    }

    public static List<ObjectElement> getObjectElements(ObjectField m) {
        if (m == null)
            return null;
        if (m.getType() == ObjectType.OBJECT_ARRAY) {
            ObjectArray objectArray = (ObjectArray) m.getValue();
            if (objectArray.getType() == ObjectType.OBJECT_ELEMENT) {
                List<ObjectElement> result = new ArrayList<>(objectArray.size() + 1);
                for (int i = 0; i < objectArray.size(); i++)
                    result.add((ObjectElement) objectArray.get(i));
                return result;
            }
        } else if (m.getType() == ObjectType.OBJECT_ELEMENT) {
            return new ArrayList<>(List.of((ObjectElement) m.getValue()));
        }
        return null;
    }

    public static boolean isSameFields(ObjectArray objectArray) {
        if (objectArray == null || objectArray.getType() != ObjectType.OBJECT_ELEMENT)
            return false;
        boolean isSame = true;
        List<String> fieldNames = null;
        List<ObjectType> fieldType = null;
        for (int i = 0; i < objectArray.size(); i++) {
            ObjectElement objectElement = (ObjectElement) objectArray.get(i);
            if (i == 0) {
                fieldNames = objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList());
                fieldType = objectElement.getFields().stream().map(ObjectField::getType).collect(Collectors.toList());
            } else if (!objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList()).equals(fieldNames) ||
                    !objectElement.getFields().stream().map(ObjectField::getType).collect(Collectors.toList()).equals(fieldType)) {
                isSame = false;
                break;
            }
        }
        return isSame;
    }

    public static List<List<ObjectField>> findFields(ObjectArray objectArray, List<String> fieldPaths) {
        if (objectArray == null || objectArray.getType() != ObjectType.OBJECT_ELEMENT || fieldPaths == null || fieldPaths.isEmpty())
            return List.of();
        return fieldPaths.stream()
                .map(p -> {
                    String[] names = splitFieldNames(p);
                    List<ObjectElement> objectElements = new ArrayList<>(objectArray.size());
                    for (int i = 0; i < objectArray.size(); i++)
                        objectElements.add((ObjectElement) objectArray.get(i));
                    return findFields(objectElements, names, 0);
                })
                .collect(Collectors.toList());
    }

    public static List<ObjectField> findFields(List<ObjectElement> objectElements, String[] names, int level) {
        if (names.length <= level)
            return List.of();
        String name = names[level];
        List<ObjectField> fields = new LinkedList<>();
        for (ObjectElement objectElement : objectElements) {
            Optional<ObjectField> field = objectElement.findField(name);
            if (field.isPresent()) {
                ObjectField objectField = field.get();
                if (names.length > level + 1) {
                    List<ObjectElement> innerObjectElements = getObjectElements(objectField);
                    if (innerObjectElements != null)
                        fields.addAll(findFields(innerObjectElements, names, level + 1));
                } else {
                    fields.add(objectField);
                }
            }
        }
        return new ArrayList<>(fields);
    }

    public static String[] splitFieldNames(String fieldPath) {
        return fieldPath.split("\\.");
    }

    public static boolean isArrayContainNumber(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.BYTE.equals(objectArray.getType()) || ObjectType.SHORT.equals(objectArray.getType()) || ObjectType.INTEGER.equals(objectArray.getType()) || ObjectType.LONG.equals(objectArray.getType()) || ObjectType.FLOAT.equals(objectArray.getType()) || ObjectType.DOUBLE.equals(objectArray.getType()) || ObjectType.BIG_INTEGER.equals(objectArray.getType()) || ObjectType.BIG_DECIMAL.equals(objectArray.getType()));
    }

    public static boolean isArrayContainString(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.STRING.equals(objectArray.getType()));
    }

    public static boolean isArrayContainBytes(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.BYTES.equals(objectArray.getType()));
    }

    public static ValueType toValueType(ObjectField m) {
        ValueType result = null;
        if (m == null || !m.isSimple())
            return result;
        if (m.getType() == ObjectType.VALUE_ANY) {
            result = getValueTypeObject(m.getValue());
        } else {
            result = ValueType.valueOf(m.getType().name());
        }
        return result;
    }

    public static ValueType getValueTypeObject(Object value) {
        if (value instanceof Byte) {
            return ValueType.BYTE;
        } else if (value instanceof Short) {
            return ValueType.SHORT;
        } else if (value instanceof Integer) {
            return ValueType.INTEGER;
        } else if (value instanceof Long) {
            return ValueType.LONG;
        } else if (value instanceof Float) {
            return ValueType.FLOAT;
        } else if (value instanceof Double) {
            return ValueType.DOUBLE;
        } else if (value instanceof BigInteger) {
            return ValueType.BIG_INTEGER;
        } else if (value instanceof BigDecimal) {
            return ValueType.BIG_DECIMAL;
        } else if (value instanceof String) {
            return ValueType.STRING;
        } else if (value instanceof byte[]) {
            return ValueType.BYTES;
        } else if (value instanceof ObjectArray) {
            return ValueType.OBJECT_ARRAY;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static ObjectType getObjectType(Object value) {
        if (value instanceof ObjectElement) {
            return ObjectType.OBJECT_ELEMENT;
        } else {
            return convertTo(getValueTypeObject(value));
        }
    }

    public static ObjectType convertTo(ValueType type) {
        switch (type) {
            case STRING:
                return ObjectType.STRING;
            case BYTE:
                return ObjectType.BYTE;
            case SHORT:
                return ObjectType.SHORT;
            case INTEGER:
                return ObjectType.INTEGER;
            case LONG:
                return ObjectType.LONG;
            case BIG_INTEGER:
                return ObjectType.BIG_INTEGER;
            case FLOAT:
                return ObjectType.FLOAT;
            case DOUBLE:
                return ObjectType.DOUBLE;
            case BIG_DECIMAL:
                return ObjectType.BIG_DECIMAL;
            case BYTES:
                return ObjectType.BYTES;
            case OBJECT_ARRAY:
                return ObjectType.OBJECT_ARRAY;
        }
        return null;
    }

    public static ValueType convertTo(ObjectType type) {
        switch (type) {
            case STRING:
                return ValueType.STRING;
            case BYTE:
                return ValueType.BYTE;
            case SHORT:
                return ValueType.SHORT;
            case INTEGER:
                return ValueType.INTEGER;
            case LONG:
                return ValueType.LONG;
            case BIG_INTEGER:
                return ValueType.BIG_INTEGER;
            case FLOAT:
                return ValueType.FLOAT;
            case DOUBLE:
                return ValueType.DOUBLE;
            case BIG_DECIMAL:
                return ValueType.BIG_DECIMAL;
            case BYTES:
                return ValueType.BYTES;
            case OBJECT_ARRAY:
                return ValueType.OBJECT_ARRAY;
        }
        return null;
    }

    public static long executeParallel(ExecutionContextTool executionContextTool, int ecId, List<Object> params, long maxWorkIntervalMs, int sleepTimeMs) {
        long threadId = executionContextTool.getFlowControlTool().executeParallel(CommandType.EXECUTE, List.of(ecId), params, 0, (int) (maxWorkIntervalMs / 1000));
        waitThread(executionContextTool, threadId, sleepTimeMs);
        return threadId;
    }

    public static void waitThread(ExecutionContextTool executionContextTool, long threadId, int sleepTime) {
        do {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
        } while (!executionContextTool.isNeedStop() && executionContextTool.getFlowControlTool().isThreadActive(threadId));
    }

    public static void waitThreadAndRelease(ExecutionContextTool executionContextTool, long threadId, int sleepTime) {
        waitThread(executionContextTool, threadId, sleepTime);
        executionContextTool.getFlowControlTool().releaseThread(threadId);
    }

    public static Optional<IAction> getFirstActionWithData(List<IAction> actions) {
        return actions.stream()
                .filter(ModuleUtils::hasData)
                .findFirst();
    }

    public static Optional<IAction> getFirstActionWithDataFromCommands(List<ICommand> commands) {
        return getFirstActionWithData(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<IAction> getLastActionWithData(List<IAction> actions) {
        return actions.stream()
                .filter(ModuleUtils::hasData)
                .reduce((first, second) -> second);
    }

    public static Optional<IAction> getLastActionWithDataFromCommands(List<ICommand> commands) {
        return getLastActionWithData(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<ObjectArray> getElements(List<IAction> actions) {
        return getFirstActionWithData(actions)
                .map(a -> ModuleUtils.deserializeToObject(new LinkedList<>(a.getMessages())))
                .filter(ModuleUtils::isArrayContainObjectElements);
    }

    public static Optional<ObjectElement> getElement(List<IAction> actions) {
        return getElements(actions).map(a -> (ObjectElement) a.get(0));
    }

    public static Optional<ObjectElement> executeAndGetElement(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        executionContextTool.getFlowControlTool().executeNow(CommandType.EXECUTE, id, params);
        return getElement(executionContextTool.getFlowControlTool().getMessagesFromExecuted(id));
    }

    public static Optional<ObjectArray> executeAndGetArrayElements(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        executionContextTool.getFlowControlTool().executeNow(CommandType.EXECUTE, id, params);
        return getElements(executionContextTool.getFlowControlTool().getMessagesFromExecuted(id));
    }

    public static Optional<List<IMessage>> executeAndGetMessages(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        executionContextTool.getFlowControlTool().executeNow(CommandType.EXECUTE, id, params);
        return executionContextTool.getFlowControlTool().getMessagesFromExecuted(id).stream()
                .filter(ModuleUtils::hasData)
                .map(IAction::getMessages)
                .findFirst();
    }

    public static String getStackTraceAsString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static String getErrorMessageOrClassName(Throwable t) {
        return t != null && t.getMessage() != null && !t.getMessage().isEmpty() ? t.getMessage() : (t != null ? t.getClass().getName() : "");
    }

    public static List<List<List<IMessage>>> getMessages(ExecutionContextTool executionContextTool) {
        return Stream.iterate(0, n -> n + 1)
                .limit(executionContextTool.countSource())
                .map(executionContextTool::getMessages)
                .map(l -> l.stream()
                        .filter(a -> ActionType.EXECUTE.equals(a.getType()))
                        .map(IAction::getMessages)
                        .filter(l2 -> !l2.isEmpty())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public static List<IMessage> getMessagesJoin(ExecutionContextTool executionContextTool) {
        return Stream.iterate(0, n -> n + 1)
                .limit(executionContextTool.countSource())
                .flatMap(i -> executionContextTool.getMessages(i).stream())
                .filter(ModuleUtils::hasData)
                .flatMap(a -> a.getMessages().stream())
                .collect(Collectors.toList());
    }

    public static void processMessages(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, CheckedConsumer<LinkedList<IMessage>> func) {
        List<List<List<IMessage>>> messagesAll = ModuleUtils.getMessages(executionContextTool);
        for (int i = 0; i < messagesAll.size(); i++) {
            int id = i;
            messagesAll.get(i).forEach(messagesList -> {
                try {
                    LinkedList<IMessage> messages = new LinkedList<>(messagesList);
                    func.accept(id, messages);
                } catch (Exception e) {
                    executionContextTool.addError(ModuleUtils.getErrorMessageOrClassName(e));
                    configurationTool.loggerWarn(ModuleUtils.getStackTraceAsString(e));
                }
            });

        }
    }

    public static List<ObjectElement> toList(ObjectArray objectArray) {
        if (!isArrayContainObjectElements(objectArray))
            return List.of();
        List<ObjectElement> list = new ArrayList<>(objectArray.size());
        for (int i = 0; i < objectArray.size(); i++)
            list.add((ObjectElement) objectArray.get(i));
        return list;
    }

    public static <T> List<T> convertFromObjectArray(ObjectArray objectArray, Class<T> resultClass, boolean silent, boolean ignoreCaseInName) {
        if (objectArray == null || objectArray.size() == 0)
            return List.of();
        if (objectArray.isSimple()) {
            return toList(objectArray).stream()
                    .filter(o -> resultClass.isAssignableFrom(o.getClass()))
                    .map(o -> (T) o)
                    .collect(Collectors.toList());
        } else if (isArrayContainArrays(objectArray) && List.class.isAssignableFrom(resultClass)) {
            List<T> result = new ArrayList<>(objectArray.size());
            for (int i = 0; i < objectArray.size(); i++) {
                ObjectArray objectArrayElement = (ObjectArray) objectArray.get(i);
                if (objectArrayElement.isSimple()) {
                    result.add((T) Stream.iterate(0, n -> n + 1)
                            .limit(objectArrayElement.size())
                            .map(objectArrayElement::get)
                            .collect(Collectors.toList()));
                }
            }
            return result;
        } else if (isArrayContainObjectElements(objectArray)) {
            return toList(objectArray).stream()
                    .map(o -> convertFromObjectElement(o, resultClass, silent, ignoreCaseInName))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public static <T> T convertFromObjectElement(ObjectElement objectElement, Class<T> resultClass, boolean silent, boolean ignoreCaseInName) {
        if (objectElement == null)
            return null;
        try {
            T t = resultClass.getConstructor().newInstance();
            BeanInfo info = Introspector.getBeanInfo(resultClass);
            List<PropertyDescriptor> properties = Arrays.stream(info.getPropertyDescriptors()).collect(Collectors.toList());
            for (ObjectField f : objectElement.getFields()) {
                Optional<PropertyDescriptor> pOpt = properties.stream().filter(p -> ignoreCaseInName ? f.getName().equalsIgnoreCase(p.getName()) : f.getName().equals(p.getName())).findFirst();
                if (pOpt.isEmpty())
                    continue;
                PropertyDescriptor p = pOpt.get();
                if(p.getWriteMethod() == null)
                    continue;
                String setter = p.getWriteMethod().getName();
                Class<?> parameterType = p.getPropertyType();
                Object value = null;
                try {
                    Method setterMethod = resultClass.getDeclaredMethod(setter, parameterType);
                    value = f.getValue();
                    if (parameterType.equals(Boolean.class)) {
                        if (isString(f)) {
                            value = Boolean.parseBoolean((String) value);
                        } else if (isNumber(f)) {
                            value = ((Number) value).intValue() > 0;
                        }
                    } else if (List.class.isAssignableFrom(parameterType)) {
                        ParameterizedType pType = (ParameterizedType) p.getReadMethod().getGenericReturnType();
                        Class<?> pClass = (Class<?>) pType.getActualTypeArguments()[0];
                        value = convertFromObjectArray(getObjectArray(f), pClass, silent, ignoreCaseInName);
                    } else if (parameterType.equals(Date.class)) {
                        if (isString(f)) {
                            value = new Date(Long.parseLong((String) value));
                        } else if (isNumber(f)) {
                            value = new Date(((Number) value).longValue());
                        }
                    } else if (parameterType.equals(Instant.class)) {
                        if (isString(f)) {
                            value = Instant.ofEpochMilli(Long.parseLong((String) value));
                        } else if (isNumber(f)) {
                            value = Instant.ofEpochMilli(((Number) value).longValue());
                        }
                    } else if (!parameterType.equals(String.class) && !Number.class.isAssignableFrom(parameterType) && !byte[].class.isAssignableFrom(parameterType)) {
                        value = convertFromObjectElement(getObjectElement(f), parameterType, silent, ignoreCaseInName);
                    }
                    if (value != null)
                        setterMethod.invoke(t, value);
                } catch (Exception e) {
                    if (!silent)
                        throw new RuntimeException(setter + " " + parameterType.getName() + " " + (value != null ? value.getClass().getName() : ""), e);
                }
            }
            return t;
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return null;
    }

    public static <T> ObjectArray convertToObjectArray(List<T> t, Class<T> resultClass, boolean silent) {
        ObjectArray objectArray = null;
        if (t == null)
            return new ObjectArray();
        if (resultClass.equals(String.class) || Number.class.isAssignableFrom(resultClass) || byte[].class.isAssignableFrom(resultClass)) {
            objectArray = new ObjectArray((List) t, ObjectType.VALUE_ANY);
        } else if (List.class.isAssignableFrom(resultClass)) {
            ObjectArray objectArrayTmp = new ObjectArray(ObjectType.OBJECT_ARRAY);
            t.stream()
                    .filter(o -> o instanceof String || o instanceof Number || o instanceof byte[])
                    .forEach(objectArrayTmp::addValueAny);
            objectArray = objectArrayTmp;
        } else {
            objectArray = new ObjectArray(
                    t.stream()
                            .map(o -> convertToObjectElement(o, silent))
                            .collect(Collectors.toList()),
                    ObjectType.OBJECT_ELEMENT);
        }
        return objectArray;
    }

    public static <T> ObjectElement convertToObjectElement(T t, boolean silent) {
        ObjectElement objectElement = new ObjectElement();
        if (t == null)
            return objectElement;
        try {
            Class<?> resultClass = t.getClass();
            BeanInfo info = Introspector.getBeanInfo(resultClass);
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            for (PropertyDescriptor p : props) {
                String name = p.getName();
                String getter = p.getReadMethod().getName();
                Class<?> parameterType = p.getPropertyType();
                Object value = null;
                try {
                    Method getterMethod = resultClass.getDeclaredMethod(getter);
                    value = getterMethod.invoke(t);
                    if (parameterType.equals(Boolean.class)) {
                        value = value.toString();
                    } else if (List.class.isAssignableFrom(parameterType)) {
                        ParameterizedType pType = (ParameterizedType) p.getReadMethod().getGenericReturnType();
                        Class<?> pClass = (Class<?>) pType.getActualTypeArguments()[0];
                        value = convertToObjectArray((List) value, pClass, silent);
                    } else if (parameterType.equals(Date.class)) {
                        value = ((Date) value).getTime();
                    } else if (parameterType.equals(Instant.class)) {
                        value = ((Instant) value).toEpochMilli();
                    } else if (!parameterType.equals(String.class) && !Number.class.isAssignableFrom(parameterType) && !byte[].class.isAssignableFrom(parameterType)) {
                        value = convertToObjectElement(value, silent);
                    }
                    if (value != null)
                        objectElement.getFields().add(new ObjectField(name, getObjectType(value), value));
                } catch (NoSuchMethodException e) {
                    if(!Objects.equals(name, "class") && !silent)
                        throw new RuntimeException(getter + " " + parameterType.getName() + " " + (value != null ? value.getClass().getName() : ""), e);
                } catch (Exception e) {
                    if (!silent)
                        throw new RuntimeException(getter + " " + parameterType.getName() + " " + (value != null ? value.getClass().getName() : ""), e);
                }
            }
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return objectElement;
    }

}
