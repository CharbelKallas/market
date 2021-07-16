CREATE TABLE IF NOT EXISTS customer
(
    id           BIGINT PRIMARY KEY,
    username     VARCHAR(250)  NOT NULL,
    password     VARCHAR(1000) NOT NULL,
    first_name   VARCHAR(200)  NOT NULL,
    last_name    VARCHAR(200)  NOT NULL,
    phone_number VARCHAR(100)  NOT NULL
);