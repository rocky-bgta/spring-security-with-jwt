package com.salahin.springsecurity.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoogleConfigJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final String PROPERTY_SOURCE_NAME = "localConfigJson";
	private static final String GOOGLE_CLIENT_ID_PROPERTY = "spring.security.oauth2.client.registration.google.client-id";
	private static final String GOOGLE_CLIENT_SECRET_PROPERTY = "spring.security.oauth2.client.registration.google.client-secret";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path configJson = Path.of("config.json");
		if (!Files.isRegularFile(configJson)) {
			return;
		}

		Map<String, Object> properties = readAndFlatten(configJson);
		copyGoogleAlias(properties, "google.client-id", GOOGLE_CLIENT_ID_PROPERTY);
		copyGoogleAlias(properties, "google.clientId", GOOGLE_CLIENT_ID_PROPERTY);
		copyGoogleAlias(properties, "google.oauth2.client-id", GOOGLE_CLIENT_ID_PROPERTY);
		copyGoogleAlias(properties, "google.oauth2.clientId", GOOGLE_CLIENT_ID_PROPERTY);
		copyGoogleAlias(properties, "google.client-secret", GOOGLE_CLIENT_SECRET_PROPERTY);
		copyGoogleAlias(properties, "google.clientSecret", GOOGLE_CLIENT_SECRET_PROPERTY);
		copyGoogleAlias(properties, "google.oauth2.client-secret", GOOGLE_CLIENT_SECRET_PROPERTY);
		copyGoogleAlias(properties, "google.oauth2.clientSecret", GOOGLE_CLIENT_SECRET_PROPERTY);

		environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	private Map<String, Object> readAndFlatten(Path configJson) {
		try {
			Map<String, Object> values = objectMapper.readValue(
				configJson.toFile(),
				new TypeReference<Map<String, Object>>() {
				});
			Map<String, Object> properties = new LinkedHashMap<>();
			flatten("", values, properties);
			return properties;
		} catch (IOException ex) {
			throw new IllegalStateException("Unable to read config.json", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private void flatten(String prefix, Map<String, Object> values, Map<String, Object> properties) {
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				flatten(key, (Map<String, Object>) value, properties);
			} else {
				properties.put(key, value);
			}
		}
	}

	private void copyGoogleAlias(Map<String, Object> properties, String sourceKey, String targetKey) {
		Object value = properties.get(sourceKey);
		if (value != null) {
			properties.put(targetKey, value);
		}
	}
}
