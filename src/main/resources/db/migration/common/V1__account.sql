CREATE TABLE account (
  id            UUID PRIMARY KEY,
  account_ref   VARCHAR(34)  NOT NULL UNIQUE,
  holder_name   VARCHAR(140) NOT NULL,
  account_type  VARCHAR(16)  NOT NULL DEFAULT 'CUSTOMER',
  currency      VARCHAR(3)   NOT NULL,
  balance       NUMERIC(19,4) NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  version       BIGINT       NOT NULL DEFAULT 0,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT account_currency_iso CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$')),
  CONSTRAINT account_type_valid   CHECK (account_type IN ('CUSTOMER','SYSTEM')),
  CONSTRAINT account_status_valid CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
  -- the backstop: no customer account can ever go negative, whatever the code does
  CONSTRAINT account_no_overdraft CHECK (account_type <> 'CUSTOMER' OR balance >= 0)
);
