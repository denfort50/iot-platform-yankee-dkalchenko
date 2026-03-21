package repository

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/require"
)

func TestPostgresRouterStore_EnsureRouter_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	rid := uuid.New()
	created := time.Date(2025, 1, 2, 3, 4, 5, 0, time.UTC)
	ls := time.Date(2025, 1, 3, 0, 0, 0, 0, time.UTC)
	rows := pgxmock.NewRows([]string{"id", "serial_number", "last_seen_at", "created_at"}).
		AddRow(rid, rid.String(), &ls, created)

	mock.ExpectQuery(`INSERT INTO routers`).
		WithArgs(rid, rid.String()).
		WillReturnRows(rows)

	s := &PostgresRouterStore{pool: mock}
	got, err := s.EnsureRouter(context.Background(), rid, "")
	require.NoError(t, err)
	require.Equal(t, rid, got.ID)
	require.NotNil(t, got.LastSeenAt)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_UpdateLastSeen_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	rid := uuid.New()
	at := time.Now()
	mock.ExpectExec(`UPDATE routers SET last_seen_at`).
		WithArgs(rid, at).
		WillReturnResult(pgxmock.NewResult("UPDATE", 1))

	s := &PostgresRouterStore{pool: mock}
	require.NoError(t, s.UpdateLastSeen(context.Background(), rid, at))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_ListRouterIDs_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	a, b := uuid.New(), uuid.New()
	rows := pgxmock.NewRows([]string{"id"}).AddRow(a).AddRow(b)
	mock.ExpectQuery(`SELECT id FROM routers`).WillReturnRows(rows)

	s := &PostgresRouterStore{pool: mock}
	ids, err := s.ListRouterIDs(context.Background())
	require.NoError(t, err)
	require.Equal(t, []uuid.UUID{a, b}, ids)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_GetByID_PgxMock(t *testing.T) {
	t.Parallel()
	t.Run("no rows", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		rid := uuid.New()
		mock.ExpectQuery(`SELECT id, serial_number, inventory_number`).
			WithArgs(rid).
			WillReturnError(pgx.ErrNoRows)
		s := &PostgresRouterStore{pool: mock}
		got, err := s.GetByID(context.Background(), rid)
		require.NoError(t, err)
		require.Nil(t, got)
		require.NoError(t, mock.ExpectationsWereMet())
	})
	t.Run("found", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		rid := uuid.New()
		inv, name, model, loc := "inv1", "n", "m", "l"
		created := time.Now()
		rows := pgxmock.NewRows([]string{
			"id", "serial_number", "inventory_number", "name", "model", "location", "last_seen_at", "created_at",
		}).AddRow(rid, "sn", &inv, &name, &model, &loc, nil, created)
		mock.ExpectQuery(`SELECT id, serial_number, inventory_number`).
			WithArgs(rid).
			WillReturnRows(rows)
		s := &PostgresRouterStore{pool: mock}
		got, err := s.GetByID(context.Background(), rid)
		require.NoError(t, err)
		require.Equal(t, inv, got.InventoryNumber)
		require.Equal(t, name, got.Name)
		require.Equal(t, model, got.Model)
		require.Equal(t, loc, got.Location)
		require.NoError(t, mock.ExpectationsWereMet())
	})
	t.Run("db error", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		rid := uuid.New()
		mock.ExpectQuery(`SELECT id, serial_number, inventory_number`).
			WithArgs(rid).
			WillReturnError(errors.New("connection failed"))
		s := &PostgresRouterStore{pool: mock}
		_, err = s.GetByID(context.Background(), rid)
		require.Error(t, err)
		require.Contains(t, err.Error(), "get router")
		require.NoError(t, mock.ExpectationsWereMet())
	})
}

