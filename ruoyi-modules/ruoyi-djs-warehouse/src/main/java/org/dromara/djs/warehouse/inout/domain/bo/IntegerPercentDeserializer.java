package org.dromara.djs.warehouse.inout.domain.bo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/** 绩效百分比保留 JSON 原始整数约束，避免小数在绑定为 Integer 时被截断。 */
public class IntegerPercentDeserializer extends StdDeserializer<Integer> {

    public IntegerPercentDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser.currentToken(), parser,
            "绩效百分比须为 1-100 的整数");
    }
}
