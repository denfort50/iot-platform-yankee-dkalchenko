package ru.dkalchenko.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.dkalchenko.device.api.model.CreateResponse;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.api.model.MessageResponse;
import ru.dkalchenko.device.api.server.DevicesApiController;
import ru.dkalchenko.device.api.server.DevicesApiDelegate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DevicesApiController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@MockBean
	DevicesApiDelegate devicesApiDelegate;

	@Test
	@WithMockUser
	void createDevice_returns202() throws Exception {
		String body = """
				{"deviceType":"sensor","meta":"{}"}
				""";
		when(devicesApiDelegate.createDevice(any(DeviceRequest.class)))
				.thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
						.body(new CreateResponse().message("Device create request accepted").deviceId("dev-1")));
		mockMvc.perform(post("/v1/devices")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(devicesApiDelegate).createDevice(any());
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
		when(devicesApiDelegate.getDevice("dev-1")).thenReturn(ResponseEntity.ok(device));

		mockMvc.perform(get("/v1/devices/dev-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceId").value("dev-1"))
				.andExpect(jsonPath("$.deviceType").value("sensor"));
	}

	@Test
	@WithMockUser
	void getDevice_whenNotFound_returns404() throws Exception {
		when(devicesApiDelegate.getDevice("unknown")).thenReturn(ResponseEntity.notFound().build());

		mockMvc.perform(get("/v1/devices/unknown"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void updateDevice_returns202() throws Exception {
		String body = """
				{"deviceType":"actuator","meta":"updated"}
				""";
		when(devicesApiDelegate.updateDevice(eq("dev-1"), any(DeviceRequest.class)))
				.thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
						.body(new MessageResponse().message("Device update request accepted").deviceId("dev-1")));
		mockMvc.perform(put("/v1/devices/dev-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(devicesApiDelegate).updateDevice(eq("dev-1"), any());
	}

	@Test
	@WithMockUser
	void deleteDevice_returns202() throws Exception {
		when(devicesApiDelegate.deleteDevice("dev-1"))
				.thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
						.body(new MessageResponse().message("Device delete request accepted").deviceId("dev-1")));
		mockMvc.perform(delete("/v1/devices/dev-1").with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.deviceId").value("dev-1"));
		verify(devicesApiDelegate).deleteDevice("dev-1");
	}
}
