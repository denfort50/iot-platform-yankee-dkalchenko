package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func clearEnvForLoad(t *testing.T) {
	t.Helper()
	// Пустые значения означают "не переопределять" — overrideFromEnv проверяет v != "".
	t.Setenv("CONFIG_PATH", "")
	t.Setenv("GRPC_PORT", "")
	t.Setenv("HTTP_PORT", "")
	t.Setenv("POSTGRES_HOST", "")
	t.Setenv("POSTGRES_PORT", "")
	t.Setenv("POSTGRES_USER", "")
	t.Setenv("POSTGRES_PASSWORD", "")
	t.Setenv("POSTGRES_DB", "")
	t.Setenv("POSTGRES_SSLMODE", "")
	t.Setenv("SCHEDULER_ENABLED", "")
	t.Setenv("SCHEDULER_CRON", "")
	t.Setenv("SENT_TIMEOUT_MINUTES", "")
	t.Setenv("KEYCLOAK_ISSUER_URI", "")
	t.Setenv("AUTH_ENABLED", "")
	t.Setenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "")
	t.Setenv("OTEL_SERVICE_NAME", "")
}

func TestPostgresConfig_DSN(t *testing.T) {
	c := PostgresConfig{
		Host:     "db",
		Port:     0,
		User:     "u",
		Password: "p",
		DBName:   "d",
		SSLMode:  "",
	}

	dsn := c.DSN()
	// Дефолты: Port=5432, SSLMode=disable.
	if !strings.Contains(dsn, "@db:5432/d") {
		t.Fatalf("unexpected dsn: %s", dsn)
	}
	if !strings.Contains(dsn, "sslmode=disable") {
		t.Fatalf("unexpected dsn sslmode: %s", dsn)
	}

	cfg := &Config{
		Postgres: c,
	}
	if got := cfg.PostgresDSN(); !strings.Contains(got, "sslmode=disable") {
		t.Fatalf("unexpected Config.PostgresDSN: %s", got)
	}
}

func TestConfig_Addr(t *testing.T) {
	cfg := &Config{
		Server: ServerConfig{
			GRPCPort: 1234,
			HTTPPort: 8081,
		},
	}
	if got := cfg.GRPCAddr(); got != ":1234" {
		t.Fatalf("GRPCAddr: got %q", got)
	}
	if got := cfg.HTTPAddr(); got != ":8081" {
		t.Fatalf("HTTPAddr: got %q", got)
	}
}

func TestLoad_Defaults(t *testing.T) {
	clearEnvForLoad(t)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.Server.GRPCPort != 50051 {
		t.Fatalf("GRPCPort: got %d", cfg.Server.GRPCPort)
	}
	if cfg.Server.HTTPPort != 8080 {
		t.Fatalf("HTTPPort: got %d", cfg.Server.HTTPPort)
	}
	if cfg.Postgres.Host != "localhost" {
		t.Fatalf("Postgres.Host: got %q", cfg.Postgres.Host)
	}
	if cfg.Postgres.User != "postgres" {
		t.Fatalf("Postgres.User: got %q", cfg.Postgres.User)
	}
	if cfg.Postgres.DBName != "router_management" {
		t.Fatalf("Postgres.DBName: got %q", cfg.Postgres.DBName)
	}
	if cfg.Scheduler.Enabled {
		t.Fatalf("Scheduler.Enabled: expected false")
	}
	if cfg.Auth.Enabled {
		t.Fatalf("Auth.Enabled: expected false by default")
	}
}

func TestLoad_OverridesFromEnvAndYAML(t *testing.T) {
	clearEnvForLoad(t)

	// Подготовим YAML с базовыми значениями.
	dir := t.TempDir()
	cfgPath := filepath.Join(dir, "config.yaml")
	yaml := `
server:
  grpc_port: 1111
  http_port: 2222
postgres:
  host: "yaml-db"
  port: 5432
  user: "yaml-user"
  password: "yaml-pass"
  dbname: "yaml-dbname"
  sslmode: "disable"
scheduler:
  enabled: true
  cron: "*/5 * * * *"
  sent_timeout_minutes: 7
auth:
  enabled: false
  issuer_url: "http://example/realms/master"
`
	if err := os.WriteFile(cfgPath, []byte(yaml), 0o600); err != nil {
		t.Fatalf("write yaml: %v", err)
	}

	t.Setenv("CONFIG_PATH", cfgPath)

	// Env переопределяет часть полей и управляет Auth через Keycloak.
	t.Setenv("GRPC_PORT", "9999")
	t.Setenv("HTTP_PORT", "8888")
	t.Setenv("POSTGRES_HOST", "env-db")
	t.Setenv("POSTGRES_PORT", "5433")
	t.Setenv("KEYCLOAK_ISSUER_URI", "http://kc:8080/realms/master")
	t.Setenv("AUTH_ENABLED", "false")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.Server.GRPCPort != 9999 {
		t.Fatalf("GRPCPort: got %d", cfg.Server.GRPCPort)
	}
	if cfg.Server.HTTPPort != 8888 {
		t.Fatalf("HTTPPort: got %d", cfg.Server.HTTPPort)
	}
	if cfg.Postgres.Host != "env-db" {
		t.Fatalf("Postgres.Host: got %q", cfg.Postgres.Host)
	}
	if cfg.Postgres.Port != 5433 {
		t.Fatalf("Postgres.Port: got %d", cfg.Postgres.Port)
	}

	if cfg.Auth.IssuerURL != "http://kc:8080/realms/master" {
		t.Fatalf("Auth.IssuerURL: got %q", cfg.Auth.IssuerURL)
	}
	// AUTH_ENABLED имеет приоритет над автоподключением при наличии KEYCLOAK_ISSUER_URI.
	if cfg.Auth.Enabled {
		t.Fatalf("Auth.Enabled: expected false")
	}
}

