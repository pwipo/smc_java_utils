package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;
import ru.smcsystem.smc.utils.ModuleUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SmcConverterMap extends SmcConverter<Map<String, Object>> {
    private final String key;
    private final String value;
    private final boolean ignoreCase;
    private final boolean convertToArray;

    public SmcConverterMap(Class<?> fieldType, String fieldName, Class<?> objectType) {
        SmcFieldMap annotation;
        try {
            annotation = objectType.getDeclaredField(fieldName).getAnnotation(SmcFieldMap.class);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        this.key = annotation != null && annotation.key() != null ? annotation.key() : "key";
        this.value = annotation != null && annotation.value() != null ? annotation.value() : "value";
        this.ignoreCase = annotation != null && annotation.ignoreCase();
        this.convertToArray = annotation != null;
    }

    @Override
    public Map<String, Object> to(ObjectField field, ObjectElement objectElement) throws Exception {
        if (field.isSimple()) {
            return new HashMap<>(Map.of(field.getName(), field.getValue()));
        } else if (ModuleUtils.isObjectElement(field)) {
            return ModuleUtils.toObjectElement(field).getFields().stream()
                    .collect(Collectors.toMap(ObjectField::getName, ObjectField::getValue));
        } else if (ModuleUtils.isObjectArray(field)) {
            ObjectArray objectArray = ModuleUtils.getObjectArray(field);
            HashMap<String, Object> result = new HashMap<>(objectArray.size());
            if (ModuleUtils.isArrayContainObjectElements(objectArray)) {
                for (int i = 0; i < objectArray.size(); i++) {
                    Map.Entry<String, Object> entry = toEntry((ObjectElement) objectArray.get(i));
                    if (entry != null)
                        result.put(entry.getKey(), entry.getValue());
                }
            } else if (objectArray.isSimple()) {
                for (int i = 0; i < objectArray.size(); i++)
                    result.put(String.valueOf(i), objectArray.get(i));
            }
            return result;
        }
        return null;
    }

    private Map.Entry<String, Object> toEntry(ObjectElement objectElement) {
        if (objectElement == null)
            return null;
        String key = Optional.of(ignoreCase ? objectElement.findFieldIgnoreCase(this.key) : objectElement.findField(this.key))
                .flatMap(o -> o).filter(ObjectField::isSimple).map(ModuleUtils::toString).orElse(null);
        Object value = Optional.of(ignoreCase ? objectElement.findFieldIgnoreCase(this.value) : objectElement.findField(this.value))
                .flatMap(o -> o).filter(ObjectField::isSimple).orElse(null);
        return key != null && value != null ? Map.entry(key, value) : null;
    }

    @Override
    public ObjectField from(String name, Map<String, Object> v, ObjectElement objectElement) throws Exception {
        if (convertToArray) {
            return new ObjectField(name, new ObjectArray(
                    v.entrySet().stream().map(e -> new ObjectElement(
                            new ObjectField(key, e.getKey()), new ObjectField(value, ModuleUtils.getObjectType(e.getValue()), e.getValue())
                    )).collect(Collectors.toList()),
                    ObjectType.OBJECT_ELEMENT));
        } else {
            return new ObjectField(name, new ObjectElement(v.entrySet().stream()
                    .map(e -> new ObjectField(e.getKey(), ModuleUtils.getObjectType(e.getValue()), e.getValue()))
                    .collect(Collectors.toList())));
        }
    }

}
