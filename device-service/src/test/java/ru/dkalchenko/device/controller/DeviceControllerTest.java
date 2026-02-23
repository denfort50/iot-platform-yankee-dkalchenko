package ru.dkalchenko.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.service.DeviceService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@MockBean
	DeviceService deviceService;

	@Test
	@WithMockUser
	void createDevice_returns202() throws Exception {
		String body = """
				{"deviceType":"sensor","meta":"{}"}
				""";
		org.mockito.Mockito.when(deviceService.createDevice(any())).thenReturn("dev-1");
		mockMvc.perform(post("/v1/devices")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(deviceService).createDevice(any());
	}

	@Test
	@WithMockUser
	void getDevice_whenFound_returns200() throws Exception {
		Device device = new Device()
				.deviceId("dev-1")
				.deviceType("sensor")
				.createdAt(1234567890L)
				.meta("{}")
				.updatedAt(OffsetDateTime.now());
		org.mockito.Mockito.when(deviceService.getDevice("dev-1"))
				.thenReturn(Optional.of(device));

		mockMvc.perform(get("/v1/devices/dev-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceId").value("dev-1"))
				.andExpect(jsonPath("$.deviceType").value("sensor"));
	}

	@Test
	@WithMockUser
	void getDevice_whenNotFound_returns404() throws Exception {
		org.mockito.Mockito.when(deviceService.getDevice("unknown")).thenReturn(Optional.empty());

		mockMvc.perform(get("/v1/devices/unknown"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void updateDevice_returns202() throws Exception {
		String body = """
				{"deviceType":"actuator","meta":"updated"}
				""";
		mockMvc.perform(put("/v1/devices/dev-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(deviceService).updateDevice(eq("dev-1"), any());
	}

	@Test
	@WithMockUser
	void deleteDevice_returns202() throws Exception {
		mockMvc.perform(delete("/v1/devices/dev-1").with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(deviceService).deleteDevice("dev-1");
	}
}
