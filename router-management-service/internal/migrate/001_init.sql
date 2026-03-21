-- Миграция 001: начальная схема БД для сервиса управления роутерами.
-- Таблица роутеров: id (UUID), серийный/инвентарный номер, имя, модель, локация, last_seen_at, created_at.
CREATE TABLE IF NOT EXISTS routers (
    id UUID PRIMARY KEY,
    serial_number TEXT UNIQUE NOT NULL,
    inventory_number TEXT UNIQUE,
    name TEXT,
    model TEXT,
    location TEXT,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now()
);

-- Таблица команд: id, router_id (FK), тип команды, payload (JSONB), статус (PENDING/SENT/ACKED), sent_at, acked_at, created_at.
CREATE TABLE IF NOT EXISTS commands (
    id UUID PRIMARY KEY,
    router_id UUID REFERENCES routers(id),
    command_type TEXT NOT NULL,
    payload JSONB,
    status TEXT NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    acked_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now()
);

-- Индексы для быстрого поиска команд по роутеру и статусу (CommandsPoll, Ack).
CREATE INDEX IF NOT EXISTS idx_commands_router_status ON commands(router_id, status);
CREATE INDEX IF NOT EXISTS idx_commands_status ON commands(status);
