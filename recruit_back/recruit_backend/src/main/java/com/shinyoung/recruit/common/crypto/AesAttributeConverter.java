package com.shinyoung.recruit.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AesAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if(attribute == null) return null;
        return CryptoHolder.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if(dbData == null) return null;
        return CryptoHolder.get().decrypt(dbData);
    }
}
