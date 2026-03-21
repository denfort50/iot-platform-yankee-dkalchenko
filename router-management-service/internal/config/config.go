// Package config — загрузка и парсинг конфигурации сервиса из YAML и переменных окружения.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

// Config — корневая конфигурация: настройки сервера (порты), подключения к PostgreSQL и фонового планировщика.
type Config struct {
	Server    ServerConfig    `yaml:"server"`
	Postgres  PostgresConfig  `yaml:"postgres"`
	Scheduler SchedulerConfig `yaml:"scheduler"`
	Auth      AuthConfig      `yaml:"auth"`
	Telemetry TelemetryConfig `yaml:"telemetry"`
}

// TelemetryConfig — OTLP-трейсы (OpenTelemetry); при непустом endpoint включается экспорт в Alloy/Tempo.
type TelemetryConfig struct {
	// OTLPTracesEndpoint — полный URL HTTP, например http://alloy:4318/v1/traces (ENV: OTEL_EXPORTER_OTLP_TRACES_ENDPOINT).
	OTLPTracesEndpoint string `yaml:"otlp_traces_endpoint"`
	// ServiceName — имя сервиса в трейсах (ENV: OTEL_SERVICE_NAME).
	ServiceName string `yaml:"service_name"`
}

// ServerConfig — порты для gRPC и HTTP (health/ready).
type ServerConfig struct {
	GRPCPort int `yaml:"grpc_port"`
	HTTPPort int `yaml:"http_port"`
}

// SchedulerConfig — настройки фонового планировщика для обработки зависших команд.
type SchedulerConfig struct {
	// Enabled включает/выключает планировщик.
	Enabled bool `yaml:"enabled"`
	// Cron — cron-выражение (5 полей: минута, час, день месяца, месяц, день недели).
	Cron string `yaml:"cron"`
	// SentTimeoutMinutes — сколько минут команда может находиться в статусе SENT до пометки FAILED.
	SentTimeoutMinutes int `yaml:"sent_timeout_minutes"`
}

// AuthConfig — настройки аутентификации через Keycloak (OIDC JWT).
type AuthConfig struct {
	// Enabled включает/выключает проверку JWT для gRPC-запросов.
	Enabled bool `yaml:"enabled"`
	// IssuerURL — URL issuer'а (например, http://host.docker.internal:8080/realms/master).
	IssuerURL string `yaml:"issuer_url"`
}

// PostgresConfig — параметры подключения к БД PostgreSQL.
type PostgresConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	User     string `yaml:"user"`
	Password string `yaml:"password"`
	DBName   string `yaml:"dbname"`
	SSLMode  string `yaml:"sslmode"`
}

// DSN возвращает строку подключения к PostgreSQL (postgres://user:pass@host:port/db?sslmode=...).
func (c PostgresConfig) DSN() string {
	if c.Port == 0 {
		c.Port = 5432
	}
	if c.SSLMode == "" {
		c.SSLMode = "disable"
	}
	return fmt.Sprintf("postgres://%s:%s@%s:%d/%s?sslmode=%s",
		c.User, c.Password, c.Host, c.Port, c.DBName, c.SSLMode)
}

// Load читает конфиг: по умолчанию — дефолтные значения; если задан CONFIG_PATH — подставляет из YAML; затем переопределяет значения из ENV (GRPC_PORT, HTTP_PORT, POSTGRES_*).
func Load() (*Config, error) {
	cfg := &Config{
		Server: ServerConfig{
			GRPCPort: 50051,
			HTTPPort: 8080,
		},
		Postgres: PostgresConfig{
			Host:    "localhost",
			Port:    5432,
			User:    "postgres",
			DBName:  "router_management",
			SSLMode: "disable",
		},
		Scheduler: SchedulerConfig{
			Enabled:            false,
			Cron:               "*/1 * * * *", // по умолчанию раз в минуту
			SentTimeoutMinutes: 10,            // по умолчанию 10 минут
		},
		Auth: AuthConfig{
			Enabled:   false,
			IssuerURL: "",
		},
		Telemetry: TelemetryConfig{
			ServiceName: "router-management-service",
		},
	}

	if path := os.Getenv("CONFIG_PATH"); path != "" {
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, fmt.Errorf("read config file: %w", err)
		}
		if err := yaml.Unmarshal(data, cfg); err != nil {
			return nil, fmt.Errorf("parse config: %w", err)
		}
	}

	overrideFromEnv(cfg)
	return cfg, nil
}

