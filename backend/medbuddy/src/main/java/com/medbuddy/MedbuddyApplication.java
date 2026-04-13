package com.medbuddy;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MedbuddyApplication {
	public static void main(String[] args) {
		loadDotenvToSystemProperties();
		SpringApplication.run(MedbuddyApplication.class, args);
	}

	private static void loadDotenvToSystemProperties() {
		Path cwd = Paths.get("").toAbsolutePath().normalize();
		Path[] candidateDirs = new Path[] {
			cwd,
			cwd.resolve("backend").resolve("medbuddy"),
			cwd.getParent() != null ? cwd.getParent() : cwd,
			cwd.getParent() != null ? cwd.getParent().resolve("medbuddy") : cwd
		};

		for (Path dir : candidateDirs) {
			if (dir == null) {
				continue;
			}
			Path envPath = dir.resolve(".env");
			if (!Files.exists(envPath)) {
				continue;
			}

			Dotenv dotenv = Dotenv.configure()
				.directory(dir.toString())
				.ignoreIfMissing()
				.load();
			dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
			return;
		}

		Dotenv dotenv = Dotenv.configure()
			.ignoreIfMissing()
			.load();
		dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
	}
}
