package uk.me.ruthmills.alarmkeypad.polling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uk.me.ruthmills.alarmkeypad.service.AlarmStateService;

@Component
public class AlarmStatePoller {

	@Autowired
	private AlarmStateService alarmStateService;

	@Scheduled(cron = "*/1 * * * * *")
	public void tick() {
		alarmStateService.tick();
	}
}
