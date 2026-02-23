-- Creates one database per microservice (true DB-per-service pattern).
-- Each service connects to its own database with the shared 'minishop' user.

CREATE DATABASE user_db;
CREATE DATABASE product_db;
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
