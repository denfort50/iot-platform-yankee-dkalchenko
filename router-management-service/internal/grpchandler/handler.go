// Package grpchandler — gRPC-обработчики API управления роутерами (api.v1.RouterManagementService).
// Преобразует запросы/ответы protobuf и делегирует бизнес-логику в service.CommandService.
package grpchandler

import (
	"context"
	"log/slog"
	"time"

	"github.com/google/uuid"

	apiv1 "router-management-service/api/proto/api/v1"
	"router-management-service/internal/metrics"
	"router-management-service/internal/service"
)

// Server реализует apiv1.RouterManagementServiceServer: регистрация роутеров, отправка команд, опрос и подтверждение.
type Server struct {
	apiv1.UnimplementedRouterManagementServiceServer
	svc *service.CommandService
	log *slog.Logger
}

// NewServer создаёт gRPC-сервер с заданным CommandService и логгером.
func NewServer(svc *service.CommandService, log *slog.Logger) *Server {
	if log == nil {
		log = slog.Default()
	}
	return &Server{svc: svc, log: log}
}

// strPtr возвращает указатель на строку (для опциональных полей в ответах).
func strPtr(s string) *string { return &s }

// RegisterRouter регистрирует новый роутер по инвентарному номеру и имени; возвращает сгенерированный router_id или ошибку в ответе.
func (s *Server) RegisterRouter(ctx context.Context, req *apiv1.RegisterRouterRequest) (resp *apiv1.RegisterRouterResponse, err error) {
	start := time.Now()
	defer func() {
		ok := err == nil && resp != nil && resp.GetError() == ""
		metrics.RecordGRPC("RegisterRouter", ok, time.Since(start))
	}()
	id, regErr := s.svc.RegisterRouter(ctx,
		req.GetInventoryNumber(),
		req.GetName(),
		req.GetModel(),
		req.GetLocation(),
	)
	if regErr != nil {
		s.log.ErrorContext(ctx, "RegisterRouter failed", "err", regErr)
		return &apiv1.RegisterRouterResponse{Error: strPtr(regErr.Error())}, nil
	}
	return &apiv1.RegisterRouterResponse{RouterId: id.String()}, nil
}

// SendCommand создаёт команду для одного роутера (при указанном router_id) или для всех зарегистрированных (при пустом router_id); возвращает command_id/command_ids или ошибку.
func (s *Server) SendCommand(ctx context.Context, req *apiv1.SendCommandRequest) (resp *apiv1.SendCommandResponse, err error) {
	start := time.Now()
	defer func() {
		ok := err == nil && resp != nil && resp.GetError() == ""
		metrics.RecordGRPC("SendCommand", ok, time.Since(start))
	}()
	if req.GetRouterId() == "" {
		ids, sendErr := s.svc.SendCommandToAllRouters(ctx, req.GetCommandType(), req.GetPayload())
		if sendErr != nil {
			s.log.ErrorContext(ctx, "SendCommandToAllRouters failed", "err", sendErr)
			return &apiv1.SendCommandResponse{Error: strPtr(sendErr.Error())}, nil
		}
		out := &apiv1.SendCommandResponse{CommandIds: make([]string, len(ids))}
		for i, id := range ids {
			out.CommandIds[i] = id.String()
		}
		return out, nil
	}
	routerID, parseErr := uuid.Parse(req.GetRouterId())
	if parseErr != nil {
		return &apiv1.SendCommandResponse{Error: strPtr("invalid router_id: " + parseErr.Error())}, nil
	}
	id, sendErr := s.svc.SendCommand(ctx, routerID, req.GetCommandType(), req.GetPayload())
	if sendErr != nil {
		s.log.ErrorContext(ctx, "SendCommand failed", "err", sendErr)
		return &apiv1.SendCommandResponse{Error: strPtr(sendErr.Error())}, nil
	}
	return &apiv1.SendCommandResponse{CommandIds: []string{id.String()}}, nil
}

// CommandsPoll возвращает список команд роутера в статусах PENDING/SENT; при выдаче статус переводится в SENT, обновляется last_seen_at.
func (s *Server) CommandsPoll(ctx context.Context, req *apiv1.CommandsPollRequest) (resp *apiv1.CommandsPollResponse, err error) {
	start := time.Now()
	defer func() {
		ok := err == nil && resp != nil && resp.GetError() == ""
		metrics.RecordGRPC("CommandsPoll", ok, time.Since(start))
	}()
	routerID, parseErr := uuid.Parse(req.GetRouterId())
	if parseErr != nil {
		return &apiv1.CommandsPollResponse{Error: strPtr("invalid router_id: " + parseErr.Error())}, nil
	}
	commands, pollErr := s.svc.PollCommands(ctx, routerID)
	if pollErr != nil {
		s.log.ErrorContext(ctx, "CommandsPoll failed", "err", pollErr)
		return &apiv1.CommandsPollResponse{Error: strPtr(pollErr.Error())}, nil
	}
	out := make([]*apiv1.CommandMessage, len(commands))
	for i, c := range commands {
		out[i] = &apiv1.CommandMessage{
			CommandId:   c.ID.String(),
			CommandType: c.CommandType,
			Payload:     c.Payload,
			Status:      c.Status,
		}
	}
	return &apiv1.CommandsPollResponse{Commands: out}, nil
}

// CommandsAck подтверждает выполнение команды роутером; статус команды переводится в ACKED.
func (s *Server) CommandsAck(ctx context.Context, req *apiv1.CommandsAckRequest) (resp *apiv1.CommandsAckResponse, err error) {
	start := time.Now()
	defer func() {
		ok := err == nil && resp != nil && resp.GetSuccess()
		metrics.RecordGRPC("CommandsAck", ok, time.Since(start))
	}()
	routerID, parseErr := uuid.Parse(req.GetRouterId())
	if parseErr != nil {
		return &apiv1.CommandsAckResponse{Success: false, Error: strPtr("invalid router_id: " + parseErr.Error())}, nil
	}
	commandID, parseCmdErr := uuid.Parse(req.GetCommandId())
	if parseCmdErr != nil {
		return &apiv1.CommandsAckResponse{Success: false, Error: strPtr("invalid command_id: " + parseCmdErr.Error())}, nil
	}
	if ackErr := s.svc.AckCommand(ctx, routerID, commandID); ackErr != nil {
		return &apiv1.CommandsAckResponse{Success: false, Error: strPtr(ackErr.Error())}, nil
	}
	return &apiv1.CommandsAckResponse{Success: true}, nil
}
