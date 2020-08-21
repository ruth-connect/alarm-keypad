package uk.me.ruthmills.alarmkeypad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlarmKeypadApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlarmKeypadApplication.class, args);
	}
}
