package migrate

import (
	"context"
	"errors"
	"log/slog"
	"testing"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/require"
)

type stubSQLPool struct {
	execErr error
	sqls    []string
}

func (s *stubSQLPool) Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error) {
	if s.execErr != nil {
		return pgconn.CommandTag{}, s.execErr
	}
	s.sqls = append(s.sqls, sql)
	return pgconn.CommandTag{}, nil
}

func TestEmbeddedMigrationFilesReadable(t *testing.T) {
	t.Parallel()
	for _, name := range migrationFileNames {
		b, err := fs.ReadFile(name)
		require.NoError(t, err, name)
		require.NotEmpty(t, b, name)
	}
}

func TestRun_AppliesMigrationsAndLogs(t *testing.T) {
	t.Parallel()
	ctx := context.Background()
	p := &stubSQLPool{}
	log := slog.Default()
	require.NoError(t, Run(ctx, p, log))
	require.Len(t, p.sqls, len(migrationFileNames))
	require.Contains(t, p.sqls[0], "CREATE TABLE")
}

func TestRun_NilLogger(t *testing.T) {
	t.Parallel()
	ctx := context.Background()
	p := &stubSQLPool{}
	require.NoError(t, Run(ctx, p, nil))
	require.Len(t, p.sqls, len(migrationFileNames))
}

func TestRun_ExecError(t *testing.T) {
	t.Parallel()
	ctx := context.Background()
	p := &stubSQLPool{execErr: errors.New("exec failed")}
	require.Error(t, Run(ctx, p, nil))
}
