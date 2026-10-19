CREATE TABLE branch (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(40) NOT NULL,
    sort_order INT         NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_branch_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO branch (code, name, sort_order) VALUES
    ('CASTORES', 'Castores', 1),
    ('LOBATOS',  'Lobatos',  2),
    ('SCOUTS',   'Scouts',   3),
    ('ESCULTAS', 'Escultas', 4),
    ('ROVERS',   'Rovers',   5);
