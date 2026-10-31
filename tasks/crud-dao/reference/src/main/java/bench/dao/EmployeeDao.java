package bench.dao;

import bench.entity.Employee;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.Update;

@Dao
public interface EmployeeDao {

  @Sql("select /*%expand*/* from employee where id = /* id */0")
  @Select
  Employee selectById(Integer id);

  @Sql("select /*%expand*/* from employee order by id")
  @Select
  List<Employee> selectAll();

  @Insert
  int insert(Employee employee);

  @Update
  int update(Employee employee);

  @Delete
  int delete(Employee employee);
}
