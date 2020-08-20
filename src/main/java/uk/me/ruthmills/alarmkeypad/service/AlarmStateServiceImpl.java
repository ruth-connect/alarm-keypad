package uk.me.ruthmills.alarmkeypad.service;

import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_AWAY;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_HOME;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_NIGHT;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.COUNTDOWN;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.DISARMED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.TRIGGERED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.UNKNOWN;

import java.util.Date;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.me.ruthmills.alarmkeypad.model.AlarmState;

@Service
public class AlarmStateServiceImpl implements AlarmStateService {

	private static final long STATE_CHANGE_TIMEOUT = 5000L;
	private static final long KEY_PRESS_TIMEOUT = 5000L;

	@Autowired
	private BuzzerService buzzerService;

	@Autowired
	private LedService ledService;

	private volatile AlarmState alarmState;
	private volatile StringBuffer code;
	private volatile Date lastKeyPressTime;
	private volatile Date lastStateChangeTime;
	private volatile int ledCount;

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
	}

	@Override
	public void armedAway() {
		alarmState = ARMED_AWAY;
		lastStateChangeTime = new Date();
	}

	@Override
	public void armedNight() {
		alarmState = ARMED_NIGHT;
		lastStateChangeTime = new Date();
	}

	@Override
	public void armedHome() {
		alarmState = ARMED_HOME;
		lastStateChangeTime = new Date();
	}

	@Override
	public void disarmed() {
		alarmState = DISARMED;
		lastStateChangeTime = new Date();
	}

	@Override
	public void countdown() {
		alarmState = COUNTDOWN;
		lastStateChangeTime = new Date();
	}

	@Override
	public void triggered() {
		alarmState = TRIGGERED;
		lastStateChangeTime = new Date();
	}

	@Override
	public void keyPressed(char key) {
		lastKeyPressTime = new Date();
		if (key >= '0' && key <= '9') {
			code.append(key);
			ledService.setLeds(code.length() % 4 == 1, code.length() % 4 == 2, code.length() % 4 == 3,
					code.length() > 0 && code.length() % 4 == 0);
			beep(100);
		} else if (key >= 'A' && key <= 'D') {
			beep(200);
		} else if (key == '*') {
			if (code.length() > 0) {
				code.delete(code.length() - 1, code.length());
				ledService.setLeds(code.length() % 4 == 1, code.length() % 4 == 2, code.length() % 4 == 3,
						code.length() > 0 && code.length() % 4 == 0);
				beep(100);
			}
		} else if (key == '#') {
			if (code.length() > 0) {
				code.delete(0, code.length());
			}
			lastKeyPressTime = null;
			lastStateChangeTime = new Date();
			ledService.setLeds(alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT),
					alarmState.equals(ARMED_HOME), alarmState.equals(DISARMED));
			beep(200);
		}
	}

	@Override
	public void tick() {
		if (alarmState.equals(TRIGGERED)) {
			flash(250, true, true, true, true);
			flash(250, false, false, false, false);
			flash(250, true, true, true, true);
			flash(0, false, false, false, false);
			ledCount = 0;
		} else if (alarmState.equals(COUNTDOWN)) {
			flash(250, false, false, false, true);
			flash(250, false, false, true, false);
			flash(250, false, true, false, false);
			flash(0, true, false, false, false);
			ledCount = 0;
		} else if (lastStateChangeTime != null
				&& new Date().getTime() - lastStateChangeTime.getTime() < STATE_CHANGE_TIMEOUT) {
			flash(250, alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
					alarmState.equals(DISARMED));
			flash(250, false, false, false, false);
			flash(250, alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
					alarmState.equals(DISARMED));
			flash(250, false, false, false, false);
			ledCount = 0;
		} else {
			flash(500, ledCount == 0, ledCount == 1, ledCount == 2, ledCount == 3);
			ledCount++;
			if (ledCount > 3) {
				ledCount = 0;
			}
			flash(0, ledCount == 0, ledCount == 1, ledCount == 2, ledCount == 3);
			ledCount++;
			if (ledCount > 3) {
				ledCount = 0;
			}
		}
	}

	@Override
	public void sleep(int milliseconds) {
		if (milliseconds > 0) {
			try {
				Thread.sleep(milliseconds);
			} catch (InterruptedException ex) {
			}
		}
	}

	@Override
	public void beep(int milliseconds) {
		buzzerService.setBuzzer(true);
		sleep(milliseconds);
		buzzerService.setBuzzer(false);
	}

	@Override
	public void flash(int milliseconds, boolean red, boolean amber, boolean green, boolean blue) {
		if (lastKeyPressTime == null || new Date().getTime() - lastKeyPressTime.getTime() > KEY_PRESS_TIMEOUT) {
			ledService.setLeds(red, amber, green, blue);
			sleep(milliseconds);
		}
	}
}