func TestLoad_AuthAndSchedulerEnvParsing(t *testing.T) {
	clearEnvForLoad(t)

	t.Setenv("GRPC_PORT", "7001")
	t.Setenv("HTTP_PORT", "7002")

	t.Setenv("POSTGRES_PORT", "6000")
	t.Setenv("POSTGRES_SSLMODE", "require")

	// Покрываем разбор SCHEDULER_ENABLED (ветка true), а также SENT_TIMEOUT_MINUTES.
	t.Setenv("SCHEDULER_ENABLED", "on")
	t.Setenv("SCHEDULER_CRON", "*/10 * * * *")
	t.Setenv("SENT_TIMEOUT_MINUTES", "12")

	// Покрываем ветку AUTH_ENABLED=true (после автоподключения при наличии KEYCLOAK_ISSUER_URI).
	t.Setenv("KEYCLOAK_ISSUER_URI", "http://kc:8080/realms/master")
	t.Setenv("AUTH_ENABLED", "true")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.Server.GRPCPort != 7001 {
		t.Fatalf("GRPCPort: got %d", cfg.Server.GRPCPort)
	}
	if cfg.Server.HTTPPort != 7002 {
		t.Fatalf("HTTPPort: got %d", cfg.Server.HTTPPort)
	}

	if cfg.Postgres.Port != 6000 {
		t.Fatalf("Postgres.Port: got %d", cfg.Postgres.Port)
	}
	if cfg.Postgres.SSLMode != "require" {
		t.Fatalf("Postgres.SSLMode: got %q", cfg.Postgres.SSLMode)
	}

	if !cfg.Scheduler.Enabled {
		t.Fatalf("Scheduler.Enabled: expected true")
	}
	if cfg.Scheduler.Cron != "*/10 * * * *" {
		t.Fatalf("Scheduler.Cron: got %q", cfg.Scheduler.Cron)
	}
	if cfg.Scheduler.SentTimeoutMinutes != 12 {
		t.Fatalf("Scheduler.SentTimeoutMinutes: got %d", cfg.Scheduler.SentTimeoutMinutes)
	}

	if !cfg.Auth.Enabled {
		t.Fatalf("Auth.Enabled: expected true")
	}
	if cfg.Auth.IssuerURL != "http://kc:8080/realms/master" {
		t.Fatalf("Auth.IssuerURL: got %q", cfg.Auth.IssuerURL)
	}
}

func TestConfig_TracingEnabled_AndTelemetryServiceName(t *testing.T) {
	t.Parallel()
	cfg := &Config{}
	if cfg.TracingEnabled() {
		t.Fatal("expected tracing off")
	}
	if cfg.TelemetryServiceName() != "router-management-service" {
		t.Fatalf("default service name: %q", cfg.TelemetryServiceName())
	}
	cfg.Telemetry.OTLPTracesEndpoint = " http://x "
	if !cfg.TracingEnabled() {
		t.Fatal("expected tracing on")
	}
	cfg.Telemetry.ServiceName = "  custom  "
	if cfg.TelemetryServiceName() != "custom" {
		t.Fatalf("TelemetryServiceName: %q", cfg.TelemetryServiceName())
	}
}

func TestLoad_ConfigFileMissing(t *testing.T) {
	clearEnvForLoad(t)
	t.Setenv("CONFIG_PATH", "/nonexistent/router-management-config-xyz.yaml")
	_, err := Load()
	if err == nil {
		t.Fatal("expected error")
	}
}

func TestLoad_InvalidYAML(t *testing.T) {
	clearEnvForLoad(t)
	dir := t.TempDir()
	badPath := filepath.Join(dir, "bad.yaml")
	if err := os.WriteFile(badPath, []byte("server: [\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("CONFIG_PATH", badPath)
	_, err := Load()
	if err == nil {
		t.Fatal("expected parse error")
	}
}

func TestLoad_SchedulerDisabledEnv_AndSentTimeoutIgnored(t *testing.T) {
	clearEnvForLoad(t)
	t.Setenv("SCHEDULER_ENABLED", "off")
	t.Setenv("SENT_TIMEOUT_MINUTES", "0")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Scheduler.Enabled {
		t.Fatal("scheduler should be off")
	}
	if cfg.Scheduler.SentTimeoutMinutes != 10 {
		t.Fatalf("SentTimeoutMinutes: got %d want default 10", cfg.Scheduler.SentTimeoutMinutes)
	}
}

func TestLoad_OTELEnv(t *testing.T) {
	clearEnvForLoad(t)
	t.Setenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://collector:4318/v1/traces")
	t.Setenv("OTEL_SERVICE_NAME", "rms-test")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Telemetry.OTLPTracesEndpoint != "http://collector:4318/v1/traces" {
		t.Fatalf("OTLP endpoint: %q", cfg.Telemetry.OTLPTracesEndpoint)
	}
	if cfg.Telemetry.ServiceName != "rms-test" {
		t.Fatalf("service name: %q", cfg.Telemetry.ServiceName)
	}
}

