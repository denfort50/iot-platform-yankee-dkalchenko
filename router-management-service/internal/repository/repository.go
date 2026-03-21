// Package repository — доменные сущности (Router, Command), константы статусов команд и интерфейсы хранилищ (RouterStore, CommandStore).
package repository

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Router — сущность роутера: идентификатор, серийный/инвентарный номер, имя, модель, локация, время последнего опроса и создания.
type Router struct {
	ID              uuid.UUID  `json:"id"`
	SerialNumber    string     `json:"serial_number"`
	InventoryNumber string     `json:"inventory_number,omitempty"`
	Name            string     `json:"name,omitempty"`
	Model           string     `json:"model,omitempty"`
	Location        string     `json:"location,omitempty"`
	LastSeenAt      *time.Time `json:"last_seen_at,omitempty"`
	CreatedAt       time.Time  `json:"created_at"`
}

// Command — команда для роутера: id, router_id, тип (REBOOT, PING и т.д.), опциональный payload (JSON), статус и метки времени отправки/подтверждения.
type Command struct {
	ID          uuid.UUID  `json:"id"`
	RouterID    uuid.UUID  `json:"router_id"`
	CommandType string     `json:"command_type"`
	Payload     string     `json:"payload,omitempty"`
	Status      string     `json:"status"`
	SentAt      *time.Time `json:"sent_at,omitempty"`
	AckedAt     *time.Time `json:"acked_at,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
}

// Статусы команд в хранилище.
const (
	StatusPending = "PENDING" // создана, ещё не выдана роутеру
	StatusSent    = "SENT"    // выдана роутеру при опросе (CommandsPoll)
	StatusAcked   = "ACKED"   // роутер подтвердил выполнение (CommandsAck)
	StatusFailed  = "FAILED"  // зарезервировано на будущее
)

// RouterStore — интерфейс хранилища роутеров: создание/обновление, получение по ID, список ID, обновление last_seen_at.
type RouterStore interface {
	EnsureRouter(ctx context.Context, id uuid.UUID, serialNumber string) (*Router, error)
	CreateRouter(ctx context.Context, r *Router) error
	UpdateLastSeen(ctx context.Context, routerID uuid.UUID, at time.Time) error
	ListRouterIDs(ctx context.Context) ([]uuid.UUID, error)
	GetByID(ctx context.Context, id uuid.UUID) (*Router, error)
}

// CommandStore — интерфейс хранилища команд: создание, выборка PENDING/SENT по роутеру, пометка отправки и подтверждения.
type CommandStore interface {
	Create(ctx context.Context, cmd *Command) error
	ListPendingOrSentByRouter(ctx context.Context, routerID uuid.UUID) ([]*Command, error)
	MarkSent(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error
	Ack(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error
	// FailSentBefore помечает как FAILED все команды в статусе SENT с sent_at < before; возвращает список обновлённых команд.
	FailSentBefore(ctx context.Context, before time.Time) ([]*Command, error)
}
