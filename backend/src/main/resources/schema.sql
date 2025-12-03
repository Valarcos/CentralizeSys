-- CURRENT DROP TABLE deletes all data in the table
-- every time I run the application
DROP TABLE IF EXISTS products;

CREATE TABLE products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  stock INTEGER NOT NULL,
  price REAL NOT NULL
);

-- TODO: check periodically if this is still needed
-- USE THIS TO PERSIST DATA ACROSS RUNS

-- CREATE TABLE IF NOT EXISTS products (
--   id INTEGER PRIMARY KEY AUTOINCREMENT,
--   name TEXT NOT NULL,
--   stock INTEGER NOT NULL, -- Note: Your Entity used 'stock', schema used 'quantity'. Fix this!
--   price REAL NOT NULL
-- );