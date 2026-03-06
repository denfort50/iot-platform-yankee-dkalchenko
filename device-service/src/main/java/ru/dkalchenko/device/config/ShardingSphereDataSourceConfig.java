package ru.dkalchenko.device.config;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * Создаёт DataSource ShardingSphere из YAML с подстановкой плейсхолдеров Spring.
 */
@Configuration
public class ShardingSphereDataSourceConfig {

	@Bean
	public DataSource dataSource(Environment environment) throws IOException, SQLException {
		ClassPathResource resource = new ClassPathResource("sharding-sphere-config.yaml");
		StringBuilder sb = new StringBuilder();
		try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
			char[] buf = new char[8192];
			int n;
			while ((n = reader.read(buf)) != -1) {
				sb.append(buf, 0, n);
			}
		}
		String yamlContent = sb.toString();
		String resolved = environment.resolvePlaceholders(yamlContent);
		byte[] yamlBytes = resolved.getBytes(StandardCharsets.UTF_8);
		return YamlShardingSphereDataSourceFactory.createDataSource(yamlBytes);
	}
}
