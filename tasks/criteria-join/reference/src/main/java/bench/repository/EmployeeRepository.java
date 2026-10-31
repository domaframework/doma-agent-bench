package bench.repository;

import static org.seasar.doma.jdbc.criteria.expression.Expressions.count;

import bench.entity.Department_;
import bench.entity.Employee;
import bench.entity.Employee_;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.seasar.doma.jdbc.criteria.tuple.Tuple2;

public class EmployeeRepository {

  private final QueryDsl queryDsl;

  public EmployeeRepository(Config config) {
    this.queryDsl = new QueryDsl(config);
  }

  public List<Employee> findByDepartmentName(String departmentName) {
    var e = new Employee_();
    var d = new Department_();
    return queryDsl
        .from(e)
        .innerJoin(d, on -> on.eq(e.departmentId, d.id))
        .where(c -> c.eq(d.name, departmentName))
        .orderBy(
            c -> {
              c.desc(e.salary);
              c.asc(e.id);
            })
        .fetch();
  }

  public Map<String, Long> countEmployeesByDepartmentName() {
    var e = new Employee_();
    var d = new Department_();
    List<Tuple2<String, Long>> rows =
        queryDsl
            .from(d)
            .leftJoin(e, on -> on.eq(d.id, e.departmentId))
            .groupBy(d.name)
            .select(d.name, count(e.id))
            .fetch();
    Map<String, Long> result = new LinkedHashMap<>();
    rows.forEach(row -> result.put(row.getItem1(), row.getItem2()));
    return result;
  }
}
