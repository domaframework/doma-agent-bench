package benchtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bench.dao.EmployeeDao;
import bench.dao.EmployeeDaoImpl;
import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixErrorsTest {

  TestDb db;
  EmployeeDao dao;

  @BeforeEach
  void setUp() {
    db = TestDb.create();
    dao = new EmployeeDaoImpl(db.config());
  }

  static List<Integer> ids(List<Employee> employees) {
    return employees.stream().map(Employee::getId).toList();
  }

  @Test
  void selectByDepartmentName() {
    assertEquals(List.of(2, 3, 5), ids(db.tx(() -> dao.selectByDepartmentName("Engineering"))));
  }

  @Test
  void selectBySalaryRange() {
    assertEquals(
        List.of(1, 5, 6),
        ids(db.tx(() -> dao.selectBySalaryRange(new BigDecimal("4500"), new BigDecimal("5000")))));
  }

  @Test
  void selectBySalaryRange_minOnly() {
    assertEquals(List.of(2, 3), ids(db.tx(() -> dao.selectBySalaryRange(new BigDecimal("6000"), null))));
  }

  @Test
  void selectBySalaryRange_maxOnly() {
    assertEquals(List.of(4), ids(db.tx(() -> dao.selectBySalaryRange(null, new BigDecimal("3000")))));
  }

  @Test
  void selectBySalaryRange_noBounds() {
    assertEquals(List.of(1, 2, 3, 4, 5, 6), ids(db.tx(() -> dao.selectBySalaryRange(null, null))));
  }

  @Test
  void selectByIds() {
    assertEquals(List.of(2, 4), ids(db.tx(() -> dao.selectByIds(List.of(4, 2)))));
  }

  @Test
  void selectByIds_emptyListReturnsNothing() {
    assertEquals(List.of(), ids(db.tx(() -> dao.selectByIds(List.of()))));
  }
}
