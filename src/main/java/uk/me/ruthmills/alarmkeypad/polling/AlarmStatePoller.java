package uk.me.ruthmills.alarmkeypad.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uk.me.ruthmills.alarmkeypad.service.AlarmStateService;

@Component
public class AlarmStatePoller {

	@Autowired
	private AlarmStateService alarmStateService;

	private final Logger logger = LoggerFactory.getLogger(AlarmStatePoller.class);

	@Scheduled(cron = "*/1 * * * * *")
	public void tick() {
		try {
			alarmStateService.tick();
		} catch (Exception ex) {
			logger.error("Exception in poller thread", ex);
		}
	}
}
