package repository

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

func TestMockRouterStore_UpdateLastSeen(t *testing.T) {
	ctx := context.Background()
	routerID := uuid.New()
	at := time.Now()

	// Case 1: UpdateLastSeenFunc is nil => should still succeed.
	{
		store := &MockRouterStore{}
		require.NoError(t, store.UpdateLastSeen(ctx, routerID, at))
	}

	// Case 2: UpdateLastSeenFunc is set => should call the func.
	{
		store := &MockRouterStore{
			UpdateLastSeenFunc: func(ctx context.Context, id uuid.UUID, atArg time.Time) error {
				require.Equal(t, routerID, id)
				require.Equal(t, at, atArg)
				return nil
			},
		}
		require.NoError(t, store.UpdateLastSeen(ctx, routerID, at))
	}
}

func TestMockCommandStore_MarkSent(t *testing.T) {
	ctx := context.Background()
	now := time.Now()
	ids := []uuid.UUID{uuid.New(), uuid.New()}

	// Case 1: MarkSentFunc is nil => should still succeed.
	{
		store := &MockCommandStore{}
		require.NoError(t, store.MarkSent(ctx, ids, now))
	}

	// Case 2: MarkSentFunc is set => should call the func.
	{
		called := false
		store := &MockCommandStore{
			MarkSentFunc: func(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error {
				called = true
				require.Len(t, commandIDs, len(ids))
				require.Equal(t, ids[0], commandIDs[0])
				require.Equal(t, ids[1], commandIDs[1])
				require.Equal(t, now, sentAt)
				return nil
			},
		}
		require.NoError(t, store.MarkSent(ctx, ids, now))
		require.True(t, called, "expected MarkSentFunc to be called")
	}
}

func TestMockRouterStore_DefaultAndCustomFuncs(t *testing.T) {
	ctx := context.Background()
	id := uuid.New()

	t.Run("CreateRouter", func(t *testing.T) {
		called := false
		s := &MockRouterStore{
			CreateRouterFunc: func(ctx context.Context, r *Router) error {
				called = true
				return nil
			},
		}
		require.NoError(t, s.CreateRouter(ctx, &Router{ID: id}))
		require.True(t, called)
		require.NoError(t, (&MockRouterStore{}).CreateRouter(ctx, &Router{}))
	})
	t.Run("ListRouterIDs", func(t *testing.T) {
		ids := []uuid.UUID{uuid.New()}
		s := &MockRouterStore{
			ListRouterIDsFunc: func(ctx context.Context) ([]uuid.UUID, error) {
				return ids, nil
			},
		}
		out, err := s.ListRouterIDs(ctx)
		require.NoError(t, err)
		require.Equal(t, ids, out)
		out2, err := (&MockRouterStore{}).ListRouterIDs(ctx)
		require.NoError(t, err)
		require.Nil(t, out2)
	})
	t.Run("GetByID", func(t *testing.T) {
		r := &Router{ID: id}
		s := &MockRouterStore{
			GetByIDFunc: func(ctx context.Context, u uuid.UUID) (*Router, error) {
				return r, nil
			},
		}
		got, err := s.GetByID(ctx, id)
		require.NoError(t, err)
		require.Equal(t, r, got)
	})
}

func TestMockCommandStore_DefaultAndCustomFuncs(t *testing.T) {
	ctx := context.Background()
	id := uuid.New()
	cmd := &Command{ID: id, RouterID: id}

	t.Run("Create", func(t *testing.T) {
		called := false
		s := &MockCommandStore{
			CreateFunc: func(ctx context.Context, c *Command) error {
				called = true
				return errors.New("fail")
			},
		}
		require.Error(t, s.Create(ctx, cmd))
		require.True(t, called)
		require.NoError(t, (&MockCommandStore{}).Create(ctx, cmd))
	})
	t.Run("CreateMany default uses Create", func(t *testing.T) {
		var seen []uuid.UUID
		c1 := &Command{ID: uuid.New(), RouterID: id}
		c2 := &Command{ID: uuid.New(), RouterID: id}
		s := &MockCommandStore{
			CreateFunc: func(ctx context.Context, c *Command) error {
				seen = append(seen, c.ID)
				return nil
			},
		}
		require.NoError(t, s.CreateMany(ctx, []*Command{c1, c2}))
		require.Len(t, seen, 2)
		require.Contains(t, seen, c1.ID)
		require.Contains(t, seen, c2.ID)
		require.NoError(t, (&MockCommandStore{}).CreateMany(ctx, nil))
	})
	t.Run("CreateMany custom", func(t *testing.T) {
		s := &MockCommandStore{
			CreateManyFunc: func(ctx context.Context, cmds []*Command) error {
				return errors.New("e")
			},
		}
		require.Error(t, s.CreateMany(ctx, []*Command{cmd}))
	})
	t.Run("CreatePendingForAllRouters default empty", func(t *testing.T) {
		s := &MockCommandStore{}
		got, err := s.CreatePendingForAllRouters(ctx, "PING", "")
		require.NoError(t, err)
		require.Empty(t, got)
	})
	t.Run("CreatePendingForAllRouters custom", func(t *testing.T) {
		exp := []uuid.UUID{uuid.New()}
		s := &MockCommandStore{
			CreatePendingForAllRoutersFunc: func(ctx context.Context, ct, pl string) ([]uuid.UUID, error) {
				require.Equal(t, "X", ct)
				return exp, nil
			},
		}
		got, err := s.CreatePendingForAllRouters(ctx, "X", "{}")
		require.NoError(t, err)
		require.Equal(t, exp, got)
	})
	t.Run("ListPendingOrSentByRouter", func(t *testing.T) {
		list := []*Command{cmd}
		s := &MockCommandStore{
			ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*Command, error) {
				return list, nil
			},
		}
		got, err := s.ListPendingOrSentByRouter(ctx, id)
		require.NoError(t, err)
		require.Equal(t, list, got)
	})
	t.Run("Ack", func(t *testing.T) {
		now := time.Now()
		s := &MockCommandStore{
			AckFunc: func(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error {
				require.Equal(t, id, commandID)
				return nil
			},
		}
		require.NoError(t, s.Ack(ctx, id, id, now))
		require.NoError(t, (&MockCommandStore{}).Ack(ctx, id, id, now))
	})
	t.Run("FailSentBefore", func(t *testing.T) {
		before := time.Now()
		s := &MockCommandStore{
			FailSentBeforeFunc: func(ctx context.Context, t time.Time) ([]*Command, error) {
				return []*Command{cmd}, nil
			},
		}
		got, err := s.FailSentBefore(ctx, before)
		require.NoError(t, err)
		require.Len(t, got, 1)
		got2, err := (&MockCommandStore{}).FailSentBefore(ctx, before)
		require.NoError(t, err)
		require.Nil(t, got2)
	})
}

