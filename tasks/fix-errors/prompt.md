This application uses Doma. The schema is in `db/schema.sql`.

`./gradlew build` currently fails. Fix the problems so that the build succeeds and
the methods of `bench.dao.EmployeeDao` behave as described below. Do not change
the method signatures.

- `selectByDepartmentName(String name)` — employees who belong to the department
  with the given name, ordered by `id`.
- `selectBySalaryRange(BigDecimal min, BigDecimal max)` — employees whose salary is
  between `min` and `max` (both inclusive), ordered by `id`. A `null` bound is not
  applied.
- `selectByIds(List<Integer> ids)` — employees whose id is in `ids`, ordered by `id`.
  An empty list returns no employees.
