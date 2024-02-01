package org.hibernate.bugs;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.LongStream;

import org.hibernate.Version;
import org.junit.AfterClass;
import org.junit.Test;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM,
 * using the Java Persistence API.
 */
@State(Scope.Thread)
public class PerformanceHibernate6Test {
	
	static final int NUMBER_OF_ENTITIES = 1000;
	static final boolean PRINT_ENTITES = false;

	private EntityManagerFactory entityManagerFactory;

	@Setup
	public void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
		setupData();
	}

	@TearDown
	public void destroy() {
		entityManagerFactory.close();
	}

	@AfterClass
	public static void afterClass() {
		System.out.println("Running with Hibernate "+ Version.getVersionString() );
	}
	
	public static void main(String[] args) throws RunnerException, IOException {
		if (args.length == 0) {
			final Options opt = new OptionsBuilder()
					.mode(Mode.Throughput)
					.include(PerformanceHibernate6Test.class.getSimpleName())
					.warmupIterations(3)
					.warmupTime(TimeValue.seconds(3))
					.measurementIterations(3)
					//.measurementTime(TimeValue.seconds(10))
					.threads(1)
					//.addProfiler("gc")
					.forks(2)
					.build();
			new Runner(opt).run();
		} else {
			Main.main(args);
		}
	}
	
	@Test
	public void test() throws RunnerException, IOException {
		main( new String[0]) ;
	}
	
	
	// Entities are auto-discovered, so just add them anywhere on class-path
	// Add your tests, using standard JUnit.
	@Benchmark
	public void h6_no_extra_query(Blackhole blackhole) {
		
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		// Do stuff...

		queryEmployees( blackhole, entityManager, false, false );
		
		entityManager.getTransaction().commit();
		entityManager.close();

		
	}

	@Benchmark
	public void h6_with_hitting_extra_query(Blackhole blackhole) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		// Do stuff...
		queryEmployees(blackhole, entityManager, true, true);
		
		entityManager.getTransaction().commit();
		entityManager.close();
		
	}


	@Benchmark
	public void h6_with_not_hitting_extra_query(Blackhole blackhole) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();
		
		// Do stuff...
		queryEmployees(blackhole, entityManager, true, false);

		
		entityManager.getTransaction().commit();
		entityManager.close();
		
		
	}
	
	
	private void queryEmployees( Blackhole blackhole, EntityManager entityManager, boolean withExtraQuery, boolean extraQueryHits) {

		TypedQuery<Employee> query = entityManager.createQuery("select e From Employee e", Employee.class);

		List<Employee> employeeList = query.getResultList();
		

		for (Employee employee : employeeList) {

			blackhole.consume(employee.toString());

			if (withExtraQuery) {
				queryAccessForEmployee( blackhole, entityManager, employee, extraQueryHits);
			}

		}
		

	}

	
	private void queryAccessForEmployee(Blackhole blackhole, EntityManager entityManager, Employee employee, boolean extraQueryHits) {

		TypedQuery<ProjectAccess> accessQuery = entityManager
				.createQuery("select pa From ProjectAccess pa where pa.employee = :employee and 1 = :value ", ProjectAccess.class)
				.setParameter("employee", employee)
				.setParameter("value",  extraQueryHits ? 1 : 2 )
				;
		
		List<ProjectAccess> accessList = accessQuery.getResultList();
		accessList.forEach(p -> blackhole.consume(p.toString()));
		
	}


	private void setupData() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		LongStream.range(1, NUMBER_OF_ENTITIES).forEach(id -> {
			Employee employee = new Employee();
			employee.setFirstName("FNAME_" + id);
			employee.setLastName("LNAME_" + id);
			employee.setEmail("NAME_" + id + "@email.com");

			entityManager.persist(employee);
		});

		LongStream.range(1, NUMBER_OF_ENTITIES).forEach(id -> {
			Project project = new Project();
			project.setTitle("TITLE_" + id);
			project.setBegin(new Timestamp(System.currentTimeMillis()));
			entityManager.persist(project);
		});

		LongStream.range(1, NUMBER_OF_ENTITIES).forEach(id -> {

			ProjectAccess projectAccess = new ProjectAccess();
			projectAccess.setEmployee(entityManager.find(Employee.class, id));
			projectAccess.setProject(entityManager.find(Project.class, id));
			projectAccess.setBegin(new Timestamp(System.currentTimeMillis()));
			entityManager.persist(projectAccess);

		});

		// to force class loading
		entityManager
			.createQuery("select p From Project p", Project.class)
			.getResultList();
		
		entityManager.getTransaction().commit();
		
		entityManager.close();

	}
}
