package ru.smcsystem.smc.utils;

import ru.smcsystem.smc.utils.converter.SmcConverterDate;
import ru.smcsystem.smc.utils.converter.SmcConverterInstant;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;

public class TestObject {
    @SmcField(order = 1, required = true)
    private String name;
    @SmcField(order = 4, name = "value_1", required = true)
    private Long value1;
    @SmcField(order = 5, name = "value_2")
    private Integer value2;
    @SmcField(order = 2, name = "value_3")
    private Float value3;
    @SmcField(order = 3, name = "value_4")
    private BigDecimal value4;
    private BigInteger value5;
    @SmcField(converter = SmcConverterDate.class)
    private Date date;
    @SmcField(converter = SmcConverterInstant.class)
    private Instant instant;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getValue1() {
        return value1;
    }

    public void setValue1(Long value1) {
        this.value1 = value1;
    }

    public Integer getValue2() {
        return value2;
    }

    public void setValue2(Integer value2) {
        this.value2 = value2;
    }

    public Float getValue3() {
        return value3;
    }

    public void setValue3(Float value3) {
        this.value3 = value3;
    }

    public BigDecimal getValue4() {
        return value4;
    }

    public void setValue4(BigDecimal value4) {
        this.value4 = value4;
    }

    public BigInteger getValue5() {
        return value5;
    }

    public void setValue5(BigInteger value5) {
        this.value5 = value5;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }
}