func TestPostgresRouterStore_CreateRouter_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	r := &Router{
		ID: uuid.New(), SerialNumber: "", InventoryNumber: "inv", Name: "nm", Model: "md", Location: "loc",
	}
	mock.ExpectExec(`INSERT INTO routers`).
		WithArgs(r.ID, "inv", r.InventoryNumber, r.Name, r.Model, r.Location).
		WillReturnResult(pgxmock.NewResult("INSERT", 1))

	s := &PostgresRouterStore{pool: mock}
	require.NoError(t, s.CreateRouter(context.Background(), r))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_Create_PgxMock(t *testing.T) {
	t.Parallel()
	t.Run("empty payload", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		cmd := &Command{ID: uuid.New(), RouterID: uuid.New(), CommandType: "PING", Payload: "", Status: StatusPending}
		mock.ExpectExec(`INSERT INTO commands`).
			WithArgs(cmd.ID, cmd.RouterID, cmd.CommandType, nil, cmd.Status).
			WillReturnResult(pgxmock.NewResult("INSERT", 1))
		s := &PostgresCommandStore{pool: mock}
		require.NoError(t, s.Create(context.Background(), cmd))
		require.NoError(t, mock.ExpectationsWereMet())
	})
	t.Run("json payload", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		cmd := &Command{ID: uuid.New(), RouterID: uuid.New(), CommandType: "X", Payload: `{"a":1}`, Status: StatusPending}
		mock.ExpectExec(`INSERT INTO commands`).
			WithArgs(cmd.ID, cmd.RouterID, cmd.CommandType, cmd.Payload, cmd.Status).
			WillReturnResult(pgxmock.NewResult("INSERT", 1))
		s := &PostgresCommandStore{pool: mock}
		require.NoError(t, s.Create(context.Background(), cmd))
		require.NoError(t, mock.ExpectationsWereMet())
	})
	t.Run("non json payload marshalled", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		cmd := &Command{ID: uuid.New(), RouterID: uuid.New(), CommandType: "X", Payload: "plain", Status: StatusPending}
		mock.ExpectExec(`INSERT INTO commands`).
			WithArgs(cmd.ID, cmd.RouterID, cmd.CommandType, `"plain"`, cmd.Status).
			WillReturnResult(pgxmock.NewResult("INSERT", 1))
		s := &PostgresCommandStore{pool: mock}
		require.NoError(t, s.Create(context.Background(), cmd))
		require.NoError(t, mock.ExpectationsWereMet())
	})
}

func TestPostgresCommandStore_ListPendingOrSentByRouter_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	rid := uuid.New()
	cid := uuid.New()
	sent := time.Now()
	payload := "{}"
	rows := pgxmock.NewRows([]string{
		"id", "router_id", "command_type", "payload", "status", "sent_at", "acked_at", "created_at",
	}).AddRow(cid, rid, "PING", &payload, StatusSent, &sent, nil, sent)

	mock.ExpectQuery(`SELECT id, router_id, command_type, payload::text`).
		WithArgs(rid).
		WillReturnRows(rows)

	s := &PostgresCommandStore{pool: mock}
	list, err := s.ListPendingOrSentByRouter(context.Background(), rid)
	require.NoError(t, err)
	require.Len(t, list, 1)
	require.Equal(t, payload, list[0].Payload)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_MarkSent_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	s := &PostgresCommandStore{pool: mock}
	require.NoError(t, s.MarkSent(context.Background(), nil, time.Now()))
	require.NoError(t, mock.ExpectationsWereMet())

	id := uuid.New()
	at := time.Now()
	mock.ExpectExec(`UPDATE commands SET status`).
		WithArgs([]uuid.UUID{id}, StatusSent, at).
		WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkSent(context.Background(), []uuid.UUID{id}, at))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_Ack_PgxMock(t *testing.T) {
	t.Parallel()
	t.Run("no row updated", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		cid, rid := uuid.New(), uuid.New()
		at := time.Now()
		mock.ExpectExec(`UPDATE commands SET status`).
			WithArgs(cid, rid, at, StatusAcked).
			WillReturnResult(pgxmock.NewResult("UPDATE", 0))
		s := &PostgresCommandStore{pool: mock}
		require.Error(t, s.Ack(context.Background(), cid, rid, at))
		require.NoError(t, mock.ExpectationsWereMet())
	})
	t.Run("ok", func(t *testing.T) {
		t.Parallel()
		mock, err := pgxmock.NewPool()
		require.NoError(t, err)
		t.Cleanup(func() { mock.Close() })
		cid, rid := uuid.New(), uuid.New()
		at := time.Now()
		mock.ExpectExec(`UPDATE commands SET status`).
			WithArgs(cid, rid, at, StatusAcked).
			WillReturnResult(pgxmock.NewResult("UPDATE", 1))
		s := &PostgresCommandStore{pool: mock}
		require.NoError(t, s.Ack(context.Background(), cid, rid, at))
		require.NoError(t, mock.ExpectationsWereMet())
	})
}

