// Mock-реализации RouterStore и CommandStore для unit-тестов (internal/service): можно подставлять свои функции или использовать значения по умолчанию.
package repository

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// MockRouterStore — заглушка RouterStore; каждому методу соответствует опциональная функция (если nil — возвращается значение по умолчанию).
type MockRouterStore struct {
	CreateRouterFunc   func(ctx context.Context, r *Router) error
	UpdateLastSeenFunc func(ctx context.Context, routerID uuid.UUID, at time.Time) error
	ListRouterIDsFunc  func(ctx context.Context) ([]uuid.UUID, error)
	GetByIDFunc        func(ctx context.Context, id uuid.UUID) (*Router, error)
}

// CreateRouter вызывает CreateRouterFunc или возвращает nil (успех).
func (m *MockRouterStore) CreateRouter(ctx context.Context, r *Router) error {
	if m.CreateRouterFunc != nil {
		return m.CreateRouterFunc(ctx, r)
	}
	return nil
}

// UpdateLastSeen вызывает UpdateLastSeenFunc или возвращает nil.
func (m *MockRouterStore) UpdateLastSeen(ctx context.Context, routerID uuid.UUID, at time.Time) error {
	if m.UpdateLastSeenFunc != nil {
		return m.UpdateLastSeenFunc(ctx, routerID, at)
	}
	return nil
}

// ListRouterIDs вызывает ListRouterIDsFunc или возвращает nil, nil.
func (m *MockRouterStore) ListRouterIDs(ctx context.Context) ([]uuid.UUID, error) {
	if m.ListRouterIDsFunc != nil {
		return m.ListRouterIDsFunc(ctx)
	}
	return nil, nil
}

// GetByID вызывает GetByIDFunc или возвращает nil, nil (роутер не найден).
func (m *MockRouterStore) GetByID(ctx context.Context, id uuid.UUID) (*Router, error) {
	if m.GetByIDFunc != nil {
		return m.GetByIDFunc(ctx, id)
	}
	return nil, nil
}

// MockCommandStore — заглушка CommandStore для тестов; при nil-функции методы возвращают успех или пустой результат.
type MockCommandStore struct {
	CreateFunc                    func(ctx context.Context, cmd *Command) error
	CreateManyFunc                      func(ctx context.Context, cmds []*Command) error
	CreatePendingForAllRoutersFunc      func(ctx context.Context, commandType, payload string) ([]uuid.UUID, error)
	ListPendingOrSentByRouterFunc func(ctx context.Context, routerID uuid.UUID) ([]*Command, error)
	MarkSentFunc                  func(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error
	AckFunc                       func(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error
	FailSentBeforeFunc            func(ctx context.Context, before time.Time) ([]*Command, error)
}

// Create вызывает CreateFunc или возвращает nil.
func (m *MockCommandStore) Create(ctx context.Context, cmd *Command) error {
	if m.CreateFunc != nil {
		return m.CreateFunc(ctx, cmd)
	}
	return nil
}

// CreateMany вызывает CreateManyFunc или последовательно Create для каждой команды.
func (m *MockCommandStore) CreateMany(ctx context.Context, cmds []*Command) error {
	if m.CreateManyFunc != nil {
		return m.CreateManyFunc(ctx, cmds)
	}
	for _, c := range cmds {
		if err := m.Create(ctx, c); err != nil {
			return err
		}
	}
	return nil
}

// CreatePendingForAllRouters вызывает CreatePendingForAllRoutersFunc или возвращает пустой слайс (без рассылки).
func (m *MockCommandStore) CreatePendingForAllRouters(ctx context.Context, commandType, payload string) ([]uuid.UUID, error) {
	if m.CreatePendingForAllRoutersFunc != nil {
		return m.CreatePendingForAllRoutersFunc(ctx, commandType, payload)
	}
	return []uuid.UUID{}, nil
}

// ListPendingOrSentByRouter вызывает ListPendingOrSentByRouterFunc или возвращает nil, nil.
func (m *MockCommandStore) ListPendingOrSentByRouter(ctx context.Context, routerID uuid.UUID) ([]*Command, error) {
	if m.ListPendingOrSentByRouterFunc != nil {
		return m.ListPendingOrSentByRouterFunc(ctx, routerID)
	}
	return nil, nil
}

// MarkSent вызывает MarkSentFunc или возвращает nil.
func (m *MockCommandStore) MarkSent(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error {
	if m.MarkSentFunc != nil {
		return m.MarkSentFunc(ctx, commandIDs, sentAt)
	}
	return nil
}

// Ack вызывает AckFunc или возвращает nil.
func (m *MockCommandStore) Ack(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error {
	if m.AckFunc != nil {
		return m.AckFunc(ctx, commandID, routerID, ackedAt)
	}
	return nil
}

// FailSentBefore вызывает FailSentBeforeFunc или возвращает nil, nil.
func (m *MockCommandStore) FailSentBefore(ctx context.Context, before time.Time) ([]*Command, error) {
	if m.FailSentBeforeFunc != nil {
		return m.FailSentBeforeFunc(ctx, before)
	}
	return nil, nil
}
