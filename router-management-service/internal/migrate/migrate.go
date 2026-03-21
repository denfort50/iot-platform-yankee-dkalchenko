// Package migrate — применение SQL-миграций к PostgreSQL при старте сервиса (таблицы routers, commands и индексы).
package migrate

import (
	"context"
	"embed"
	"log/slog"

	"github.com/jackc/pgx/v5/pgconn"
)

//go:embed *.sql
var fs embed.FS

// migrationFileNames — порядок применения SQL к БД при старте.
var migrationFileNames = []string{"001_init.sql"}

// SQLExecutor — минимум для применения миграций (реализуют *pgxpool.Pool и тестовые двойники).
type SQLExecutor interface {
	Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error)
}

// Run выполняет миграции из встроенных файлов (*.sql) в порядке списка names; при ошибке возвращает её, иначе логирует применённые файлы.
func Run(ctx context.Context, pool SQLExecutor, log *slog.Logger) error {
	names := migrationFileNames
	for _, name := range names {
		body, err := fs.ReadFile(name)
		if err != nil {
			return err
		}
		_, err = pool.Exec(ctx, string(body))
		if err != nil {
			return err
		}
		if log != nil {
			log.InfoContext(ctx, "migration applied", "file", name)
		}
	}
	return nil
}