func TestPostgresRouterStore_EnsureRouter_PgxMock_QueryError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	rid := uuid.New()
	mock.ExpectQuery(`INSERT INTO routers`).
		WithArgs(rid, rid.String()).
		WillReturnError(errors.New("db"))
	s := &PostgresRouterStore{pool: mock}
	_, err = s.EnsureRouter(context.Background(), rid, "")
	require.Error(t, err)
	require.Contains(t, err.Error(), "ensure router")
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_ListRouterIDs_PgxMock_QueryError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	mock.ExpectQuery(`SELECT id FROM routers`).WillReturnError(errors.New("db"))
	s := &PostgresRouterStore{pool: mock}
	_, err = s.ListRouterIDs(context.Background())
	require.Error(t, err)
	require.Contains(t, err.Error(), "list routers")
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_CreateRouter_PgxMock_ExecError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	r := &Router{ID: uuid.New(), SerialNumber: "s", Name: "n"}
	mock.ExpectExec(`INSERT INTO routers`).
		WithArgs(r.ID, r.SerialNumber, r.InventoryNumber, r.Name, r.Model, r.Location).
		WillReturnError(errors.New("db"))
	s := &PostgresRouterStore{pool: mock}
	require.Error(t, s.CreateRouter(context.Background(), r))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresRouterStore_CreateRouter_UsesIDAsSerial(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	rid := uuid.New()
	r := &Router{ID: rid, SerialNumber: "", InventoryNumber: ""}
	mock.ExpectExec(`INSERT INTO routers`).
		WithArgs(rid, rid.String(), r.InventoryNumber, r.Name, r.Model, r.Location).
		WillReturnResult(pgxmock.NewResult("INSERT", 1))
	s := &PostgresRouterStore{pool: mock}
	require.NoError(t, s.CreateRouter(context.Background(), r))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_Create_PgxMock_ExecError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	cmd := &Command{ID: uuid.New(), RouterID: uuid.New(), CommandType: "PING", Payload: "", Status: StatusPending}
	mock.ExpectExec(`INSERT INTO commands`).
		WithArgs(cmd.ID, cmd.RouterID, cmd.CommandType, nil, cmd.Status).
		WillReturnError(errors.New("db"))
	s := &PostgresCommandStore{pool: mock}
	require.Error(t, s.Create(context.Background(), cmd))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_ListPendingOrSentByRouter_PgxMock_QueryError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	rid := uuid.New()
	mock.ExpectQuery(`SELECT id, router_id, command_type, payload::text`).
		WithArgs(rid).
		WillReturnError(errors.New("db"))
	s := &PostgresCommandStore{pool: mock}
	_, err = s.ListPendingOrSentByRouter(context.Background(), rid)
	require.Error(t, err)
	require.Contains(t, err.Error(), "list commands")
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_MarkSent_PgxMock_ExecError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	id := uuid.New()
	at := time.Now()
	mock.ExpectExec(`UPDATE commands SET status`).
		WithArgs([]uuid.UUID{id}, StatusSent, at).
		WillReturnError(errors.New("db"))
	s := &PostgresCommandStore{pool: mock}
	require.Error(t, s.MarkSent(context.Background(), []uuid.UUID{id}, at))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_Ack_PgxMock_ExecError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	cid, rid := uuid.New(), uuid.New()
	at := time.Now()
	mock.ExpectExec(`UPDATE commands SET status`).
		WithArgs(cid, rid, at, StatusAcked).
		WillReturnError(errors.New("db"))
	s := &PostgresCommandStore{pool: mock}
	require.Error(t, s.Ack(context.Background(), cid, rid, at))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_FailSentBefore_PgxMock_QueryError(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })
	before := time.Now()
	mock.ExpectQuery(`UPDATE commands`).
		WithArgs(before, StatusFailed).
		WillReturnError(errors.New("db"))
	s := &PostgresCommandStore{pool: mock}
	_, err = s.FailSentBefore(context.Background(), before)
	require.Error(t, err)
	require.Contains(t, err.Error(), "fail sent before")
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestPostgresCommandStore_FailSentBefore_PgxMock(t *testing.T) {
	t.Parallel()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(func() { mock.Close() })

	before := time.Now()
	cid, rid := uuid.New(), uuid.New()
	sent := before.Add(-time.Hour)
	pl := "null"
	rows := pgxmock.NewRows([]string{
		"id", "router_id", "command_type", "payload", "status", "sent_at", "acked_at", "created_at",
	}).AddRow(cid, rid, "PING", &pl, StatusFailed, &sent, nil, sent)

	mock.ExpectQuery(`UPDATE commands`).
		WithArgs(before, StatusFailed).
		WillReturnRows(rows)

	s := &PostgresCommandStore{pool: mock}
	got, err := s.FailSentBefore(context.Background(), before)
	require.NoError(t, err)
	require.Len(t, got, 1)
	require.Equal(t, StatusFailed, got[0].Status)
	require.NoError(t, mock.ExpectationsWereMet())
}
