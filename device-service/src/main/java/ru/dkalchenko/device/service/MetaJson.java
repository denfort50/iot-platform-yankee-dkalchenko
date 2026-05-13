package ru.dkalchenko.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Обогащение meta (JSON-строка) полем целевой версии ПО.
 */
final class MetaJson {

	static final ObjectMapper MAPPER = new ObjectMapper();

	private MetaJson() {
	}

	static String mergeTargetSoftwareVersion(String metaNullable, String targetSoftwareVersion) {
		try {
			JsonNode root;
			if (metaNullable == null || metaNullable.isBlank()) {
				root = MAPPER.createObjectNode();
			} else {
				root = MAPPER.readTree(metaNullable);
				if (!root.isObject()) {
					ObjectNode wrap = MAPPER.createObjectNode();
					wrap.set("_original", root);
					root = wrap;
				}
			}
			((ObjectNode) root).put("targetSoftwareVersion", targetSoftwareVersion);
			return MAPPER.writeValueAsString(root);
		} catch (Exception e) {
			ObjectNode n = MAPPER.createObjectNode();
			n.put("targetSoftwareVersion", targetSoftwareVersion);
			try {
				return MAPPER.writeValueAsString(n);
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	static void enrichDeviceFromMeta(ru.dkalchenko.device.api.model.Device device) {
		String meta = device.getMeta();
		if (meta == null || meta.isBlank()) {
			return;
		}
		try {
			JsonNode root = MAPPER.readTree(meta);
			if (root.has("targetSoftwareVersion") && !root.get("targetSoftwareVersion").isNull()) {
				device.setTargetSoftwareVersion(root.get("targetSoftwareVersion").asText());
			}
		} catch (Exception ignored) {
			// meta не JSON — поле версии недоступно
		}
	}
}
