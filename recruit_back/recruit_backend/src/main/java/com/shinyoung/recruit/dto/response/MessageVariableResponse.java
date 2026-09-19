package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;

import java.util.List;

public record MessageVariableResponse(
        String key,
        String label,
        List<MessageType> types
) {
    public static MessageVariableResponse from(MessageVariable variable) {
        return new MessageVariableResponse(
                variable.getKey(),
                variable.getLabel(),
                variable.getTypes().stream().sorted().toList()
        );
    }
}
