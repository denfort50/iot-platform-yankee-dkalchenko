//go:build integration

package repository

import (
	"context"
	"log/slog"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"

	"router-management-service/internal/migrate"
)

const (
	pgImage   = "postgres:17-alpine"
	pgDB      = "router_management"
	pgUser    = "postgres"
	pgPass    = "postgres"
)

func setupPostgres(t *testing.T) (*pgxpool.Pool, func()) {
	t.Helper()
	ctx := context.Background()

	pgCtr, err := postgres.Run(ctx,
		pgImage,
		postgres.WithDatabase(pgDB),
		postgres.WithUsername(pgUser),
		postgres.WithPassword(pgPass),
		postgres.BasicWaitStrategies(),
	)
	require.NoError(t, err)

	cleanup := func() {
		_ = pgCtr.Terminate(ctx)
	}

	connStr, err := pgCtr.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)

	pool, err := pgxpool.New(ctx, connStr)
	require.NoError(t, err)
	require.NoError(t, pool.Ping(ctx))

	require.NoError(t, migrate.Run(ctx, pool, slog.Default()))

	return pool, func() {
		pool.Close()
		cleanup()
	}
}

func TestPostgresRouterStore_UpdateLastSeen(t *testing.T) {
	ctx := context.Background()
	pool, cleanup := setupPostgres(t)
	defer cleanup()

	store := NewPostgresRouterStore(pool)

	routerID := uuid.New()
	serial := "sn-" + routerID.String()

	_, err := pool.Exec(ctx,
		`INSERT INTO routers (id, serial_number, inventory_number, name, model, location)
		 VALUES ($1, $2, $3, $4, $5, $6)`,
		routerID, serial, "INV", "R1", "Model", "Loc",
	)
	require.NoError(t, err)

	at := time.Now().UTC().Truncate(time.Microsecond)
	require.NoError(t, store.UpdateLastSeen(ctx, routerID, at))

	var lastSeen *time.Time
	err = pool.QueryRow(ctx, `SELECT last_seen_at FROM routers WHERE id = $1`, routerID).Scan(&lastSeen)
	require.NoError(t, err)
	require.NotNil(t, lastSeen)

	// В PostgreSQL TIMESTAMP точность может быть чуть усечена, поэтому сравниваем с допуском.
	require.WithinDuration(t, at, *lastSeen, time.Second)
}

func TestPostgresCommandStore_FailSentBefore(t *testing.T) {
	ctx := context.Background()
	pool, cleanup := setupPostgres(t)
	defer cleanup()

	routerID := uuid.New()
	serial := "sn-" + routerID.String()

	_, err := pool.Exec(ctx,
		`INSERT INTO routers (id, serial_number, inventory_number, name, model, location)
		 VALUES ($1, $2, $3, $4, $5, $6)`,
		routerID, serial, "INV", "R1", "Model", "Loc",
	)
	require.NoError(t, err)

	store := NewPostgresCommandStore(pool)

	cmdShouldFail := uuid.New()
	cmdShouldNotFail := uuid.New()
	cmdPending := uuid.New()

	now := time.Now().UTC().Truncate(time.Microsecond)
	cutoff := now.Add(-30 * time.Minute)
	sentFailAt := cutoff.Add(-10 * time.Minute)
	sentNotFailAt := cutoff.Add(10 * time.Minute)

	// 1) SENT с sent_at < cutoff => FAILED
	_, err = pool.Exec(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status, sent_at, acked_at)
		 VALUES ($1, $2, $3, $4::jsonb, $5, $6, NULL)`,
		cmdShouldFail, routerID, "REBOOT", `{"delay": 1}`, StatusSent, sentFailAt,
	)
	require.NoError(t, err)

	// 2) SENT с sent_at >= cutoff => остаётся SENT
	_, err = pool.Exec(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status, sent_at, acked_at)
		 VALUES ($1, $2, $3, $4::jsonb, $5, $6, NULL)`,
		cmdShouldNotFail, routerID, "REBOOT", `{"delay": 2}`, StatusSent, sentNotFailAt,
	)
	require.NoError(t, err)

	// 3) PENDING => не трогаем
	_, err = pool.Exec(ctx,
		`INSERT INTO commands (id, router_id, command_type, payload, status, sent_at, acked_at)
		 VALUES ($1, $2, $3, $4::jsonb, $5, $6, NULL)`,
		cmdPending, routerID, "REBOOT", `{"delay": 3}`, StatusPending, sentFailAt,
	)
	require.NoError(t, err)

	updated, err := store.FailSentBefore(ctx, cutoff)
	require.NoError(t, err)
	require.Len(t, updated, 1)
	require.Equal(t, cmdShouldFail, updated[0].ID)
	require.Equal(t, StatusFailed, updated[0].Status)
	require.NotNil(t, updated[0].SentAt)
	require.WithinDuration(t, sentFailAt, *updated[0].SentAt, time.Second)

	// Проверяем, что второй SENT команда не была изменена.
	var status2 string
	err = pool.QueryRow(ctx, `SELECT status FROM commands WHERE id = $1`, cmdShouldNotFail).Scan(&status2)
	require.NoError(t, err)
	require.Equal(t, StatusSent, status2)
}

