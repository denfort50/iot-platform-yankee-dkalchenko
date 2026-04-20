package ru.dkalchenko.event.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.event.api.model.Event;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEventRepositoryTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	private DeviceEventRepository repository;

	@BeforeEach
	void setUp() {
		repository = new DeviceEventRepository(jdbcTemplate);
	}

	@Test
	void whenCountAndFiltersProvidedThenSqlContainsAllPredicates() {
		when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(5L);

		long total = repository.count("dev-1", 10L, 20L, "temperature");

		assertThat(total).isEqualTo(5L);
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), argsCaptor.capture());
		assertThat(sqlCaptor.getValue())
				.contains("SELECT count()")
				.contains("device_id = ?")
				.contains("timestamp_ms >= ?")
				.contains("timestamp_ms <= ?")
				.contains("type = ?");
		assertThat(argsCaptor.getValue()).containsExactly("dev-1", 10L, 20L, "temperature");
	}

	@Test
	void whenCountReturnsNullThenZero() {
		when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(null);

		long total = repository.count("dev-1", null, null, null);

		assertThat(total).isZero();
	}

	@Test
	void whenExistsDeviceAndRowsReturnedThenTrue() {
		when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("dev-1")))
				.thenReturn(List.of(1));

		boolean exists = repository.existsDevice("dev-1");

		assertThat(exists).isTrue();
	}

	@Test
	void whenExistsDeviceAndNoRowsThenFalse() {
		when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("dev-2")))
				.thenReturn(List.of());

		boolean exists = repository.existsDevice("dev-2");

		assertThat(exists).isFalse();
	}

	@Test
	void whenFindPageThenAddsOrderLimitAndOffset() {
		Event event = new Event().eventId("e-1").deviceId("dev-1").timestamp(100L).type("t").payload("{}");
		when(jdbcTemplate.query(any(String.class), eq(DeviceEventRepository.ROW_MAPPER), any(Object[].class)))
				.thenReturn(List.of(event));

		List<Event> result = repository.findPage("dev-1", null, null, "t", 2, 3);

		assertThat(result).hasSize(1);
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), eq(DeviceEventRepository.ROW_MAPPER), argsCaptor.capture());
		assertThat(sqlCaptor.getValue())
				.contains("ORDER BY timestamp_ms DESC, event_id DESC")
				.contains("LIMIT ? OFFSET ?");
		assertThat(argsCaptor.getValue()).containsExactly("dev-1", "t", 3, 2);
	}

	@Test
	void whenFindByDeviceAndEventIdAndRowExistsThenReturnsFirst() {
		Event event = new Event().eventId("e-1").deviceId("dev-1").timestamp(100L).type("t").payload("{}");
		when(jdbcTemplate.query(any(String.class), eq(DeviceEventRepository.ROW_MAPPER), eq("dev-1"), eq("e-1")))
				.thenReturn(List.of(event));

		Optional<Event> result = repository.findByDeviceAndEventId("dev-1", "e-1");

		assertThat(result).contains(event);
	}

	@Test
	void whenFindByDeviceAndEventIdAndNoRowsThenEmpty() {
		when(jdbcTemplate.query(any(String.class), eq(DeviceEventRepository.ROW_MAPPER), eq("dev-1"), eq("missing")))
				.thenReturn(List.of());

		Optional<Event> result = repository.findByDeviceAndEventId("dev-1", "missing");

		assertThat(result).isEmpty();
	}
}
