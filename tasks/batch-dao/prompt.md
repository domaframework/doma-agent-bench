This application uses Doma. The schema is in `db/schema.sql`, and the entity
`bench.entity.Employee` and the DAO `bench.dao.EmployeeDao` already exist.

Add these methods to `EmployeeDao`:

- `int[] insertAll(List<Employee> employees)` — inserts all employees as a JDBC
  batch. Each element of the result is the row count for the corresponding employee.
  After the call, each entity has its database-generated id set.
- `int[] updateAll(List<Employee> employees)` — updates all employees as a JDBC batch,
  with optimistic locking on `version`: if any employee's version is stale, Doma's
  optimistic lock exception is thrown.
- `int raiseSalaryByDepartment(Integer departmentId, BigDecimal amount)` — adds
  `amount` to the salary of every employee in the department and increments their
  `version`, so that optimistic locking keeps working for entities loaded earlier.
  Returns the number of updated rows. Write this query in an external SQL file
  (Doma's SQL template), not in the Java source.
