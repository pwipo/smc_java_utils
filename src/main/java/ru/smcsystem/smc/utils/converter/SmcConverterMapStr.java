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

public class SmcConverterMapStr extends SmcConverter<Map<String, String>> {
    private final String key;
    private final String value;
    private final boolean ignoreCase;

    public SmcConverterMapStr(Class<?> fieldType, String fieldName, Class<?> objectType) {
        SmcFieldMap annotation;
        try {
            annotation = objectType.getDeclaredField(fieldName).getAnnotation(SmcFieldMap.class);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        this.key = annotation != null && annotation.key() != null ? annotation.key() : "key";
        this.value = annotation != null && annotation.value() != null ? annotation.value() : "value";
        this.ignoreCase = annotation != null && annotation.ignoreCase();
    }

    @Override
    public Map<String, String> to(ObjectField field, ObjectElement objectElement) throws Exception {
        if (field.isSimple()) {
            return new HashMap<>(Map.of(field.getName(), field.getValue().toString()));
        } else if (ModuleUtils.isObjectElement(field)) {
            Map.Entry<String, String> entry = toEntry(ModuleUtils.toObjectElement(field));
            if (entry != null)
                return Map.of(entry.getKey(), entry.getValue());
        } else if (ModuleUtils.isObjectArray(field)) {
            ObjectArray objectArray = ModuleUtils.getObjectArray(field);
            HashMap<String, String> result = new HashMap<>(objectArray.size());
            if (ModuleUtils.isArrayContainObjectElements(objectArray)) {
                for (int i = 0; i < objectArray.size(); i++) {
                    Map.Entry<String, String> entry = toEntry((ObjectElement) objectArray.get(i));
                    if (entry != null)
                        result.put(entry.getKey(), entry.getValue());
                }
            } else if (objectArray.isSimple()) {
                for (int i = 0; i < objectArray.size(); i++)
                    result.put(String.valueOf(i), objectArray.get(i).toString());
            }
            return result;
        }
        return null;
    }

    private Map.Entry<String, String> toEntry(ObjectElement objectElement) {
        if (objectElement == null)
            return null;
        String key = Optional.of(ignoreCase ? objectElement.findFieldIgnoreCase(this.key) : objectElement.findField(this.key))
                .flatMap(o -> o).filter(ObjectField::isSimple).map(ModuleUtils::toString).orElse(null);
        String value = Optional.of(ignoreCase ? objectElement.findFieldIgnoreCase(this.value) : objectElement.findField(this.value))
                .flatMap(o -> o).filter(ObjectField::isSimple).map(ModuleUtils::toString).orElse(null);
        return key != null && value != null ? Map.entry(key, value) : null;
    }

    @Override
    public ObjectField from(String name, Map<String, String> v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, new ObjectArray(
                v.entrySet().stream().map(e -> new ObjectElement(
                        new ObjectField(key, e.getKey()), new ObjectField(value, e.getValue())
                )).collect(Collectors.toList()),
                ObjectType.OBJECT_ELEMENT));
    }

}
