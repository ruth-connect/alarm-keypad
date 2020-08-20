package uk.me.ruthmills.alarmkeypad.service;

import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_AWAY;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_HOME;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_NIGHT;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.COUNTDOWN;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.DISARMED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.TRIGGERED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.UNKNOWN;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import uk.me.ruthmills.alarmkeypad.model.AlarmState;

@Service
public class AlarmStateServiceImpl implements AlarmStateService {

	private AlarmState alarmState;

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
	}

	public void armedAway() {
		alarmState = ARMED_AWAY;
	}

	public void armedNight() {
		alarmState = ARMED_NIGHT;
	}

	public void armedHome() {
		alarmState = ARMED_HOME;
	}

	public void disarmed() {
		alarmState = DISARMED;
	}

	public void countdown() {
		alarmState = COUNTDOWN;
	}

	public void triggered() {
		alarmState = TRIGGERED;
	}

	public AlarmState getAlarmState() {
		return alarmState;
	}
}