// overrideFromEnv перезаписывает поля конфига значениями из переменных окружения, если они заданы.
func overrideFromEnv(cfg *Config) {
	if v := os.Getenv("GRPC_PORT"); v != "" {
		if p, err := strconv.Atoi(v); err == nil {
			cfg.Server.GRPCPort = p
		}
	}
	if v := os.Getenv("HTTP_PORT"); v != "" {
		if p, err := strconv.Atoi(v); err == nil {
			cfg.Server.HTTPPort = p
		}
	}
	if v := os.Getenv("POSTGRES_HOST"); v != "" {
		cfg.Postgres.Host = v
	}
	if v := os.Getenv("POSTGRES_PORT"); v != "" {
		if p, err := strconv.Atoi(v); err == nil {
			cfg.Postgres.Port = p
		}
	}
	if v := os.Getenv("POSTGRES_USER"); v != "" {
		cfg.Postgres.User = v
	}
	if v := os.Getenv("POSTGRES_PASSWORD"); v != "" {
		cfg.Postgres.Password = v
	}
	if v := os.Getenv("POSTGRES_DB"); v != "" {
		cfg.Postgres.DBName = v
	}
	if v := os.Getenv("POSTGRES_SSLMODE"); v != "" {
		cfg.Postgres.SSLMode = v
	}

	if v := os.Getenv("SCHEDULER_ENABLED"); v != "" {
		switch strings.ToLower(v) {
		case "1", "true", "yes", "on":
			cfg.Scheduler.Enabled = true
		case "0", "false", "no", "off":
			cfg.Scheduler.Enabled = false
		}
	}
	if v := os.Getenv("SCHEDULER_CRON"); v != "" {
		cfg.Scheduler.Cron = v
	}
	if v := os.Getenv("SENT_TIMEOUT_MINUTES"); v != "" {
		if m, err := strconv.Atoi(v); err == nil && m > 0 {
			cfg.Scheduler.SentTimeoutMinutes = m
		}
	}

	if v := os.Getenv("KEYCLOAK_ISSUER_URI"); v != "" {
		cfg.Auth.IssuerURL = strings.TrimSpace(v)
		// Если настроен issuer, по умолчанию включаем auth; можно переопределить через AUTH_ENABLED.
		cfg.Auth.Enabled = true
	}
	if v := os.Getenv("AUTH_ENABLED"); v != "" {
		switch strings.ToLower(v) {
		case "1", "true", "yes", "on":
			cfg.Auth.Enabled = true
		case "0", "false", "no", "off":
			cfg.Auth.Enabled = false
		}
	}

	if v := strings.TrimSpace(os.Getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT")); v != "" {
		cfg.Telemetry.OTLPTracesEndpoint = v
	}
	if v := strings.TrimSpace(os.Getenv("OTEL_SERVICE_NAME")); v != "" {
		cfg.Telemetry.ServiceName = v
	}
}

// GRPCAddr возвращает адрес для прослушивания gRPC-сервера (например, ":50051").
func (c *Config) GRPCAddr() string {
	return fmt.Sprintf(":%d", c.Server.GRPCPort)
}

// HTTPAddr возвращает адрес для HTTP-сервера (health/ready), например ":8080".
func (c *Config) HTTPAddr() string {
	return fmt.Sprintf(":%d", c.Server.HTTPPort)
}

// PostgresDSN возвращает DSN для PostgreSQL с обрезанными пробелами.
func (c *Config) PostgresDSN() string {
	return strings.TrimSpace(c.Postgres.DSN())
}

// TracingEnabled — true, если задан endpoint экспорта OTLP-трейсов.
func (c *Config) TracingEnabled() bool {
	return strings.TrimSpace(c.Telemetry.OTLPTracesEndpoint) != ""
}

// TelemetryServiceName — имя сервиса для спанов; непустой дефолт из конфигурации.
func (c *Config) TelemetryServiceName() string {
	if s := strings.TrimSpace(c.Telemetry.ServiceName); s != "" {
		return s
	}
	return "router-management-service"
}