func TestPostgresCommandStore_CreatePendingForAllRouters(t *testing.T) {
	ctx := context.Background()
	pool, cleanup := setupPostgres(t)
	defer cleanup()

	r1, r2 := uuid.New(), uuid.New()
	inv1, inv2 := "INV-"+r1.String()[:8], "INV-"+r2.String()[:8]
	for _, tc := range []struct {
		id  uuid.UUID
		inv string
	}{
		{r1, inv1},
		{r2, inv2},
	} {
		_, err := pool.Exec(ctx,
			`INSERT INTO routers (id, serial_number, inventory_number, name, model, location)
			 VALUES ($1, $2, $3, $4, $5, $6)`,
			tc.id, "sn-"+tc.id.String(), tc.inv, "R", "M", "L",
		)
		require.NoError(t, err)
	}

	store := NewPostgresCommandStore(pool)
	ids, err := store.CreatePendingForAllRouters(ctx, "PING", `{"k":1}`)
	require.NoError(t, err)
	require.Len(t, ids, 2)

	var n int
	require.NoError(t, pool.QueryRow(ctx,
		`SELECT count(*) FROM commands WHERE router_id = ANY($1) AND command_type = 'PING' AND status = 'PENDING'`,
		[]uuid.UUID{r1, r2},
	).Scan(&n))
	require.Equal(t, 2, n)
}

func TestPostgresCommandStore_CreateMany_AtomicRollback(t *testing.T) {
	ctx := context.Background()
	pool, cleanup := setupPostgres(t)
	defer cleanup()

	routerOK := uuid.New()
	_, err := pool.Exec(ctx,
		`INSERT INTO routers (id, serial_number, inventory_number, name, model, location)
		 VALUES ($1, $2, $3, $4, $5, $6)`,
		routerOK, "sn-a", "INV-A", "R", "M", "L",
	)
	require.NoError(t, err)

	store := NewPostgresCommandStore(pool)
	badRouter := uuid.New()
	cmds := []*Command{
		{ID: uuid.New(), RouterID: routerOK, CommandType: "PING", Status: StatusPending},
		{ID: uuid.New(), RouterID: badRouter, CommandType: "PING", Status: StatusPending},
	}
	require.Error(t, store.CreateMany(ctx, cmds))

	var n int
	require.NoError(t, pool.QueryRow(ctx, `SELECT count(*) FROM commands WHERE id = ANY($1)`, []uuid.UUID{cmds[0].ID, cmds[1].ID}).Scan(&n))
	require.Zero(t, n, "partial insert must be rolled back")
}

