// Package repository (postgres) — реализации RouterStore и CommandStore поверх PostgreSQL (pgxpool).
package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// pgxConn — подмножество *pgxpool.Pool (Exec/Query/QueryRow); тем же контрактом пользуется pgxmock в тестах.
type pgxConn interface {
	Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

// PostgresRouterStore — хранилище роутеров в таблице routers.
type PostgresRouterStore struct {
	pool pgxConn
}

// PostgresCommandStore — хранилище команд в таблице commands.
type PostgresCommandStore struct {
	pool pgxConn
}

// NewPostgresRouterStore создаёт реализацию RouterStore для PostgreSQL.
func NewPostgresRouterStore(pool *pgxpool.Pool) *PostgresRouterStore {
	return &PostgresRouterStore{pool: pool}
}

// NewPostgresCommandStore создаёт реализацию CommandStore для PostgreSQL.
func NewPostgresCommandStore(pool *pgxpool.Pool) *PostgresCommandStore {
	return &PostgresCommandStore{pool: pool}
}

// EnsureRouter вставляет роутера с id и serial_number или обновляет last_seen_at при конфликте по id; возвращает запись.
func (s *PostgresRouterStore) EnsureRouter(ctx context.Context, id uuid.UUID, serialNumber string) (*Router, error) {
	if serialNumber == "" {
		serialNumber = id.String()
	}
	query := `
INSERT INTO routers (id, serial_number, last_seen_at)
VALUES ($1, $2, now())
ON CONFLICT (id) DO UPDATE SET last_seen_at = now()
RETURNING id, serial_number, last_seen_at, created_at`
	var r Router
	var lastSeen *time.Time
	err := s.pool.QueryRow(ctx, query, id, serialNumber).Scan(&r.ID, &r.SerialNumber, &lastSeen, &r.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("ensure router: %w", err)
	}
	r.LastSeenAt = lastSeen
	return &r, nil
}

// UpdateLastSeen обновляет поле last_seen_at у роутера по id.
func (s *PostgresRouterStore) UpdateLastSeen(ctx context.Context, routerID uuid.UUID, at time.Time) error {
	_, err := s.pool.Exec(ctx, `UPDATE routers SET last_seen_at = $2 WHERE id = $1`, routerID, at)
	if err != nil {
		return fmt.Errorf("update last_seen: %w", err)
	}
	return nil
}

// ListRouterIDs возвращает список всех id роутеров из таблицы routers.
func (s *PostgresRouterStore) ListRouterIDs(ctx context.Context) ([]uuid.UUID, error) {
	rows, err := s.pool.Query(ctx, `SELECT id FROM routers`)
	if err != nil {
		return nil, fmt.Errorf("list routers: %w", err)
	}
	defer rows.Close()
	var ids []uuid.UUID
	for rows.Next() {
		var id uuid.UUID
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, rows.Err()
}

// GetByID возвращает роутера по id или nil, если не найден.
func (s *PostgresRouterStore) GetByID(ctx context.Context, id uuid.UUID) (*Router, error) {
	var r Router
	var lastSeen *time.Time
	var invNum, name, model, loc *string
	err := s.pool.QueryRow(ctx,
		`SELECT id, serial_number, inventory_number, name, model, location, last_seen_at, created_at FROM routers WHERE id = $1`,
		id,
	).Scan(&r.ID, &r.SerialNumber, &invNum, &name, &model, &loc, &lastSeen, &r.CreatedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("get router: %w", err)
	}
	if invNum != nil {
		r.InventoryNumber = *invNum
	}
	if name != nil {
		r.Name = *name
	}
	if model != nil {
		r.Model = *model
	}
	if loc != nil {
		r.Location = *loc
	}
	r.LastSeenAt = lastSeen
	return &r, nil
}

// CreateRouter вставляет нового роутера в таблицу routers (id, serial_number, inventory_number, name, model, location).
func (s *PostgresRouterStore) CreateRouter(ctx context.Context, r *Router) error {
	serialNumber := r.SerialNumber
	if serialNumber == "" {
		serialNumber = r.InventoryNumber
	}
	if serialNumber == "" {
		serialNumber = r.ID.String()
	}
	_, err := s.pool.Exec(ctx,
		`INSERT INTO routers (id, serial_number, inventory_number, name, model, location)
VALUES ($1, $2, NULLIF($3,''), NULLIF($4,''), NULLIF($5,''), NULLIF($6,''))`,
		r.ID, serialNumber, r.InventoryNumber, r.Name, r.Model, r.Location,
	)
	if err != nil {
		return fmt.Errorf("create router: %w", err)
	}
	return nil
}

// Create вставляет команду в таблицу commands; пустой payload сохраняется как NULL в JSONB.
func (s *PostgresCommandStore) Create(ctx context.Context, cmd *Command) error {
	// Пустая строка невалидна для JSONB; при пустом payload записываем NULL.
	// Non-JSON strings (e.g. "no content") are stored as JSON strings.
	var payloadArg interface{} = nil
	if cmd.Payload != "" {
		if json.Valid([]byte(cmd.Payload)) {
			payloadArg = cmd.Payload
		} else {
			b, _ := json.Marshal(cmd.Payload)
			payloadArg = string(b)
		}
	}
	_, err := s.pool.Exec(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status)
VALUES ($1, $2, $3, $4::jsonb, $5)`,
		cmd.ID, cmd.RouterID, cmd.CommandType, payloadArg, cmd.Status,
	)
	if err != nil {
		return fmt.Errorf("create command: %w", err)
	}
	return nil
}

// ListPendingOrSentByRouter возвращает команды роутера в статусах PENDING и SENT, отсортированные по created_at.
func (s *PostgresCommandStore) ListPendingOrSentByRouter(ctx context.Context, routerID uuid.UUID) ([]*Command, error) {
	rows, err := s.pool.Query(ctx,
		`SELECT id, router_id, command_type, payload::text, status, sent_at, acked_at, created_at
FROM commands
WHERE router_id = $1 AND status IN ('PENDING', 'SENT')
ORDER BY created_at`,
		routerID,
	)
	if err != nil {
		return nil, fmt.Errorf("list commands: %w", err)
	}
	defer rows.Close()
	return scanCommands(rows)
}

// scanCommands сканирует результат запроса в слайс *Command.
func scanCommands(rows pgx.Rows) ([]*Command, error) {
	var list []*Command
	for rows.Next() {
		var c Command
		var payload *string
		var sentAt, ackedAt *time.Time
		err := rows.Scan(&c.ID, &c.RouterID, &c.CommandType, &payload, &c.Status, &sentAt, &ackedAt, &c.CreatedAt)
		if err != nil {
			return nil, err
		}
		if payload != nil {
			c.Payload = *payload
		}
		c.SentAt = sentAt
		c.AckedAt = ackedAt
		list = append(list, &c)
	}
	return list, rows.Err()
}

// MarkSent переводит команды с указанными id из статуса PENDING в SENT и записывает sent_at.
func (s *PostgresCommandStore) MarkSent(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error {
	if len(commandIDs) == 0 {
		return nil
	}
	_, err := s.pool.Exec(ctx,
		`UPDATE commands SET status = $2, sent_at = $3 WHERE id = ANY($1) AND status = 'PENDING'`,
		commandIDs, StatusSent, sentAt,
	)
	if err != nil {
		return fmt.Errorf("mark sent: %w", err)
	}
	return nil
}

// Ack переводит команду в статус ACKED и записывает acked_at; команда должна принадлежать роутеру и быть в PENDING или SENT.
func (s *PostgresCommandStore) Ack(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error {
	result, err := s.pool.Exec(ctx,
		`UPDATE commands SET status = $4, acked_at = $3 WHERE id = $1 AND router_id = $2 AND status IN ('PENDING', 'SENT')`,
		commandID, routerID, ackedAt, StatusAcked,
	)
	if err != nil {
		return fmt.Errorf("ack: %w", err)
	}
	if result.RowsAffected() == 0 {
		return fmt.Errorf("command not found or already acked: id=%s router=%s", commandID, routerID)
	}
	return nil
}

// FailSentBefore помечает как FAILED все команды в статусе SENT, у которых sent_at меньше переданного момента времени,
// и возвращает список затронутых команд.
func (s *PostgresCommandStore) FailSentBefore(ctx context.Context, before time.Time) ([]*Command, error) {
	rows, err := s.pool.Query(ctx,
		`UPDATE commands
SET status = $2
WHERE status = 'SENT' AND sent_at IS NOT NULL AND sent_at < $1
RETURNING id, router_id, command_type, payload::text, status, sent_at, acked_at, created_at`,
		before, StatusFailed,
	)
	if err != nil {
		return nil, fmt.Errorf("fail sent before: %w", err)
	}
	defer rows.Close()
	commands, err := scanCommands(rows)
	if err != nil {
		return nil, fmt.Errorf("scan failed commands: %w", err)
	}
	return commands, nil
}
