package com.gzxm.server.modules.indicator.api;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** Keep integer coercion rules local to the indicator contract. */
public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        if (parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)
            return (Integer)context.handleUnexpectedToken(Integer.class,parser);
        return parser.getIntValue();
    }
}
