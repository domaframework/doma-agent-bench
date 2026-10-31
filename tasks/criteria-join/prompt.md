This application uses Doma. The schema is in `db/schema.sql`, and the entities
`bench.entity.Employee` and `bench.entity.Department` already exist.

Create a class `bench.repository.EmployeeRepository` with a public constructor
`EmployeeRepository(org.seasar.doma.jdbc.Config config)` and these methods:

- `List<Employee> findByDepartmentName(String departmentName)`
  — employees who belong to the department with the given name, ordered by
  salary descending and then by id ascending.
- `Map<String, Long> countEmployeesByDepartmentName()`
  — the number of employees in each department, keyed by department name.
  Departments with no employees must be included with a count of 0.

Implement the queries with Doma's type-safe Criteria API, not with SQL strings
or SQL files.
