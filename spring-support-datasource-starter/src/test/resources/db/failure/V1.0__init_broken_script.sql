CREATE TABLE broken_script (
    id INT PRIMARY KEY
);

INSERT INTO broken_script (missing_column)
VALUES (1);
