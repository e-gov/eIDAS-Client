package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.authnrequest.SPType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class StringToSPTypeEnumConverter implements Converter<String, SPType> {
    @Override
    public SPType convert(String source) {
        return source.isEmpty() ? null : SPType.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
