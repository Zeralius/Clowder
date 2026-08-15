package dev.zeralius.clowder;

import org.springframework.boot.SpringApplication;

public class TestClowderApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClowderApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
