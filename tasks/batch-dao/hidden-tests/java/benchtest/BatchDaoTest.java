package benchtest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bench.dao.EmployeeDao;
import bench.dao.EmployeeDaoImpl;
import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seasar.doma.jdbc.OptimisticLockException;

class BatchDaoTest {

  TestDb db;
  EmployeeDao dao;

  @BeforeEach
  void setUp() {
    db = TestDb.create();
    dao = new EmployeeDaoImpl(db.config());
  }

  static Employee employee(String name, String salary, Integer departmentId) {
    Employee e = new Employee();
    e.setName(name);
    e.setSalary(new BigDecimal(salary));
    e.setDepartmentId(departmentId);
    return e;
  }

  @Test
  void insertAll_setsGeneratedIds() {
    Employee a = employee("Eve", "1000", 3);
    Employee b = employee("Frank", "2000", 3);
    int[] counts = db.tx(() -> dao.insertAll(List.of(a, b)));
    assertArrayEquals(new int[] {1, 1}, counts);
    assertNotNull(a.getId());
    assertNotNull(b.getId());
    assertNotEquals(a.getId(), b.getId());
    assertEquals("Frank", db.tx(() -> dao.selectById(b.getId())).getName());
    assertEquals(8, db.tx(() -> dao.selectAll()).size());
  }

  @Test
  void updateAll() {
    Employee alice = db.tx(() -> dao.selectById(1));
    Employee bob = db.tx(() -> dao.selectById(2));
    alice.setName("Alicia");
    bob.setName("Robert");
    int[] counts = db.tx(() -> dao.updateAll(List.of(alice, bob)));
    assertArrayEquals(new int[] {1, 1}, counts);
    assertEquals("Alicia", db.tx(() -> dao.selectById(1)).getName());
    assertEquals(2, db.tx(() -> dao.selectById(2)).getVersion());
  }

  @Test
  void updateAll_staleVersionFails() {
    Employee stale = db.tx(() -> dao.selectById(1));
    Employee fresh = db.tx(() -> dao.selectById(1));
    fresh.setName("First");
    db.tx(() -> dao.updateAll(List.of(fresh)));
    stale.setName("Second");
    assertThrows(OptimisticLockException.class, () -> db.tx(() -> dao.updateAll(List.of(stale))));
  }

  @Test
  void raiseSalaryByDepartment() {
    int count = db.tx(() -> dao.raiseSalaryByDepartment(2, new BigDecimal("100")));
    assertEquals(3, count);
    Employee bob = db.tx(() -> dao.selectById(2));
    assertEquals(0, new BigDecimal("6100").compareTo(bob.getSalary()));
    Employee alice = db.tx(() -> dao.selectById(1));
    assertEquals(0, new BigDecimal("5000").compareTo(alice.getSalary()));
  }

  @Test
  void raiseSalaryByDepartment_incrementsVersion() {
    Employee loadedEarlier = db.tx(() -> dao.selectById(2));
    db.tx(() -> dao.raiseSalaryByDepartment(2, new BigDecimal("100")));
    assertEquals(2, db.tx(() -> dao.selectById(2)).getVersion());
    loadedEarlier.setName("Stale");
    assertThrows(
        OptimisticLockException.class, () -> db.tx(() -> dao.updateAll(List.of(loadedEarlier))));
  }
}
