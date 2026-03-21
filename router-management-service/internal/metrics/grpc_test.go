package metrics

import (
	"testing"
	"time"
)

func TestRecordGRPC(t *testing.T) {
	t.Parallel()
	RecordGRPC("TestRPC", true, 10*time.Millisecond)
	RecordGRPC("TestRPC", false, 5*time.Millisecond)
}
