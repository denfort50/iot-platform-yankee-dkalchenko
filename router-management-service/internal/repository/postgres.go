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

// stringPayloadForJSONB преобразует строку payload для колонки JSONB (NULL при пустой строке).
func stringPayloadForJSONB(payload string) interface{} {
	if payload == "" {
		return nil
	}
	if json.Valid([]byte(payload)) {
		return payload
	}
	b, _ := json.Marshal(payload)
	return string(b)
}

func commandPayloadArg(cmd *Command) interface{} {
	return stringPayloadForJSONB(cmd.Payload)
}

// Create вставляет команду в таблицу commands; пустой payload сохраняется как NULL в JSONB.
func (s *PostgresCommandStore) Create(ctx context.Context, cmd *Command) error {
	_, err := s.pool.Exec(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status)
VALUES ($1, $2, $3, $4::jsonb, $5)`,
		cmd.ID, cmd.RouterID, cmd.CommandType, commandPayloadArg(cmd), cmd.Status,
	)
	if err != nil {
		return fmt.Errorf("create command: %w", err)
	}
	return nil
}

// CreateMany вставляет команды одной транзакцией (откат при любой ошибке).
func (s *PostgresCommandStore) CreateMany(ctx context.Context, cmds []*Command) error {
	if len(cmds) == 0 {
		return nil
	}
	beginner, ok := s.pool.(interface {
		Begin(ctx context.Context) (pgx.Tx, error)
	})
	if !ok {
		return fmt.Errorf("CreateMany requires a transaction-capable pool (e.g. *pgxpool.Pool)")
	}
	tx, err := beginner.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	insertSQL := `INSERT INTO commands (id, router_id, command_type, payload, status)
VALUES ($1, $2, $3, $4::jsonb, $5)`
	for _, cmd := range cmds {
		_, err := tx.Exec(ctx, insertSQL, cmd.ID, cmd.RouterID, cmd.CommandType, commandPayloadArg(cmd), cmd.Status)
		if err != nil {
			return fmt.Errorf("create command: %w", err)
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit: %w", err)
	}
	return nil
}

// CreatePendingForAllRouters вставляет по одной команде на каждый роутер одним INSERT...SELECT (снимок таблицы routers согласован с вставкой).
func (s *PostgresCommandStore) CreatePendingForAllRouters(ctx context.Context, commandType, payload string) ([]uuid.UUID, error) {
	payloadArg := stringPayloadForJSONB(payload)
	rows, err := s.pool.Query(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status)
SELECT gen_random_uuid(), r.id, $1, $2::jsonb, $3
FROM routers r
ORDER BY r.id
RETURNING id`,
		commandType, payloadArg, StatusPending,
	)
	if err != nil {
		return nil, fmt.Errorf("broadcast commands: %w", err)
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
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return ids, nil
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
