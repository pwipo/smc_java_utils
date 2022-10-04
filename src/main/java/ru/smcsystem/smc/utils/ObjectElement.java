package ru.smcsystem.smc.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Object in ObjectArray
 * use for serialization/deserialization messages as object - object serialization format
 * contain list of fields (ObjectField)
 */
public class ObjectElement implements Serializable {

    private final List<ObjectField> fields;

    public ObjectElement(List<ObjectField> fields) {
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    }

    public ObjectElement() {
        this(null);
    }

    public List<ObjectField> getFields() {
        return fields;
    }

    public Optional<ObjectField> findField(String name) {
        return fields.stream()
                .filter(f -> StringUtils.equals(f.getName(), name))
                .findFirst();
    }

    public boolean isSimple() {
        return getFields().stream().allMatch(ObjectField::isSimple);
    }

}
