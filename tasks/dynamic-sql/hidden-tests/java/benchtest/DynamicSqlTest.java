package benchtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bench.dao.EmployeeDao;
import bench.dao.EmployeeDaoImpl;
import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicSqlTest {

  TestDb db;
  EmployeeDao dao;

  @BeforeEach
  void setUp() {
    db = TestDb.create();
    dao = new EmployeeDaoImpl(db.config());
  }

  List<Integer> ids(String prefix, String minSalary, List<Integer> departmentIds) {
    BigDecimal salary = minSalary == null ? null : new BigDecimal(minSalary);
    return db.tx(() -> dao.selectByCondition(prefix, salary, departmentIds == null ? List.of() : departmentIds)).stream()
        .map(Employee::getId)
        .toList();
  }

  @Test
  void noCondition() {
    assertEquals(List.of(1, 2, 3, 4, 5, 6), ids(null, null, null));
  }

  @Test
  void emptyDepartmentIdsMeansNoFilter() {
    assertEquals(List.of(1, 2, 3, 4, 5, 6), ids(null, null, List.of()));
  }

  @Test
  void namePrefix() {
    assertEquals(List.of(1, 4, 5), ids("A", null, null));
  }

  @Test
  void namePrefixIsMatchedLiterally() {
    assertEquals(List.of(4), ids("A%", null, null));
  }

  @Test
  void minSalaryOnly() {
    assertEquals(List.of(2, 3), ids(null, "6000", null));
  }

  @Test
  void departmentIdsOnly() {
    assertEquals(List.of(2, 3, 5), ids(null, null, List.of(2)));
  }

  @Test
  void allConditions() {
    assertEquals(List.of(5), ids("A", "4000", List.of(2, 3)));
  }
}
