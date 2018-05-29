CREATE TABLE T2 (C0 CHARACTER(8), C1 BOOLEAN NOT NULL, C2 INTEGER);

SELECT ALL *
FROM T2
WHERE (ABS(1)) <> ABS(ABS(7));

SELECT C1
FROM T2
WHERE ABS(T2.C2) = ABS(T2.C2 * T2.C2);

SELECT *
FROM T2
WHERE (T2.C2) <= POWER((T2.C2), 4);
