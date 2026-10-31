package benchtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bench.entity.Employee;
import bench.repository.EmployeeRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CriteriaJoinTest {

  TestDb db;
  EmployeeRepository repository;

  @BeforeEach
  void setUp() {
    db = TestDb.create();
    repository = new EmployeeRepository(db.config());
  }

  @Test
  void findByDepartmentName_tieBrokenById() {
    // Alice (id 1) and Dave (id 6) have the same salary
    List<Employee> list = db.tx(() -> repository.findByDepartmentName("Sales"));
    assertEquals(List.of(1, 6, 4), list.stream().map(Employee::getId).toList());
  }

  @Test
  void findByDepartmentName_orderedBySalaryDesc() {
    List<Employee> list = db.tx(() -> repository.findByDepartmentName("Engineering"));
    assertEquals(List.of(3, 2, 5), list.stream().map(Employee::getId).toList());
  }

  @Test
  void findByDepartmentName_unknown() {
    assertEquals(List.of(), db.tx(() -> repository.findByDepartmentName("Nope")));
  }

  @Test
  void countEmployeesByDepartmentName_includesEmptyDepartments() {
    Map<String, Long> counts = db.tx(() -> repository.countEmployeesByDepartmentName());
    assertEquals(Map.of("Sales", 3L, "Engineering", 3L, "Legal", 0L), Map.copyOf(counts));
  }
}
