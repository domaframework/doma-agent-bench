package benchtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bench.dao.EmployeeDao;
import bench.dao.EmployeeDaoImpl;
import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seasar.doma.jdbc.OptimisticLockException;

class CrudDaoTest {

  TestDb db;
  EmployeeDao dao;

  @BeforeEach
  void setUp() {
    db = TestDb.create();
    dao = new EmployeeDaoImpl(db.config());
  }

  @Test
  void selectById() {
    Employee e = db.tx(() -> dao.selectById(2));
    assertEquals("Bob", e.getName());
    assertEquals(0, new BigDecimal("6000").compareTo(e.getSalary()));
    assertEquals(2, e.getDepartmentId());
    assertEquals(1, e.getVersion());
  }

  @Test
  void selectById_notFound() {
    assertNull(db.tx(() -> dao.selectById(999)));
  }

  @Test
  void selectAll_orderedById() {
    List<Employee> list = db.tx(() -> dao.selectAll());
    assertEquals(List.of(1, 2, 3, 4, 5, 6), list.stream().map(Employee::getId).toList());
  }

  @Test
  void insert_setsGeneratedId() {
    Employee e = new Employee();
    e.setName("Eve");
    e.setSalary(new BigDecimal("1234.50"));
    e.setDepartmentId(3);
    int count = db.tx(() -> dao.insert(e));
    assertEquals(1, count);
    assertNotNull(e.getId());
    Employee found = db.tx(() -> dao.selectById(e.getId()));
    assertEquals("Eve", found.getName());
    assertEquals(3, found.getDepartmentId());
  }

  @Test
  void update_incrementsVersion() {
    Employee e = db.tx(() -> dao.selectById(1));
    e.setName("Alicia");
    assertEquals(1, db.tx(() -> dao.update(e)));
    Employee found = db.tx(() -> dao.selectById(1));
    assertEquals("Alicia", found.getName());
    assertEquals(2, found.getVersion());
  }

  @Test
  void update_staleVersionFails() {
    Employee first = db.tx(() -> dao.selectById(1));
    Employee second = db.tx(() -> dao.selectById(1));
    first.setName("First");
    db.tx(() -> dao.update(first));
    second.setName("Second");
    assertThrows(OptimisticLockException.class, () -> db.tx(() -> dao.update(second)));
  }

  @Test
  void delete() {
    Employee e = db.tx(() -> dao.selectById(4));
    assertEquals(1, db.tx(() -> dao.delete(e)));
    assertNull(db.tx(() -> dao.selectById(4)));
  }
}
