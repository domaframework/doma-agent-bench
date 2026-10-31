This application uses Doma. The schema is in `db/schema.sql`, and the entity
`bench.entity.Employee` and the DAO `bench.dao.EmployeeDao` already exist.

Add a search method to `EmployeeDao`:

```java
List<Employee> selectByCondition(String namePrefix, BigDecimal minSalary, List<Integer> departmentIds);
```

- `namePrefix`: employees whose name starts with this string. The value must be
  matched literally: characters such as `%` and `_` must not act as wildcards.
- `minSalary`: employees whose salary is greater than or equal to this value.
- `departmentIds`: employees who belong to one of these departments. This argument
  is never `null`; an empty list means the condition is not applied.
- A condition whose argument is `null` is not applied.
  When no condition applies, all employees are returned.
- Results are ordered by `id`.

Write the query in an external SQL file (Doma's SQL template), not in the Java source.
