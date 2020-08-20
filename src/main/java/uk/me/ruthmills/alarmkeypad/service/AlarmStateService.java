package uk.me.ruthmills.alarmkeypad.service;

import uk.me.ruthmills.alarmkeypad.model.AlarmState;

public interface AlarmStateService {

	public void armedAway();

	public void armedNight();

	public void armedHome();

	public void disarmed();

	public void countdown();

	public void triggered();

	public AlarmState getAlarmState();
}
